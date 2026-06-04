<template>
  <div class="api-page">
    <aside class="surface api-side">
      <div class="side-head">
        <div>
          <h2>接口目录</h2>
          <p>常用后端接口模板</p>
        </div>
        <el-button :icon="Refresh" @click="runRequest">执行</el-button>
      </div>

      <el-input v-model="keyword" clearable placeholder="搜索接口" />
      <div class="preset-list">
        <button
          v-for="item in filteredPresets"
          :key="item.name"
          :class="['preset-item', { active: activePreset?.name === item.name }]"
          @click="selectPreset(item)"
        >
          <span class="method" :class="item.method.toLowerCase()">{{ item.method }}</span>
          <span>
            <strong>{{ item.name }}</strong>
            <small>{{ item.url }}</small>
          </span>
        </button>
      </div>
    </aside>

    <section class="surface api-main">
      <div class="main-head">
        <div>
          <h2>接口测试</h2>
          <p>使用当前登录账号的 Token 调用接口，便于验证 PC、App 和第三方接入。</p>
        </div>
        <div class="main-tools">
          <el-button plain @click="openSwagger">Swagger 文档</el-button>
          <el-button plain @click="openOpenApiJson">OpenAPI JSON</el-button>
          <div class="result-meta">
            <span>状态 {{ responseStatus || '-' }}</span>
            <span>耗时 {{ responseTime ? `${responseTime} ms` : '-' }}</span>
          </div>
        </div>
      </div>

      <div class="request-line">
        <el-select v-model="method" style="width: 120px">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-input v-model="url" placeholder="/api/..." @keyup.enter="runRequest" />
        <el-button type="primary" :loading="loading" @click="runRequest">发送</el-button>
      </div>

      <div class="editor-grid">
        <div class="editor-panel">
          <div class="panel-title">
            <strong>请求 Body</strong>
            <el-button link @click="formatBody">格式化</el-button>
          </div>
          <el-input v-model="bodyText" type="textarea" :rows="18" class="mono" spellcheck="false" />
        </div>

        <div class="editor-panel">
          <div class="panel-title">
            <strong>响应结果</strong>
            <el-button link @click="copyResponse">复制</el-button>
          </div>
          <pre class="response-box">{{ responseText || '暂无响应' }}</pre>
        </div>
      </div>

      <div class="tips">
        <span>当前 Token：{{ tokenState }}</span>
        <span>GET/DELETE 请求会忽略 Body。</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const presets = [
  { name: '当前用户', method: 'GET', url: '/api/user/current' },
  { name: '项目列表', method: 'GET', url: '/api/projects' },
  { name: '设备列表', method: 'GET', url: '/api/devices' },
  { name: '协议列表', method: 'GET', url: '/api/protocols' },
  { name: '设备模板列表', method: 'GET', url: '/api/device-templates' },
  { name: '点位列表', method: 'GET', url: '/api/points?deviceId=8' },
  { name: '实时数据', method: 'GET', url: '/api/points/runtime?deviceId=8' },
  { name: '发现点位预览', method: 'POST', url: '/api/points/discover/preview?deviceId=8' },
  { name: '写入点位', method: 'POST', url: '/api/points/{pointId}/write', body: { value: 51.23 } },
  { name: '报警事件', method: 'GET', url: '/api/alarms' },
  { name: '工单列表', method: 'GET', url: '/api/work-orders' },
  { name: '维修资料卡', method: 'GET', url: '/api/maintenance-cards' }
]

const keyword = ref('')
const activePreset = ref(presets[0])
const method = ref(presets[0].method)
const url = ref(presets[0].url)
const bodyText = ref('')
const responseText = ref('')
const responseStatus = ref(null)
const responseTime = ref(null)
const loading = ref(false)

const filteredPresets = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return presets
  return presets.filter((item) => `${item.name} ${item.method} ${item.url}`.toLowerCase().includes(value))
})

const tokenState = computed(() => localStorage.getItem('iiot_token') ? '已加载' : '未登录')

const selectPreset = (preset) => {
  activePreset.value = preset
  method.value = preset.method
  url.value = preset.url
  bodyText.value = preset.body ? JSON.stringify(preset.body, null, 2) : ''
  responseText.value = ''
  responseStatus.value = null
  responseTime.value = null
}

const runRequest = async () => {
  if (!url.value.trim()) {
    ElMessage.warning('请输入接口路径')
    return
  }
  loading.value = true
  responseText.value = ''
  const startedAt = performance.now()
  try {
    const headers = {}
    const token = localStorage.getItem('iiot_token')
    if (token) headers.Authorization = `Bearer ${token}`

    const options = { method: method.value, headers }
    if (!['GET', 'DELETE'].includes(method.value) && bodyText.value.trim()) {
      options.headers['Content-Type'] = 'application/json;charset=utf-8'
      options.body = JSON.stringify(JSON.parse(bodyText.value))
    }

    const response = await fetch(url.value.trim(), options)
    responseStatus.value = response.status
    const text = await response.text()
    responseText.value = prettyResponse(text)
  } catch (error) {
    responseStatus.value = 'ERR'
    responseText.value = error.message
  } finally {
    responseTime.value = Math.round(performance.now() - startedAt)
    loading.value = false
  }
}

const prettyResponse = (text) => {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch (error) {
    return text
  }
}

const formatBody = () => {
  if (!bodyText.value.trim()) return
  bodyText.value = JSON.stringify(JSON.parse(bodyText.value), null, 2)
}

const copyResponse = async () => {
  await navigator.clipboard.writeText(responseText.value || '')
  ElMessage.success('响应结果已复制')
}

const openSwagger = () => {
  window.open('http://127.0.0.1:8080/swagger-ui/index.html', '_blank')
}

const openOpenApiJson = () => {
  window.open('http://127.0.0.1:8080/v3/api-docs', '_blank')
}
</script>

<style scoped>
.api-page {
  height: 100%;
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 14px;
}

.surface {
  background: #ffffff;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
}

.api-side,
.api-main {
  min-height: 0;
  padding: 18px;
}

.side-head,
.main-head,
.panel-title,
.main-tools,
.result-meta,
.request-line,
.tips {
  display: flex;
  align-items: center;
  gap: 12px;
}

.side-head,
.main-head,
.panel-title {
  justify-content: space-between;
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

.preset-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preset-item {
  width: 100%;
  padding: 12px;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 10px;
  text-align: left;
  background: #ffffff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  cursor: pointer;
}

.preset-item.active {
  background: #eff6ff;
  border-color: #93c5fd;
}

.preset-item strong,
.preset-item small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-item small {
  margin-top: 4px;
  color: #667085;
}

.method {
  align-self: start;
  padding: 3px 0;
  text-align: center;
  border-radius: 4px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.method.get {
  background: #2563eb;
}

.method.post {
  background: #16a34a;
}

.method.put {
  background: #d97706;
}

.method.delete {
  background: #dc2626;
}

.result-meta {
  color: #475467;
  font-size: 13px;
}

.request-line {
  margin: 18px 0;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(340px, 0.85fr) minmax(420px, 1.15fr);
  gap: 14px;
}

.editor-panel {
  min-width: 0;
}

.panel-title {
  height: 34px;
}

.mono,
.response-box {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.response-box {
  height: 398px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  background: #0f172a;
  color: #dbeafe;
  border-radius: 6px;
  border: 1px solid #1e293b;
  font-size: 13px;
}

.tips {
  margin-top: 14px;
  color: #667085;
  font-size: 13px;
}
</style>
