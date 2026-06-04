<template>
  <div class="work-order-page">
    <aside class="work-order-side">
      <div class="side-head">
        <div>
          <h2>工单筛选</h2>
          <span>按项目、设备和状态定位工单</span>
        </div>
        <el-button size="small" :icon="Refresh" plain @click="loadAll" />
      </div>

      <el-form label-position="top" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable filterable placeholder="全部可见项目" @change="handleProjectChange">
            <el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="filters.deviceId" clearable filterable placeholder="全部设备" @change="loadOrders">
            <el-option v-for="item in devices" :key="item.id" :label="`${item.deviceName} / ${item.protocolType}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" @change="loadOrders">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="flow-card">
        <span>标准闭环</span>
        <strong>创建 / 派单 / 接单 / 处理 / 验收 / 自动归档</strong>
        <small>验收通过后自动关闭，并生成维修资料卡，用于后续同类故障推荐。</small>
      </div>
    </aside>

    <section class="work-order-main">
      <div class="headline">
        <div>
          <h2>工单管理</h2>
          <span>承接报警、设备维护和现场处理任务</span>
        </div>
        <div class="headline-actions">
          <el-input v-model="keyword" clearable placeholder="搜索工单号、标题、说明或负责人" :prefix-icon="Search" @input="filterLocal" />
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <div class="metric-grid">
        <div class="metric"><span>当前筛选</span><strong>{{ orders.length }}</strong></div>
        <div class="metric warn"><span>处理中</span><strong>{{ processingCount }}</strong></div>
        <div class="metric"><span>待验收</span><strong>{{ finishedCount }}</strong></div>
        <div class="metric ok"><span>已归档</span><strong>{{ archivedCount }}</strong></div>
      </div>

      <el-table v-loading="loading" :data="pagedOrders" height="100%" class="order-table" empty-text="暂无工单">
        <el-table-column prop="orderNo" label="工单号" width="210" show-overflow-tooltip />
        <el-table-column label="状态" width="112" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" effect="dark">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="210" show-overflow-tooltip />
        <el-table-column label="处理人" width="120" align="center">
          <template #default="{ row }">{{ row.assigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="验收人" width="120" align="center">
          <template #default="{ row }">{{ row.verifierName || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="92" align="center">
          <template #default="{ row }">{{ row.sourceType === 'ALARM' ? '报警' : '手工' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button size="small" plain @click="openDetail(row)">详情</el-button>
              <el-button v-for="action in nextActions(row)" :key="action.value" size="small" plain @click="handleAction(row, action.value)">
                {{ action.label }}
              </el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="deleteOrder(row)">删除</el-button>
              <span v-if="nextActions(row).length === 0" class="muted">{{ passiveActionText(row) }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <span>共 {{ visibleOrders.length }} 条</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="visibleOrders.length"
          layout="sizes, prev, pager, next"
          background
        />
      </div>
    </section>

    <el-dialog v-model="dispatchDialog.visible" title="派发工单" width="560px">
      <el-form label-width="86px">
        <el-form-item label="处理人">
          <el-select v-model="dispatchDialog.assigneeUserId" filterable placeholder="选择现场处理人">
            <el-option v-for="user in assigneeUsers" :key="user.userId" :label="userLabel(user)" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="验收人">
          <el-select v-model="dispatchDialog.verifierUserId" filterable placeholder="选择验收人">
            <el-option v-for="user in verifierUsers" :key="user.userId" :label="userLabel(user)" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划完成">
          <el-date-picker
            v-model="dispatchDialog.plannedFinishTime"
            type="datetime"
            placeholder="可选，不能早于当前时间"
            value-format="x"
            :disabled-date="disablePastDate"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dispatchDialog.remark" type="textarea" :rows="3" placeholder="派单说明、注意事项" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitDispatch">确认派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finishDialog.visible" title="提交处理结果" width="640px">
      <el-form label-width="96px">
        <el-form-item label="故障类型">
          <el-input v-model="finishDialog.faultType" placeholder="例如：传感器异常、线路松动、参数配置错误" />
        </el-form-item>
        <el-form-item label="故障原因" required>
          <el-input v-model="finishDialog.faultReason" type="textarea" :rows="3" placeholder="说明根因或初步判断" />
        </el-form-item>
        <el-form-item label="处理措施" required>
          <el-input v-model="finishDialog.processMeasure" type="textarea" :rows="3" placeholder="记录现场采取的处理动作" />
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="finishDialog.processResult" type="textarea" :rows="3" placeholder="设备恢复情况、遗留问题、建议复查项" />
        </el-form-item>
        <el-form-item label="处理照片">
          <el-upload
            v-model:file-list="finishDialog.files"
            :auto-upload="false"
            multiple
            list-type="picture-card"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitFinish">提交验收</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="64%" :with-header="false">
      <div v-if="detail.order" class="detail-panel">
        <div class="detail-head">
          <div>
            <h2>{{ detail.order.title }}</h2>
            <span>{{ detail.order.orderNo }} / {{ detail.order.sourceType === 'ALARM' ? '报警工单' : '手工工单' }}</span>
          </div>
          <div class="detail-actions">
            <el-button v-if="detail.maintenanceCard" type="primary" plain @click="goMaintenanceCard">维修资料卡</el-button>
            <el-button @click="detailVisible = false">关闭</el-button>
          </div>
        </div>

        <div class="detail-meta">
          <div><span>状态</span><strong>{{ statusLabel(detail.order.status) }}</strong></div>
          <div><span>优先级</span><strong>{{ priorityLabel(detail.order.priority) }}</strong></div>
          <div><span>创建人</span><strong>{{ detail.order.creatorName || '-' }}</strong></div>
          <div><span>处理人</span><strong>{{ detail.order.assigneeName || '-' }}</strong></div>
          <div><span>验收人</span><strong>{{ detail.order.verifierName || '-' }}</strong></div>
          <div><span>计划完成</span><strong>{{ formatTime(detail.order.plannedFinishTime) }}</strong></div>
          <div><span>实际完成</span><strong>{{ formatTime(detail.order.finishTime) }}</strong></div>
          <div><span>验收时间</span><strong>{{ formatTime(detail.order.verifyTime) }}</strong></div>
          <div><span>关闭时间</span><strong>{{ formatTime(detail.order.closeTime) }}</strong></div>
        </div>

        <div class="detail-grid">
          <section class="detail-section">
            <h3>工单说明</h3>
            <p>{{ detail.order.description || '未填写' }}</p>
          </section>
          <section class="detail-section">
            <h3>故障原因</h3>
            <p>{{ detail.order.faultReason || '未填写' }}</p>
          </section>
          <section class="detail-section">
            <h3>处理措施</h3>
            <p>{{ detail.order.processMeasure || '未填写' }}</p>
          </section>
          <section class="detail-section">
            <h3>处理结果</h3>
            <p>{{ detail.order.processResult || '未填写' }}</p>
          </section>
        </div>

        <section class="detail-section">
          <h3>现场附件</h3>
          <div v-if="imageAttachments.length" class="image-grid">
            <el-image
              v-for="item in imageAttachments"
              :key="item.id"
              :src="absoluteUrl(item.fileUrl)"
              :preview-src-list="imageAttachments.map((file) => absoluteUrl(file.fileUrl))"
              fit="cover"
            />
          </div>
          <el-empty v-else description="暂无图片附件" />
        </section>

        <section class="detail-section">
          <h3>流转日志</h3>
          <el-timeline>
            <el-timeline-item
              v-for="item in detail.logs"
              :key="item.id"
              :timestamp="formatTime(item.actionTime)"
              placement="top"
            >
              <div class="log-card">
                <strong>{{ actionLabel(item.action) }}</strong>
                <span>{{ statusLabel(item.fromStatus) }} -> {{ statusLabel(item.toStatus) }}</span>
                <small>{{ item.operatorName || '系统' }}</small>
                <p v-if="item.remark">{{ item.remark }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])
