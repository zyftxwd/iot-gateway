<template>
  <div class="alarm-page">
    <section class="alarm-side">
      <div class="panel-head">
        <div>
          <h2>报警筛选</h2>
          <span>按项目、设备、状态定位报警事件</span>
        </div>
        <el-button :icon="Refresh" @click="loadAll" />
      </div>

      <el-form label-position="top" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable filterable placeholder="全部可见项目" @change="handleProjectChange">
            <el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="filters.deviceId" clearable filterable placeholder="全部设备" @change="loadAlarms">
            <el-option v-for="item in devices" :key="item.id" :label="deviceLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件状态">
          <el-segmented v-model="filters.status" :options="statusOptions" block @change="loadAlarms" />
        </el-form-item>
        <el-form-item label="报警类型">
          <el-select v-model="filters.alarmType" clearable placeholder="全部类型" @change="loadAlarms">
            <el-option label="点位规则" value="POINT_RULE" />
            <el-option label="采集异常" value="COLLECT_ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.severity" clearable placeholder="全部级别" @change="loadAlarms">
            <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="node-card">
        <span>采集节点</span>
        <strong>{{ collectorStatus.nodeId || '-' }}</strong>
        <small>运行设备 {{ collectorStatus.runningDevices || 0 }} 台，队列 {{ collectorStatus.queuedTasks || 0 }}</small>
      </div>
    </section>

    <section class="alarm-main">
      <div class="headline">
        <div>
          <h2>报警事件</h2>
          <span>规则报警、采集异常和后续工单入口统一在这里闭环。</span>
        </div>
        <div class="headline-actions">
          <el-input v-model="keyword" clearable placeholder="搜索标题、设备、点位或内容" :prefix-icon="Search" @input="filterLocal" />
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <div class="metric-grid">
        <div class="metric danger">
          <span>活动报警</span>
          <strong>{{ activeCount }}</strong>
        </div>
        <div class="metric warn">
          <span>高风险</span>
          <strong>{{ highRiskCount }}</strong>
        </div>
        <div class="metric">
          <span>点位规则</span>
          <strong>{{ ruleAlarmCount }}</strong>
        </div>
        <div class="metric">
          <span>当前筛选</span>
          <strong>{{ alarms.length }}</strong>
        </div>
      </div>

      <el-table v-loading="loading" :data="pagedAlarms" height="100%" class="alarm-table" empty-text="暂无报警事件">
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" effect="dark">{{ severityLabel(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报警对象" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="object-cell">
              <strong>{{ row.title || alarmTypeLabel(row.alarmType) }}</strong>
              <span>{{ objectText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前值" width="120" align="center">
          <template #default="{ row }">
            <strong class="current-value">{{ formatValue(row) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="thresholdText" label="阈值/规则" min-width="150" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="次数" width="86" align="center">
          <template #default="{ row }">{{ row.occurCount || 1 }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ formatTime(row.firstTime) }}</template>
        </el-table-column>
        <el-table-column label="最后发生" width="180">
          <template #default="{ row }">{{ formatTime(row.lastTime) }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="180">
          <template #default="{ row }">{{ formatTime(row.recoverTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="canHandle(row)" size="small" type="primary" plain @click="resolveAlarm(row)">关闭</el-button>
            <el-button size="small" plain :disabled="!!row.workOrderId" @click="createWorkOrder(row)">
              {{ row.workOrderId ? '已转工单' : '转工单' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <span>共 {{ visibleAlarms.length }} 条</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="visibleAlarms.length"
          layout="sizes, prev, pager, next"
          background
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])

const loading = ref(false)
const alarms = ref([])
const visibleAlarms = ref([])
const projects = ref([])
const devices = ref([])
const collectorStatus = ref({})
const currentUser = ref(null)
const keyword = ref('')
const pagination = reactive({ page: 1, pageSize: 20 })

const filters = reactive({
  projectId: null,
  deviceId: null,
  status: 'ACTIVE',
  alarmType: '',
  severity: ''
})

const statusOptions = [
  { label: '活动', value: 'ACTIVE' },
  { label: '恢复', value: 'RECOVERED' },
  { label: '关闭', value: 'RESOLVED' }
]

const severityOptions = [
  { label: '提示', value: 'INFO' },
  { label: '预警', value: 'WARN' },
  { label: '一般', value: 'MINOR' },
  { label: '严重', value: 'MAJOR' },
  { label: '紧急', value: 'CRITICAL' }
]

const activeCount = computed(() => alarms.value.filter((item) => item.status === 'ACTIVE').length)
const ruleAlarmCount = computed(() => alarms.value.filter((item) => item.alarmType === 'POINT_RULE').length)
const highRiskCount = computed(() => alarms.value.filter((item) => ['MAJOR', 'CRITICAL'].includes(item.severity)).length)
const pagedAlarms = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return visibleAlarms.value.slice(start, start + pagination.pageSize)
})

const loadBaseData = async () => {
  const [user, projectList, status] = await Promise.all([
    api.currentUser(),
    api.listProjects(),
    api.getCollectorStatus()
  ])
  currentUser.value = user
  projects.value = projectList || []
  collectorStatus.value = status || {}
}

const loadDevices = async () => {
  devices.value = await api.listDevices({
    projectId: filters.projectId || undefined
  })
}

const loadAlarms = async () => {
  loading.value = true
  try {
    const data = await api.listAlarms({
      status: filters.status || undefined,
      projectId: filters.projectId || undefined,
      deviceId: filters.deviceId || undefined
    })
    alarms.value = (data || []).filter((item) => {
      if (filters.alarmType && item.alarmType !== filters.alarmType) return false
      if (filters.severity && item.severity !== filters.severity) return false
      return true
    })
    pagination.page = 1
    filterLocal()
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

const loadAll = async () => {
  try {
    await loadBaseData()
    await loadDevices()
    await loadAlarms()
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  }
}

const handleAlarmChanged = async () => {
  await loadAlarms()
}

const handleProjectChange = async () => {
  filters.deviceId = null
  await loadDevices()
  await loadAlarms()
}

const filterLocal = () => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) {
    visibleAlarms.value = alarms.value
    pagination.page = 1
    return
  }
  visibleAlarms.value = alarms.value.filter((item) => [
    item.title,
    item.message,
    item.protocolType,
    item.currentValue,
    item.thresholdText,
    item.projectName,
    item.deviceName,
    item.pointLabel,
    item.pointKey,
    item.deviceId,
    item.pointId
  ].some((value) => String(value ?? '').toLowerCase().includes(text)))
  pagination.page = 1
}

const canHandle = (row) => row.status === 'ACTIVE' && hasOperatePermission(row.projectId)

const hasOperatePermission = (projectId) => {
  if (currentUser.value?.roleKey === 'admin') return true
  const permission = currentUser.value?.projectPermissions?.find((item) => item.projectId === projectId)
  return ['OPERATE', 'ADMIN'].includes(permission?.permissionLevel)
}

const resolveAlarm = async (row) => {
  await ElMessageBox.confirm(`确认关闭报警：${row.title || alarmTypeLabel(row.alarmType)}？`, '关闭报警', { type: 'warning' })
  await api.resolveAlarm(row.id)
  ElMessage.success('报警已关闭')
  await loadAll()
}

const createWorkOrder = async (row) => {
  await ElMessageBox.confirm(`确认将报警转为工单：${row.title || alarmTypeLabel(row.alarmType)}？`, '报警转工单', { type: 'warning' })
  await api.createWorkOrderFromAlarm(row.id)
  ElMessage.success('工单已创建')
  await loadAll()
}

const objectText = (row) => {
  const parts = []
  if (row.projectName) parts.push(row.projectName)
  if (row.deviceName) parts.push(row.deviceName)
  if (row.pointLabel || row.pointKey) parts.push(row.pointLabel || row.pointKey)
  if (!row.deviceName && row.deviceId) parts.push(`设备 ${row.deviceId}`)
  if (!row.pointLabel && !row.pointKey && row.pointId) parts.push(`点位 ${row.pointId}`)
  if (row.sourceNode) parts.push(row.sourceNode)
  return parts.join(' / ') || alarmTypeLabel(row.alarmType)
}

const deviceLabel = (item) => `${item.deviceName} · ${item.protocolType}`
const formatValue = (row) => {
  if (row.currentValue == null) return '-'
  const value = Number(row.currentValue)
  const text = Number.isFinite(value) ? value.toFixed(2) : row.currentValue
  return `${text}${row.unit ? ` ${row.unit}` : ''}`
}

const alarmTypeLabel = (type) => {
  if (type === 'POINT_RULE') return '点位规则'
  if (type === 'COLLECT_ERROR') return '采集异常'
  return type || '-'
}

const statusLabel = (status) => {
  if (status === 'ACTIVE') return '活动'
  if (status === 'RECOVERED') return '已恢复'
  if (status === 'RESOLVED') return '已关闭'
  return status || '-'
}

const statusType = (status) => {
  if (status === 'ACTIVE') return 'danger'
  if (status === 'RECOVERED') return 'success'
  return 'info'
}

const severityLabel = (severity) => severityOptions.find((item) => item.value === severity)?.label || severity || '-'

const severityType = (severity) => {
  if (severity === 'CRITICAL') return 'danger'
  if (severity === 'MAJOR') return 'danger'
  if (severity === 'WARN') return 'warning'
  if (severity === 'INFO') return 'info'
  return ''
}

const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'

onMounted(() => {
  window.addEventListener('iiot-alarm-changed', handleAlarmChanged)
  loadAll()
})

onUnmounted(() => {
  window.removeEventListener('iiot-alarm-changed', handleAlarmChanged)
})
</script>

<style scoped>
.alarm-page {
  height: 100%;
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.alarm-side,
.alarm-main {
  min-height: 0;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.alarm-side {
  padding: 18px;
  overflow: auto;
}

.panel-head,
.headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panel-head h2,
.headline h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.panel-head span,
.headline span,
.node-card span,
.metric span,
.object-cell span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.filter-form {
  margin-top: 18px;
}

.node-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.node-card strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
}

.node-card small {
  display: block;
  margin-top: 8px;
  color: #475569;
}

.alarm-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.headline {
  min-height: 78px;
  padding: 0 18px;
  border-bottom: 1px solid #e6edf5;
}

.headline-actions {
  width: min(520px, 48%);
  display: flex;
  gap: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  border-bottom: 1px solid #e6edf5;
  background: #f8fafc;
}

.metric {
  padding: 14px 18px;
  border-right: 1px solid #e6edf5;
}

.metric strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 24px;
}

.metric.danger strong {
  color: #dc2626;
}

.metric.warn strong {
  color: #d97706;
}

.alarm-table {
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

.object-cell {
  display: grid;
  gap: 4px;
}

.object-cell strong {
  color: #111827;
}

.current-value {
  color: #1d4ed8;
  font-family: Consolas, Monaco, monospace;
}

@media (max-width: 1280px) {
  .alarm-page {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .headline {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 18px;
  }

  .headline-actions {
    width: 100%;
  }
}
</style>
