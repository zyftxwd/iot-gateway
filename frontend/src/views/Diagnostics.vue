<template>
  <div class="diagnostics-page">
    <section class="diagnostics-toolbar">
      <div class="title-block">
        <h2>系统诊断</h2>
        <p>集中检查采集节点、协议连接、异常采样、报警和工单运行状态。</p>
      </div>
      <div class="toolbar-actions">
        <span class="update-time">更新时间：{{ formatTime(data.generatedAt) }}</span>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </section>

    <section class="kpi-grid">
      <div class="kpi-card">
        <span>设备总数</span>
        <strong>{{ summary.deviceTotal || 0 }}</strong>
        <small>当前账号可见设备</small>
      </div>
      <div class="kpi-card success">
        <span>在线设备</span>
        <strong>{{ summary.onlineDevices || 0 }}</strong>
        <small>最近采集正常</small>
      </div>
      <div class="kpi-card danger">
        <span>离线设备</span>
        <strong>{{ summary.offlineDevices || 0 }}</strong>
        <small>连接或采集异常</small>
      </div>
      <div class="kpi-card warning">
        <span>活动报警</span>
        <strong>{{ summary.activeAlarms || 0 }}</strong>
        <small>未恢复或未关闭</small>
      </div>
      <div class="kpi-card">
        <span>未完成工单</span>
        <strong>{{ summary.openWorkOrders || 0 }}</strong>
        <small>仍在流转中</small>
      </div>
    </section>

    <section class="status-grid">
      <div class="panel collector-panel">
        <div class="panel-head">
          <div>
            <h3>采集节点</h3>
            <p>判断线程池、队列和采集任务是否堆积。</p>
          </div>
          <el-tag effect="plain">{{ collector.nodeId || 'local-dev' }}</el-tag>
        </div>
        <div class="collector-body">
          <div class="node-main">
            <span>运行设备</span>
            <strong>{{ collector.runningDevices || 0 }}</strong>
            <small>队列 {{ collector.queuedTasks || 0 }} / {{ collector.queueCapacity || 0 }}</small>
          </div>
          <div class="node-metrics">
            <div>
              <span>线程池</span>
              <strong>{{ collector.poolSize || 0 }} / {{ collector.corePoolSize || 0 }}</strong>
            </div>
            <div>
              <span>活跃线程</span>
              <strong>{{ collector.activeThreads || 0 }}</strong>
            </div>
            <div>
              <span>完成任务</span>
              <strong>{{ collector.completedTasks || 0 }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="panel protocol-panel">
        <div class="panel-head">
          <div>
            <h3>协议状态</h3>
            <p>按协议聚合设备在线、离线和最后采集时间。</p>
          </div>
          <el-select v-model="filters.protocolType" clearable placeholder="全部协议" class="protocol-filter">
            <el-option v-for="item in protocolOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
        <el-table :data="filteredProtocols" height="100%" empty-text="暂无协议数据">
          <el-table-column prop="protocolType" label="协议" min-width="120" />
          <el-table-column prop="total" label="设备" width="80" align="center" />
          <el-table-column prop="online" label="在线" width="80" align="center" />
          <el-table-column prop="offline" label="离线" width="80" align="center" />
          <el-table-column prop="unknown" label="未知" width="80" align="center" />
          <el-table-column label="最后采集" width="170">
            <template #default="{ row }">{{ formatTime(row.lastCollectTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="panel detail-panel">
      <div class="detail-head">
        <div>
          <h3>诊断明细</h3>
          <p>错误、异常采样、活动报警和未完成工单统一在这里排查。</p>
        </div>
        <el-input
          v-model="filters.keyword"
          clearable
          class="keyword-input"
          placeholder="搜索设备、点位、错误内容"
        />
      </div>

      <el-tabs v-model="activeTab" class="diag-tabs">
        <el-tab-pane :label="`设备错误 ${filteredDeviceErrors.length}`" name="devices">
          <div class="tab-table">
            <el-table :data="filteredDeviceErrors" height="100%" empty-text="暂无设备错误">
              <el-table-column prop="deviceName" label="设备" min-width="150" show-overflow-tooltip />
              <el-table-column prop="protocolType" label="协议" width="120" align="center" />
              <el-table-column prop="address" label="连接地址" width="150" show-overflow-tooltip />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'" effect="plain">
                    {{ statusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="failCount" label="失败次数" width="100" align="center" />
              <el-table-column prop="lastErrorMessage" label="错误内容" min-width="260" show-overflow-tooltip />
              <el-table-column label="最后采集" width="170">
                <template #default="{ row }">{{ formatTime(row.lastCollectTime) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`异常采样 ${filteredBadHistory.length}`" name="history">
          <div class="tab-table">
            <el-table :data="filteredBadHistory" height="100%" empty-text="暂无异常采样">
              <el-table-column prop="pointLabel" label="点位" min-width="150" show-overflow-tooltip />
              <el-table-column prop="pointKey" label="标识" min-width="150" show-overflow-tooltip />
              <el-table-column prop="protocolType" label="协议" width="110" />
              <el-table-column prop="quality" label="质量" width="100" />
              <el-table-column prop="rawValue" label="原始值" min-width="140" show-overflow-tooltip />
              <el-table-column prop="collectorNode" label="采集节点" width="130" show-overflow-tooltip />
              <el-table-column label="采集时间" width="170">
                <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`活动报警 ${filteredAlarms.length}`" name="alarms">
          <div class="tab-table">
            <el-table :data="filteredAlarms" height="100%" empty-text="暂无活动报警">
              <el-table-column prop="title" label="报警标题" min-width="180" show-overflow-tooltip />
              <el-table-column prop="severity" label="级别" width="100" align="center" />
              <el-table-column prop="currentValue" label="当前值" width="120" />
              <el-table-column prop="thresholdText" label="规则" min-width="150" show-overflow-tooltip />
              <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
              <el-table-column label="最后时间" width="170">
                <template #default="{ row }">{{ formatTime(row.lastTime || row.firstTime) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`未完成工单 ${filteredOrders.length}`" name="orders">
          <div class="tab-table">
            <el-table :data="filteredOrders" height="100%" empty-text="暂无未完成工单">
              <el-table-column prop="orderNo" label="工单号" min-width="170" show-overflow-tooltip />
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="110" align="center">
                <template #default="{ row }">{{ workOrderStatus(row.status) }}</template>
              </el-table-column>
              <el-table-column prop="assigneeName" label="处理人" width="120" />
              <el-table-column prop="verifierName" label="验收人" width="120" />
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])
const loading = ref(false)
const data = ref({})
const activeTab = ref('devices')
const filters = reactive({ protocolType: '', keyword: '' })
let refreshTimer = null

const summary = computed(() => data.value.summary || {})
const collector = computed(() => data.value.collector || {})
const protocolOptions = computed(() => (data.value.protocols || []).map((item) => item.protocolType))

const filteredProtocols = computed(() => {
  if (!filters.protocolType) return data.value.protocols || []
  return (data.value.protocols || []).filter((item) => item.protocolType === filters.protocolType)
})

const keywordMatch = (values) => {
  const keyword = filters.keyword.trim().toLowerCase()
  if (!keyword) return true
  return values.some((value) => String(value || '').toLowerCase().includes(keyword))
}

const filteredDeviceErrors = computed(() => (data.value.deviceErrors || []).filter((item) => {
  if (filters.protocolType && item.protocolType !== filters.protocolType) return false
  return keywordMatch([item.deviceName, item.protocolType, item.address, item.lastErrorMessage])
}))

const filteredBadHistory = computed(() => (data.value.badHistory || []).filter((item) => {
  if (filters.protocolType && item.protocolType !== filters.protocolType) return false
  return keywordMatch([item.pointLabel, item.pointKey, item.protocolType, item.rawValue, item.collectorNode])
}))

const filteredAlarms = computed(() => (data.value.activeAlarms || []).filter((item) => {
  return keywordMatch([item.title, item.severity, item.thresholdText, item.message])
}))

const filteredOrders = computed(() => (data.value.openWorkOrders || []).filter((item) => {
  return keywordMatch([item.orderNo, item.title, item.status, item.assigneeName, item.verifierName])
}))

const loadData = async () => {
  loading.value = true
  try {
    data.value = await api.getDiagnosticsOverview()
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message || '诊断数据加载失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'
const statusLabel = (status) => ({ ONLINE: '在线', OFFLINE: '离线', UNKNOWN: '未知' }[status] || status || '-')
const workOrderStatus = (status) => ({
  CREATED: '待派单',
  DISPATCHED: '待接单',
  ACCEPTED: '已接单',
  PROCESSING: '处理中',
  FINISHED: '待验收',
  VERIFIED: '待关闭',
  CLOSED: '已关闭'
}[status] || status || '-')

onMounted(() => {
  loadData()
  refreshTimer = window.setInterval(loadData, 5000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<style scoped>
.diagnostics-page {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: auto auto 260px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.diagnostics-toolbar,
.kpi-card,
.panel {
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
}

.diagnostics-toolbar {
  min-height: 72px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title-block h2,
.panel-head h3,
.detail-head h3 {
  margin: 0;
  color: #111827;
  line-height: 1.25;
}

.title-block h2 {
  font-size: 20px;
}

.panel-head h3,
.detail-head h3 {
  font-size: 17px;
}

.title-block p,
.panel-head p,
.detail-head p,
.update-time,
.kpi-card span,
.kpi-card small,
.node-main span,
.node-main small,
.node-metrics span {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  min-width: 0;
  padding: 14px 16px;
}

.kpi-card strong {
  display: block;
  margin: 8px 0 4px;
  color: #111827;
  font-size: 28px;
  line-height: 1;
}

.kpi-card.success strong {
  color: #16a34a;
}

.kpi-card.danger strong {
  color: #dc2626;
}

.kpi-card.warning strong {
  color: #d97706;
}

.kpi-card small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 14px;
}

.panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-head,
.detail-head {
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid #e6edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.protocol-filter {
  width: 150px;
}

.collector-body {
  flex: 1;
  min-height: 0;
  padding: 14px;
  display: grid;
  grid-template-columns: 145px minmax(0, 1fr);
  gap: 12px;
}

.node-main {
  min-width: 0;
  padding: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}

.node-main strong {
  display: block;
  margin: 10px 0 8px;
  color: #111827;
  font-size: 30px;
  line-height: 1;
}

.node-metrics {
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.node-metrics div {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e6edf5;
  border-radius: 8px;
  background: #f8fafc;
}

.node-metrics strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 18px;
  line-height: 1.2;
  word-break: break-word;
}

.detail-panel {
  min-height: 0;
}

.keyword-input {
  width: 320px;
}

.diag-tabs {
  flex: 1;
  min-height: 0;
  padding: 0 14px 14px;
  display: flex;
  flex-direction: column;
}

.tab-table {
  height: 100%;
  min-height: 0;
}

:deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
}

:deep(.el-tab-pane) {
  height: 100%;
}

:deep(.el-table .cell) {
  line-height: 22px;
}

@media (max-width: 1500px) {
  .diagnostics-page {
    grid-template-rows: auto auto auto minmax(420px, 1fr);
    overflow: auto;
  }

  .kpi-grid {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }

  .status-grid {
    grid-template-columns: 1fr;
  }

  .collector-panel,
  .protocol-panel {
    min-height: 260px;
  }
}

@media (max-width: 900px) {
  .diagnostics-toolbar,
  .panel-head,
  .detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .collector-body {
    grid-template-columns: 1fr;
  }

  .keyword-input,
  .protocol-filter {
    width: 100%;
  }
}
</style>
