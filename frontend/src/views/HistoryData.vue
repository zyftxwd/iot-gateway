<template>
  <div class="history-page">
    <aside class="history-side">
      <div class="side-title">
        <h2>数据范围</h2>
        <span>按资产层级定位历史点位</span>
      </div>

      <el-form label-position="top" class="scope-form">
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable filterable placeholder="选择项目" @change="handleProjectChange">
            <el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="filters.groupId" clearable filterable placeholder="选择分组" @change="handleGroupChange">
            <el-option v-for="item in filteredGroups" :key="item.id" :label="item.groupName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="filters.deviceId" clearable filterable placeholder="全部设备" @change="handleDeviceChange">
            <el-option v-for="item in devices" :key="item.id" :label="deviceLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="点位">
          <el-select v-model="filters.pointId" clearable filterable placeholder="全部点位" @change="loadHistory">
            <el-option v-for="item in points" :key="item.id" :label="pointLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="scope-card">
        <span>当前设备</span>
        <strong>{{ activeDevice?.deviceName || '-' }}</strong>
        <small>{{ scopeHint }}</small>
      </div>
    </aside>

    <section class="history-main">
      <div class="query-bar">
        <div>
          <h2>历史数据</h2>
          <span>查看点位历史值、质量、采集耗时和采集节点。</span>
        </div>
        <div class="query-actions">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
            value-format="x"
            style="width: 380px"
          />
          <el-input-number v-model="filters.limit" :min="50" :max="5000" :step="50" />
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadHistory">查询</el-button>
        </div>
      </div>

      <div class="metric-grid">
        <div class="metric-card">
          <span>记录数</span>
          <strong>{{ histories.length }}</strong>
        </div>
        <div class="metric-card">
          <span>{{ filters.pointId ? '最新值' : '点位数' }}</span>
          <strong>{{ filters.pointId ? latestValue : pointCount }}</strong>
        </div>
        <div class="metric-card">
          <span>采集质量</span>
          <strong>{{ goodRate }}</strong>
        </div>
        <div class="metric-card">
          <span>平均耗时</span>
          <strong>{{ avgCost }}</strong>
        </div>
      </div>

      <el-table v-loading="loading" :data="pagedHistories" height="100%" class="history-table" empty-text="请选择项目、分组、设备或点位后查询">
        <el-table-column prop="pointLabel" label="点位名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="pointKey" label="数据标识" min-width="150" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.pointKey }}</span></template>
        </el-table-column>
        <el-table-column prop="valueText" label="数值" width="130" align="center">
          <template #default="{ row }">
            <strong class="value-text">{{ row.valueText ?? '-' }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="quality" label="质量" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.quality === 'GOOD' ? 'success' : 'danger'" effect="plain">{{ row.quality }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="protocolType" label="协议" width="130" align="center" />
        <el-table-column label="采集时间" width="190">
          <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ row.collectCostMs ?? '-' }} ms</template>
        </el-table-column>
        <el-table-column prop="collectorNode" label="采集节点" min-width="130" show-overflow-tooltip />
      </el-table>
      <div class="pager-row">
        <span>共 {{ histories.length }} 条</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="histories.length"
          layout="sizes, prev, pager, next"
          background
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])

const loading = ref(false)
const projects = ref([])
const groups = ref([])
const devices = ref([])
const points = ref([])
const histories = ref([])
const timeRange = ref([])
const pagination = reactive({ page: 1, pageSize: 50 })

const filters = reactive({
  projectId: null,
  groupId: null,
  deviceId: null,
  pointId: null,
  limit: 500
})

const filteredGroups = computed(() => {
  if (!filters.projectId) return groups.value
  return groups.value.filter((item) => item.projectId === filters.projectId)
})

