<template>
  <div class="template-page">
    <aside class="surface template-side">
      <div class="side-head">
        <div>
          <h2>设备模板</h2>
          <p>内置模板，也可以从现有设备复制完整配置。</p>
        </div>
        <el-button :icon="Refresh" @click="loadAll" />
      </div>

      <div class="side-actions">
        <el-select v-model="protocolFilter" clearable placeholder="全部协议" @change="loadTemplates">
          <el-option label="MODBUS_TCP" value="MODBUS_TCP" />
          <el-option label="MQTT" value="MQTT" />
        </el-select>
        <el-button type="primary" :icon="DocumentCopy" @click="openCopyDialog">复制现有设备</el-button>
      </div>

      <div class="template-list">
        <button
          v-for="item in visibleTemplates"
          :key="item.templateKey"
          :class="['template-item', { active: activeTemplate?.templateKey === item.templateKey }]"
          @click="selectTemplate(item)"
        >
          <span>
            <strong>{{ item.templateName }}</strong>
            <small>{{ item.protocolType }} · {{ item.points?.length || 0 }} 个点位</small>
          </span>
          <el-tag size="small" effect="plain" :type="item.builtin ? 'info' : 'success'">
            {{ item.builtin ? '内置' : '设备复制' }}
          </el-tag>
        </button>
      </div>
    </aside>

    <section class="surface template-main">
      <template v-if="activeTemplate">
        <div class="main-head">
          <div>
            <h2>{{ activeTemplate.templateName }}</h2>
            <p>{{ activeTemplate.description }}</p>
            <div class="tag-row">
              <el-tag type="info">{{ activeTemplate.protocolType }}</el-tag>
              <el-tag v-for="tag in activeTemplate.tags || []" :key="tag" effect="plain">{{ tag }}</el-tag>
            </div>
          </div>
          <div class="head-actions">
            <el-button :icon="DocumentCopy" @click="copyTemplateJson">复制 JSON</el-button>
            <el-button type="primary" :icon="Plus" @click="openApplyDialog">应用模板</el-button>
            <el-button type="danger" plain :icon="Delete" @click="deleteTemplate">删除模板</el-button>
          </div>
        </div>

        <div class="template-grid">
          <div class="metric">
            <span>协议</span>
            <strong>{{ activeTemplate.protocolType }}</strong>
          </div>
          <div class="metric">
            <span>设备类型</span>
            <strong>{{ activeTemplate.deviceType || '-' }}</strong>
          </div>
          <div class="metric">
            <span>采集周期</span>
            <strong>{{ intervalLabel(activeTemplate.device?.collectIntervalMs) }}</strong>
          </div>
          <div class="metric">
            <span>历史策略</span>
            <strong>{{ historyLabel(activeTemplate.device?.historyMode) }}</strong>
          </div>
        </div>

        <div class="config-block">
          <div>
            <span>连接地址</span>
            <strong>{{ activeTemplate.device?.ipAddress || '-' }}:{{ activeTemplate.device?.port || '-' }}</strong>
          </div>
          <div>
            <span>扩展参数</span>
            <code>{{ compactJson(activeTemplate.device?.extConfig) }}</code>
          </div>
        </div>

        <el-table :data="activeTemplate.points || []" height="calc(100vh - 430px)" class="compact-table">
          <el-table-column prop="pointLabel" label="点位名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="pointKey" label="数据标识" min-width="180" show-overflow-tooltip />
          <el-table-column prop="address" label="地址/路径" min-width="160" show-overflow-tooltip />
          <el-table-column prop="dataType" label="类型" width="110" align="center" />
          <el-table-column prop="decimalPlaces" label="小数位" width="90" align="center" />
          <el-table-column prop="unit" label="单位" width="90" align="center" />
          <el-table-column label="读写" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.accessMode === 'READ_WRITE' ? 'warning' : 'info'" effect="plain">
                {{ row.accessMode === 'READ_WRITE' ? '读写' : '只读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="历史" width="130" align="center">
            <template #default="{ row }">
              {{ historyLabel(row.historyMode || 'INHERIT') }}
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="请选择模板" />
    </section>

    <el-dialog v-model="copyVisible" title="从现有设备生成模板" width="680px">
      <el-form label-width="96px">
        <el-form-item label="源项目">
          <el-select v-model="copyForm.projectId" clearable placeholder="全部项目" style="width: 100%">
            <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源设备">
          <el-select v-model="copyForm.deviceId" filterable placeholder="选择已有设备" style="width: 100%">
            <el-option
              v-for="device in copyDeviceOptions"
              :key="device.id"
              :label="`${device.deviceName} / ${device.protocolType} / ${device.ipAddress || '-'}`"
              :value="device.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="copyForm.templateName" placeholder="为空时使用源设备名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button type="primary" @click="createTemplateFromDevice">生成模板</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="applyVisible" title="应用设备模板" width="640px">
      <el-form label-width="96px">
        <el-form-item label="模板">
          <el-input :model-value="activeTemplate?.templateName" disabled />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="applyForm.deviceName" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select v-model="applyForm.projectId" placeholder="选择项目" style="width: 100%" @change="handleProjectChange">
            <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="applyForm.groupId" placeholder="选择分组" style="width: 100%">
            <el-option v-for="group in groupsForProject" :key="group.id" :label="group.groupName" :value="group.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址">
          <div class="address-row">
            <el-input v-model="applyForm.ipAddress" placeholder="IP / Broker 地址" />
            <el-input-number v-model="applyForm.port" :min="1" :max="65535" controls-position="right" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApply">创建设备</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, DocumentCopy, Plus, Refresh } from '@element-plus/icons-vue'
import { api } from '../api'

const templates = ref([])
const copiedTemplates = ref([])
const activeTemplate = ref(null)
const protocolFilter = ref('')
const projects = ref([])
const groups = ref([])
const devices = ref([])
const copyVisible = ref(false)
const applyVisible = ref(false)

const copyForm = reactive({
  projectId: null,
  deviceId: null,
  templateName: ''
})

const applyForm = reactive({
  projectId: null,
  groupId: null,
  deviceName: '',
  ipAddress: '',
  port: null
})

const visibleTemplates = computed(() => {
  const rows = [...copiedTemplates.value, ...templates.value]
  if (!protocolFilter.value) return rows
  return rows.filter((item) => item.protocolType === protocolFilter.value)
})

const groupsForProject = computed(() => groups.value.filter((item) => item.projectId === applyForm.projectId))
const copyDeviceOptions = computed(() => devices.value.filter((item) => !copyForm.projectId || item.projectId === copyForm.projectId))

const loadTemplates = async () => {
  templates.value = await api.listDeviceTemplates({ protocolType: protocolFilter.value || undefined })
  normalizeActiveTemplate()
}

const loadAll = async () => {
  const [templateRows, projectRows, groupRows, deviceRows] = await Promise.all([
    api.listDeviceTemplates({ protocolType: protocolFilter.value || undefined }),
    api.listProjects(),
    api.listProjectGroups(),
    api.listDevices()
  ])
  templates.value = templateRows
  projects.value = projectRows
  groups.value = groupRows
  devices.value = deviceRows
  normalizeActiveTemplate()
}

const normalizeActiveTemplate = () => {
  if (!activeTemplate.value && visibleTemplates.value.length) {
    activeTemplate.value = visibleTemplates.value[0]
    return
  }
  if (activeTemplate.value && !visibleTemplates.value.some((item) => item.templateKey === activeTemplate.value.templateKey)) {
    activeTemplate.value = visibleTemplates.value[0] || null
  }
}

const selectTemplate = (template) => {
  activeTemplate.value = template
}

const openCopyDialog = () => {
  copyForm.projectId = projects.value[0]?.id || null
  copyForm.deviceId = copyDeviceOptions.value[0]?.id || null
  copyForm.templateName = ''
  copyVisible.value = true
}

const createTemplateFromDevice = async () => {
  if (!copyForm.deviceId) {
    ElMessage.warning('请选择源设备')
    return
  }
  const fullConfig = await api.getDeviceFullConfig(copyForm.deviceId)
  const sourceDevice = fullConfig.device || {}
  const template = {
    templateKey: `copied_device_${copyForm.deviceId}_${Date.now()}`,
    templateName: copyForm.templateName || `${sourceDevice.deviceName || '设备'} 复制模板`,
    protocolType: sourceDevice.protocolType,
    deviceType: sourceDevice.deviceType,
    description: `从现有设备 ${sourceDevice.deviceName || copyForm.deviceId} 复制，包含连接参数、扩展参数、历史策略和完整点表。`,
    builtin: false,
    tags: ['现有设备', sourceDevice.protocolType],
    device: cloneDeviceForTemplate(sourceDevice),
    points: (fullConfig.points || []).map(clonePointForTemplate)
  }
  copiedTemplates.value.unshift(template)
  activeTemplate.value = template
  copyVisible.value = false
  ElMessage.success('已从现有设备生成模板')
}

const openApplyDialog = () => {
  if (!activeTemplate.value) return
  applyForm.projectId = projects.value[0]?.id || null
  applyForm.groupId = groups.value.find((item) => item.projectId === applyForm.projectId)?.id || null
  applyForm.deviceName = activeTemplate.value.device?.deviceName || activeTemplate.value.templateName
  applyForm.ipAddress = activeTemplate.value.device?.ipAddress || ''
  applyForm.port = activeTemplate.value.device?.port || null
  applyVisible.value = true
}

const handleProjectChange = () => {
  applyForm.groupId = groups.value.find((item) => item.projectId === applyForm.projectId)?.id || null
}

const submitApply = async () => {
  if (!activeTemplate.value) return
  if (!applyForm.projectId || !applyForm.groupId) {
    ElMessage.warning('请选择项目和分组')
    return
  }
  if (activeTemplate.value.builtin) {
    await api.applyDeviceTemplate(activeTemplate.value.templateKey, applyForm)
  } else {
    await api.createFullConfigs([buildFullConfigFromTemplate(activeTemplate.value)])
  }
  applyVisible.value = false
  ElMessage.success('设备已按模板创建')
}

const buildFullConfigFromTemplate = (template) => {
  const device = cloneDeviceForTemplate(template.device || {})
  device.projectId = applyForm.projectId
  device.groupId = applyForm.groupId
  device.deviceName = applyForm.deviceName || device.deviceName || template.templateName
  device.ipAddress = applyForm.ipAddress
  device.port = applyForm.port
  return {
    device,
    points: (template.points || []).map(clonePointForTemplate)
  }
}

const cloneDeviceForTemplate = (source) => {
  const {
    id, createTime, updateTime, status, lastCollectTime, lastCollectAt, lastErrorMessage,
    ...device
  } = source || {}
  return { ...device }
}

const clonePointForTemplate = (source) => {
  const { id, commDeviceId, createTime, updateTime, ...point } = source || {}
  return { ...point }
}

const copyTemplateJson = async () => {
  await navigator.clipboard.writeText(JSON.stringify(activeTemplate.value, null, 2))
  ElMessage.success('模板 JSON 已复制')
}

const deleteTemplate = async () => {
  if (!activeTemplate.value) return
  const template = activeTemplate.value
  await ElMessageBox.confirm(
    `确认删除模板「${template.templateName}」？已创建的设备和点位不会受影响。`,
    '删除设备模板',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  )
  if (template.builtin) {
    await api.deleteDeviceTemplate(template.templateKey)
    await loadTemplates()
  } else {
    copiedTemplates.value = copiedTemplates.value.filter((item) => item.templateKey !== template.templateKey)
    normalizeActiveTemplate()
  }
  ElMessage.success('模板已删除')
}

const intervalLabel = (value) => {
  if (!value) return '-'
  if (value % 1000 === 0) return `${value / 1000}秒`
  return `${value}毫秒`
}

const historyLabel = (value) => {
  const map = {
    INHERIT: '继承',
    INTERVAL: '周期',
    CHANGE: '变化',
    INTERVAL_CHANGE: '周期+变化',
    DISABLED: '不存'
  }
  return map[value] || value || '-'
}

const compactJson = (value) => {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value))
  } catch {
    return value
  }
}

