<template>
  <div class="protocol-page">
    <section class="surface protocol-header">
      <div>
        <h2>协议插件</h2>
        <p>管理当前平台支持的协议能力。没有真实采集程序的协议只作为配置定义保存，不会出现在新增设备下拉中。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Upload" type="primary" @click="openImport">导入协议定义</el-button>
        <el-button :icon="Refresh" @click="loadProtocols">刷新</el-button>
      </div>
    </section>

    <section class="summary-grid">
      <div class="surface summary-item">
        <span>已安装</span>
        <strong>{{ protocols.length }}</strong>
      </div>
      <div class="surface summary-item">
        <span>可创建设备</span>
        <strong>{{ enabledCount }}</strong>
      </div>
      <div class="surface summary-item">
        <span>配置定义</span>
        <strong>{{ configOnlyCount }}</strong>
      </div>
    </section>

    <section class="plugin-grid">
      <article v-for="item in protocols" :key="item.protocolType" class="surface plugin-card">
        <div class="plugin-top">
          <div>
            <h3>{{ item.displayName || item.protocolType }}</h3>
            <span class="mono">{{ item.protocolType }} / v{{ item.version || '1.0.0' }}</span>
          </div>
          <el-tag :type="statusType(item)" effect="plain">{{ statusLabel(item) }}</el-tag>
        </div>

        <p>{{ item.description || '-' }}</p>

        <div class="capability-row">
          <el-tag :type="item.supportsDiscovery ? 'success' : 'info'" size="small">发现点位</el-tag>
          <el-tag :type="item.supportsWrite ? 'success' : 'info'" size="small">写入</el-tag>
          <el-tag :type="item.supportsExcelImport ? 'success' : 'info'" size="small">Excel</el-tag>
          <el-tag :type="item.supportsJsonImport ? 'success' : 'info'" size="small">JSON</el-tag>
          <el-tag :type="item.supportsBrowse ? 'success' : 'info'" size="small">浏览</el-tag>
        </div>

        <el-collapse class="field-collapse">
          <el-collapse-item :title="`表单字段 ${item.fields?.length || 0}`">
            <div class="field-list">
              <div v-for="field in item.fields || []" :key="field.target + field.key" class="field-chip">
                <span>{{ field.label || field.key }}</span>
                <strong>{{ field.target === 'DEVICE' ? '设备字段' : '扩展参数' }} / {{ field.type }}</strong>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <div class="plugin-foot">
          <span>{{ item.enabled ? '可在新增设备中选择' : '仅保存定义，不参与采集' }}</span>
        </div>
      </article>
    </section>

    <el-dialog v-model="importDialogVisible" title="导入协议定义" width="720px">
      <div class="import-hint">
        这里导入的是协议能力和表单字段定义，不会导入可执行代码。需要真实采集时，仍然要接入对应的后端协议处理器。
      </div>
      <el-input v-model="definitionText" type="textarea" :rows="18" :placeholder="sampleDefinition" />
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDefinition">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Upload } from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])
const protocols = ref([])
const importDialogVisible = ref(false)
const definitionText = ref('')
const saving = ref(false)

const enabledCount = computed(() => protocols.value.filter((item) => item.enabled).length)
const configOnlyCount = computed(() => protocols.value.filter((item) => item.installStatus === 'CONFIG_ONLY').length)

const sampleDefinition = `{
  "protocolType": "CUSTOM_HTTP",
  "displayName": "自定义 HTTP",
  "description": "第三方网关 HTTP 上报协议定义",
  "version": "1.0.0",
  "supportsDiscovery": false,
  "supportsWrite": false,
  "supportsExcelImport": false,
  "supportsJsonImport": true,
  "supportsBrowse": false,
  "fields": [
    { "key": "baseUrl", "label": "服务地址", "type": "TEXT", "target": "EXT_CONFIG", "required": true },
    { "key": "token", "label": "访问令牌", "type": "PASSWORD", "target": "EXT_CONFIG", "required": false, "advanced": true, "groupName": "认证参数" }
  ]
}`

const statusLabel = (item) => {
  if (item.enabled) return '已启用'
  if (item.installStatus === 'CONFIG_ONLY') return '配置定义'
  if (item.installStatus === 'PLANNED') return '待实现'
  return item.installStatus || '未启用'
}

const statusType = (item) => {
  if (item.enabled) return 'success'
  if (item.installStatus === 'CONFIG_ONLY') return 'warning'
  return 'info'
}

const openImport = () => {
  definitionText.value = sampleDefinition
  importDialogVisible.value = true
}

const saveDefinition = async () => {
  let payload
  try {
    payload = JSON.parse(definitionText.value)
  } catch (error) {
    ElMessage.error('JSON 格式不正确')
    return
  }
  saving.value = true
  try {
    await api.importProtocolDefinition(payload)
    ElMessage.success('协议定义已导入')
    importDialogVisible.value = false
    await loadProtocols()
  } finally {
    saving.value = false
  }
}

const loadProtocols = async () => {
  try {
    protocols.value = await api.listInstalledProtocols()
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
  }
}

onMounted(loadProtocols)
</script>

<style scoped>
.protocol-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.protocol-header {
  min-height: 84px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.protocol-header h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

.protocol-header p,
.import-hint {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 12px;
}

.summary-item {
  padding: 14px 16px;
}

.summary-item span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.summary-item strong {
  display: block;
  margin-top: 8px;
  color: #111827;
  font-size: 28px;
  line-height: 1;
}

.plugin-grid {
  min-height: 0;
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 12px;
  align-content: start;
  overflow: auto;
}

.surface {
  background: #ffffff;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
}

.plugin-card {
  padding: 18px;
}

.plugin-top,
.plugin-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.plugin-top h3 {
  margin: 0 0 4px;
  color: #111827;
  font-size: 18px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  color: #667085;
  font-size: 12px;
}

.plugin-card p {
  min-height: 42px;
  margin: 14px 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.6;
}

.capability-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.field-collapse {
  margin-top: 12px;
}

.field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.field-chip {
  min-width: 116px;
  padding: 8px 10px;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
  background: #f8fafc;
}

.field-chip span {
  display: block;
  color: #111827;
  font-size: 13px;
}

.field-chip strong {
  display: block;
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
  font-weight: 500;
}

.plugin-foot {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #e4e9f0;
  color: #667085;
  font-size: 12px;
}

.import-hint {
  margin-bottom: 12px;
}

@media (max-width: 980px) {
  .summary-grid,
  .plugin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