const activeDevice = computed(() => devices.value.find((item) => item.id === filters.deviceId))
const scopeHint = computed(() => {
  if (activeDevice.value) {
    return `${activeDevice.value.protocolType} / ${activeDevice.value.ipAddress || '-'}:${activeDevice.value.port || '-'}`
  }
  if (filters.groupId) return '当前查询范围：该分组下全部设备点位'
  if (filters.projectId) return '当前查询范围：该项目下全部设备点位'
  return '请选择项目后查询历史'
})
const latestValue = computed(() => histories.value[0]?.valueText ?? '-')
const pointCount = computed(() => new Set(histories.value.map((item) => item.pointId)).size || '-')
const pagedHistories = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return histories.value.slice(start, start + pagination.pageSize)
})
const goodRate = computed(() => {
  if (!histories.value.length) return '-'
  const good = histories.value.filter((item) => item.quality === 'GOOD').length
  return `${Math.round((good / histories.value.length) * 100)}%`
})
const avgCost = computed(() => {
  const costs = histories.value.map((item) => item.collectCostMs).filter((item) => typeof item === 'number')
  if (!costs.length) return '-'
  const avg = costs.reduce((sum, item) => sum + item, 0) / costs.length
  return `${avg.toFixed(1)} ms`
})

const loadBaseData = async () => {
  const [projectList, groupList] = await Promise.all([
    api.listProjects(),
    api.listProjectGroups()
  ])
  projects.value = projectList || []
  groups.value = groupList || []
}

const loadDevices = async () => {
  if (!filters.projectId) {
    devices.value = []
    return
  }
  devices.value = await api.listDevices({
    projectId: filters.projectId,
    groupId: filters.groupId || undefined
  })
}

const loadPoints = async () => {
  if (!filters.deviceId) {
    points.value = []
    return
  }
  points.value = await api.listPoints({ deviceId: filters.deviceId })
}

const handleProjectChange = async () => {
  filters.groupId = null
  filters.deviceId = null
  filters.pointId = null
  histories.value = []
  points.value = []
  await loadDevices()
  await loadHistory()
}

const handleGroupChange = async () => {
  filters.deviceId = null
  filters.pointId = null
  histories.value = []
  points.value = []
  await loadDevices()
  await loadHistory()
}

const handleDeviceChange = async () => {
  filters.pointId = null
  histories.value = []
  await loadPoints()
  await loadHistory()
}

const loadHistory = async () => {
  if (!filters.projectId && !filters.deviceId && !filters.pointId) {
    ElMessage.warning('请先选择项目、设备或点位')
    return
  }
  loading.value = true
  try {
    histories.value = await api.listPointHistory({
      projectId: filters.pointId || filters.deviceId ? undefined : filters.projectId,
      groupId: filters.pointId || filters.deviceId ? undefined : filters.groupId,
      deviceId: filters.pointId ? undefined : filters.deviceId,
      pointId: filters.pointId || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
      limit: filters.limit
    })
    pagination.page = 1
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

const deviceLabel = (item) => `${item.deviceName} · ${item.protocolType}`
const pointLabel = (item) => `${item.pointLabel || item.pointKey} · ${item.pointKey}`
const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'

onMounted(async () => {
  try {
    await loadBaseData()
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  }
})
</script>

<style scoped>
.history-page {
  height: 100%;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.history-side,
.history-main {
  min-height: 0;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.history-side {
  padding: 18px;
  overflow: auto;
}

.side-title h2,
.query-bar h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.side-title span,
.query-bar span {
  display: block;
  margin-top: 5px;
  color: #667085;
  font-size: 13px;
}

.scope-form {
  margin-top: 18px;
}

.scope-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: linear-gradient(135deg, #eff6ff 0%, #f8fafc 100%);
}

.scope-card span,
.metric-card span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.scope-card strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 20px;
}

.scope-card small {
  display: block;
  margin-top: 8px;
  color: #475569;
}

.history-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.query-bar {
  min-height: 78px;
  padding: 0 18px;
  border-bottom: 1px solid #e6edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.query-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  border-bottom: 1px solid #e6edf5;
  background: #f8fafc;
}

.metric-card {
  padding: 14px 18px;
  border-right: 1px solid #e6edf5;
}

.metric-card strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 24px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-table {
  flex: 1;
}

.pager-row {
  min-height: 48px;
  padding: 0 18px;
  border-top: 1px solid #e6edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #667085;
  font-size: 13px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
}

.value-text {
  color: #1d4ed8;
  font-size: 15px;
}

@media (max-width: 1280px) {
  .history-page {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .query-bar {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 18px;
  }
}
</style>