const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const orders = ref([])
const visibleOrders = ref([])
const projects = ref([])
const devices = ref([])
const currentUser = ref(null)
const assigneeUsers = ref([])
const verifierUsers = ref([])
const keyword = ref('')
const pagination = reactive({ page: 1, pageSize: 20 })
const filters = reactive({ projectId: null, deviceId: null, status: '' })
const dispatchDialog = reactive({ visible: false, row: null, assigneeUserId: null, verifierUserId: null, plannedFinishTime: null, remark: '' })
const finishDialog = reactive({ visible: false, row: null, faultType: '', faultReason: '', processMeasure: '', processResult: '', files: [] })
const detailVisible = ref(false)
const detail = reactive({ order: null, logs: [], attachments: [], maintenanceCard: null })

const statusOptions = [
  { label: '待派单', value: 'CREATED' },
  { label: '待接单', value: 'DISPATCHED' },
  { label: '已接单', value: 'ACCEPTED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '待验收', value: 'FINISHED' },
  { label: '已验收', value: 'VERIFIED' },
  { label: '已归档', value: 'CLOSED' }
]

const pagedOrders = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return visibleOrders.value.slice(start, start + pagination.pageSize)
})
const processingCount = computed(() => orders.value.filter((item) => ['ACCEPTED', 'PROCESSING'].includes(item.status)).length)
const finishedCount = computed(() => orders.value.filter((item) => item.status === 'FINISHED').length)
const archivedCount = computed(() => orders.value.filter((item) => item.status === 'CLOSED').length)

