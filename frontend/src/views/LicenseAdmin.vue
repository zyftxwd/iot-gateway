<template>
  <div class="license-page">
    <section class="surface license-main">
      <div class="main-head">
        <div>
          <h2>授权管理</h2>
          <p>管理当前服务器的机器码、授权状态、协议许可和设备点位额度。</p>
        </div>
        <el-button :icon="Refresh" @click="loadStatus">刷新</el-button>
      </div>

      <div v-if="status" class="status-grid">
        <div class="metric">
          <span>授权状态</span>
          <strong :class="status.valid ? 'ok' : 'bad'">{{ status.valid ? '有效' : '无效' }}</strong>
        </div>
        <div class="metric">
          <span>授权模式</span>
          <strong>{{ modeLabel(status.mode) }}</strong>
        </div>
        <div class="metric">
          <span>设备额度</span>
          <strong>{{ status.usedDevices }} / {{ status.maxDevices }}</strong>
        </div>
        <div class="metric">
          <span>点位额度</span>
          <strong>{{ status.usedPoints }} / {{ status.maxPoints }}</strong>
        </div>
      </div>

      <div v-if="status" class="quota-row">
        <div>
          <div class="quota-title">设备使用率</div>
          <el-progress :percentage="usagePercent(status.usedDevices, status.maxDevices)" />
        </div>
        <div>
          <div class="quota-title">点位使用率</div>
          <el-progress :percentage="usagePercent(status.usedPoints, status.maxPoints)" />
        </div>
      </div>

      <div v-if="status" class="info-panel">
        <div class="machine-code">
          <span>机器码</span>
          <code>{{ status.machineCode }}</code>
          <el-button link @click="copyMachineCode">复制</el-button>
        </div>
        <div>
          <span>授权编号</span>
          <strong>{{ status.licenseNo || '-' }}</strong>
        </div>
        <div>
          <span>到期时间</span>
          <strong>{{ timeLabel(status.expiresAt) }}</strong>
        </div>
        <div>
          <span>协议许可</span>
          <div class="protocol-tags">
            <el-tag v-for="item in status.enabledProtocols || []" :key="item" size="small">{{ item }}</el-tag>
            <span v-if="!status.enabledProtocols?.length">-</span>
          </div>
        </div>
      </div>

      <el-alert
        v-for="item in status?.warnings || []"
        :key="item"
        :title="item"
        type="warning"
        show-icon
        class="warning-item"
      />
    </section>

    <aside class="surface license-side">
      <h2>导入授权</h2>
      <p>支持授权 JSON 或 Base64(JSON)。后续加密狗也会接入同一套状态接口。</p>
      <el-input
        v-model="licenseText"
        type="textarea"
        :rows="14"
        placeholder='{"licenseNo":"IIOT-001","machineCode":"...","maxDevices":100,"maxPoints":10000,"enabledProtocols":["MODBUS_TCP","MQTT"],"expiresAt":1893427200000}'
      />
      <el-button type="primary" :loading="saving" @click="activate">导入授权</el-button>
    </aside>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api'

const status = ref(null)
const licenseText = ref('')
const saving = ref(false)

const loadStatus = async () => {
  status.value = await api.getLicenseStatus()
}

const activate = async () => {
  if (!licenseText.value.trim()) {
    ElMessage.warning('请粘贴授权内容')
    return
  }
  saving.value = true
  try {
    status.value = await api.activateLicense({ licenseText: licenseText.value.trim() })
    licenseText.value = ''
    ElMessage.success('授权已导入')
  } finally {
    saving.value = false
  }
}

const copyMachineCode = async () => {
  await navigator.clipboard.writeText(status.value?.machineCode || '')
  ElMessage.success('机器码已复制')
}

const timeLabel = (value) => {
  if (!value) return '长期'
  return new Date(value).toLocaleString()
}

const modeLabel = (value) => {
  if (value === 'DEVELOPMENT') return '开发授权'
  if (value === 'SOFTWARE') return '软件授权'
  if (value === 'DONGLE') return '加密狗'
  return value || '-'
}

const usagePercent = (used, max) => {
  if (!max || max <= 0) return 0
  return Math.min(100, Math.round((used / max) * 100))
}

onMounted(loadStatus)
</script>

<style scoped>
.license-page {
  height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 14px;
}

.surface {
  min-height: 0;
  padding: 20px;
  background: #ffffff;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
}

.main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
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

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  overflow: hidden;
}

.metric {
  padding: 16px 18px;
  background: #f8fafc;
  border-right: 1px solid #e4e9f0;
}

.metric:last-child {
  border-right: none;
}

.metric span,
.metric strong,
.info-panel span,
.info-panel strong,
.info-panel code {
  display: block;
}

.metric span,
.info-panel span,
.quota-title {
  color: #667085;
  font-size: 13px;
}

.metric strong {
  margin-top: 8px;
  color: #111827;
  font-size: 24px;
}

.metric strong.ok {
  color: #16a34a;
}

.metric strong.bad {
  color: #dc2626;
}

.quota-row {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quota-row > div {
  padding: 14px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
}

.quota-title {
  margin-bottom: 10px;
}

.info-panel {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-panel > div {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #fbfdff;
}

.info-panel strong,
.info-panel code {
  margin-top: 8px;
  color: #111827;
  font-size: 15px;
  word-break: break-all;
}

.machine-code {
  position: relative;
}

.machine-code .el-button {
  position: absolute;
  right: 12px;
  top: 10px;
}

.protocol-tags {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.warning-item {
  margin-top: 12px;
}

.license-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