onMounted(loadAll)
</script>

<style scoped>
.template-page {
  height: 100%;
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 14px;
}

.surface {
  background: #ffffff;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
}

.template-side,
.template-main {
  min-height: 0;
  padding: 18px;
}

.side-head,
.main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.side-actions {
  display: grid;
  gap: 10px;
}

h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.template-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 250px);
  overflow: auto;
}

.template-item {
  width: 100%;
  padding: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  background: #ffffff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  cursor: pointer;
}

.template-item.active {
  background: #eff6ff;
  border-color: #93c5fd;
}

.template-item strong,
.template-item small {
  display: block;
}

.template-item small {
  margin-top: 5px;
  color: #667085;
}

.tag-row,
.head-actions,
.address-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tag-row {
  margin-top: 12px;
  flex-wrap: wrap;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 12px;
}

.metric {
  padding: 14px 18px;
  background: #f8fafc;
  border-right: 1px solid #e4e9f0;
}

.metric:last-child {
  border-right: none;
}

.metric span,
.metric strong,
.config-block span,
.config-block strong {
  display: block;
}

.metric span,
.config-block span {
  color: #667085;
  font-size: 13px;
}

.metric strong {
  margin-top: 6px;
  color: #111827;
  font-size: 20px;
}

.config-block {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.config-block > div {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #fbfdff;
}

.config-block strong,
.config-block code {
  margin-top: 6px;
  color: #111827;
  font-size: 14px;
}

.config-block code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-table {
  border: 1px solid #e4e9f0;
}
</style>