const loadBaseData = async () => {
  currentUser.value = await api.currentUser()
  projects.value = await api.listProjects()
}

const loadDevices = async () => {
  devices.value = await api.listDevices({ projectId: filters.projectId || undefined })
}

const loadOrders = async () => {
  loading.value = true
  try {
    orders.value = await api.listWorkOrders({
      status: filters.status || undefined,
      projectId: filters.projectId || undefined,
      deviceId: filters.deviceId || undefined
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
  await loadBaseData()
  await loadDevices()
  await loadOrders()
}

const handleProjectChange = async () => {
  filters.deviceId = null
  await loadDevices()
  await loadOrders()
}

const filterLocal = () => {
  const text = keyword.value.trim().toLowerCase()
  visibleOrders.value = text
    ? orders.value.filter((item) => [item.orderNo, item.title, item.description, item.assigneeName, item.verifierName].some((value) => String(value ?? '').toLowerCase().includes(text)))
    : orders.value
  pagination.page = 1
}

const nextActions = (row) => {
  if (row.status === 'CREATED' && canManageProject(row.projectId)) return [{ label: '派单', value: 'DISPATCH' }]
  if (row.status === 'DISPATCHED' && isAssignee(row)) return [{ label: '接单', value: 'ACCEPT' }]
  if (row.status === 'ACCEPTED' && isAssignee(row)) return [{ label: '开始处理', value: 'START' }]
  if (row.status === 'PROCESSING' && isAssignee(row)) return [{ label: '完成处理', value: 'FINISH' }]
  if (row.status === 'FINISHED' && canVerify(row)) return [{ label: '验收通过', value: 'VERIFY' }, { label: '驳回', value: 'REJECT' }]
  if (row.status === 'VERIFIED' && canVerify(row)) return [{ label: '关闭', value: 'CLOSE' }]
  return []
}

const handleAction = async (row, action) => {
  if (action === 'DISPATCH') {
    openDispatch(row)
    return
  }
  if (action === 'FINISH') {
    openFinish(row)
    return
  }
  if (action === 'REJECT') {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '验收驳回', { inputType: 'textarea', inputPlaceholder: '说明需要重新处理的问题' })
    await submitAction(row, { action, remark: value })
    return
  }
  await ElMessageBox.confirm(`确认执行：${actionLabel(action)}？`, '工单流转', { type: 'warning' })
  await submitAction(row, { action })
}

const openDetail = async (row) => {
  loading.value = true
  try {
    const result = await api.getWorkOrder(row.id)
    detail.order = result.order
    detail.logs = result.logs || []
    detail.attachments = result.attachments || []
    detail.maintenanceCard = result.maintenanceCard || null
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

const deleteOrder = async (row) => {
  await ElMessageBox.confirm(
    `确认删除工单「${row.orderNo || row.title || row.id}」？相关流程日志、附件、维修资料卡会一起清理。`,
    '删除工单',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  )
  await api.deleteWorkOrder(row.id)
  ElMessage.success('工单已删除')
  if (detail.order?.id === row.id) {
    detailVisible.value = false
    detail.order = null
    detail.logs = []
    detail.attachments = []
    detail.maintenanceCard = null
  }
  await loadOrders()
}

const openDispatch = async (row) => {
  dispatchDialog.row = row
  dispatchDialog.assigneeUserId = row.assigneeUserId || null
  dispatchDialog.verifierUserId = row.verifierUserId || null
  dispatchDialog.plannedFinishTime = row.plannedFinishTime || null
  dispatchDialog.remark = row.remark || ''
  try {
    const candidates = await api.listWorkOrderCandidates(row.projectId)
    assigneeUsers.value = candidates.assignees || []
    verifierUsers.value = candidates.verifiers || []
  } catch (error) {
    assigneeUsers.value = []
    verifierUsers.value = []
    ElMessage.error(error.message)
    return
  }
  dispatchDialog.visible = true
}

const submitDispatch = async () => {
  if (dispatchDialog.plannedFinishTime && Number(dispatchDialog.plannedFinishTime) < Date.now()) {
    ElMessage.error('计划完成时间不能早于当前时间')
    return
  }
  const ok = await submitAction(dispatchDialog.row, {
    action: 'DISPATCH',
    assigneeUserId: dispatchDialog.assigneeUserId,
    verifierUserId: dispatchDialog.verifierUserId,
    plannedFinishTime: dispatchDialog.plannedFinishTime,
    remark: dispatchDialog.remark
  })
  if (ok) dispatchDialog.visible = false
}

const openFinish = (row) => {
  finishDialog.row = row
  finishDialog.faultType = row.faultType || ''
  finishDialog.faultReason = row.faultReason || ''
  finishDialog.processMeasure = row.processMeasure || ''
  finishDialog.processResult = row.processResult || ''
  finishDialog.files = []
  finishDialog.visible = true
}

const submitFinish = async () => {
  actionLoading.value = true
  try {
    for (const item of finishDialog.files) {
      if (item.raw) {
        await api.uploadWorkOrderAttachment(finishDialog.row.id, item.raw)
      }
    }
    const ok = await submitAction(finishDialog.row, {
      action: 'FINISH',
      faultType: finishDialog.faultType,
      faultReason: finishDialog.faultReason,
      processMeasure: finishDialog.processMeasure,
      processResult: finishDialog.processResult
    })
    if (ok) finishDialog.visible = false
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    actionLoading.value = false
  }
}

const submitAction = async (row, payload) => {
  actionLoading.value = true
  try {
    await api.workOrderAction(row.id, payload)
    ElMessage.success('工单状态已更新')
    await loadOrders()
    return true
  } catch (error) {
    ElMessage.error(error.message)
    return false
  } finally {
    actionLoading.value = false
  }
}

const actionLabel = (action) => ({
  DISPATCH: '派单',
  ACCEPT: '接单',
  START: '开始处理',
  FINISH: '完成处理',
  VERIFY: '验收通过',
  REJECT: '驳回',
  CLOSE: '关闭',
  CREATE: '创建工单',
  CREATE_FROM_ALARM: '报警转工单',
  AUTO_CLOSE: '自动关闭',
  AUTO_ARCHIVE: '自动归档'
}[action] || action)

const passiveActionText = (row) => {
  if (row.status === 'DISPATCHED') return '待接单'
  if (row.status === 'FINISHED') return '待验收'
  if (row.status === 'VERIFIED') return '待关闭'
  if (row.status === 'CLOSED') return '已结束'
  if (row.status === 'PROCESSING') return '处理中'
  if (row.status === 'ACCEPTED') return '已接单'
  return '-'
}

const statusLabel = (status) => statusOptions.find((item) => item.value === status)?.label || status || '-'
const statusType = (status) => {
  if (status === 'CLOSED') return 'success'
  if (status === 'FINISHED' || status === 'VERIFIED') return 'warning'
  if (status === 'PROCESSING') return 'primary'
  return 'info'
}
const priorityLabel = (priority) => ({
  INFO: '提示',
  WARN: '预警',
  WARNING: '预警',
  MINOR: '一般',
  MAJOR: '严重',
  CRITICAL: '紧急',
  LOW: '预警',
  NORMAL: '一般',
  HIGH: '严重',
  URGENT: '紧急'
}[priority] || priority || '-')
const priorityType = (priority) => {
  if (priority === 'CRITICAL' || priority === 'URGENT') return 'danger'
  if (priority === 'MAJOR' || priority === 'HIGH') return 'danger'
  if (priority === 'WARN' || priority === 'WARNING' || priority === 'LOW') return 'warning'
  if (priority === 'INFO') return 'info'
  return ''
}
const userLabel = (user) => `${user.nickName || user.username} / ${user.username}`
const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'
const imageAttachments = computed(() => detail.attachments.filter((item) => String(item.fileUrl || '').match(/\.(png|jpe?g|gif|webp|bmp)$/i)))
const absoluteUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  const normalized = url.startsWith('/') ? url : `/${url}`
  if (window.location.port === '5173') {
    return `${window.location.protocol}//${window.location.hostname}:8080${normalized}`
  }
  return `${window.location.origin}${normalized}`
}
const goMaintenanceCard = () => {
  detailVisible.value = false
  router.push('/maintenance-cards')
}
const disablePastDate = (date) => date.getTime() < Date.now() - 24 * 60 * 60 * 1000
const currentUserId = () => currentUser.value?.userId
const isAdmin = () => currentUser.value?.roleKey === 'admin'
const projectPermission = (projectId) => currentUser.value?.projectPermissions?.find((item) => item.projectId === projectId)?.permissionLevel
const canManageProject = (projectId) => isAdmin() || projectPermission(projectId) === 'ADMIN'
const isAssignee = (row) => row.assigneeUserId && row.assigneeUserId === currentUserId()
const canVerify = (row) => isAdmin() || (row.verifierUserId && row.verifierUserId === currentUserId()) || canManageProject(row.projectId)

onMounted(loadAll)
</script>

<style scoped>
.work-order-page {
  height: 100%;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.work-order-side,
.work-order-main {
  min-height: 0;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.work-order-side {
  padding: 18px;
  overflow: auto;
}

.side-head,
.headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.side-head h2,
.headline h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.side-head span,
.headline span,
.flow-card span,
.metric span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.filter-form {
  margin-top: 18px;
}

.flow-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.flow-card strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  line-height: 1.6;
}

.flow-card small {
  display: block;
  margin-top: 8px;
  color: #475569;
  line-height: 1.6;
}

.work-order-main {
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
  width: min(560px, 50%);
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

.metric.warn strong {
  color: #d97706;
}

.metric.ok strong {
  color: #16a34a;
}

.order-table {
  flex: 1;
}

.action-cell {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.muted {
  color: #98a2b3;
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

.detail-panel {
  height: 100%;
  overflow: auto;
  padding: 22px;
  background: #f8fafc;
}

.detail-head,
.detail-section,
.detail-meta div {
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
}

.detail-head {
  padding: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.detail-head h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

.detail-head span,
.detail-meta span {
  display: block;
  margin-top: 6px;
  color: #667085;
  font-size: 13px;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-meta {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-meta div {
  padding: 14px;
}

.detail-meta strong {
  display: block;
  margin-top: 6px;
  color: #111827;
}

.detail-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-section {
  margin-top: 14px;
  padding: 18px;
}

.detail-grid .detail-section {
  margin-top: 0;
}

.detail-section h3 {
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 4px solid #2563eb;
  color: #111827;
  font-size: 16px;
}

.detail-section p {
  margin: 0;
  color: #344054;
  line-height: 1.8;
  white-space: pre-wrap;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.image-grid .el-image {
  width: 100%;
  height: 124px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #f8fafc;
}

.log-card {
  padding: 12px 14px;
  border: 1px solid #e6edf5;
  border-radius: 8px;
  background: #fff;
}

.log-card strong,
.log-card span,
.log-card small {
  display: block;
}

.log-card span,
.log-card small {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.log-card p {
  margin-top: 8px;
}

</style>
