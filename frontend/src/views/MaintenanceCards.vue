<template>
  <div class="card-page">
    <aside class="card-side">
      <div class="side-title">
        <h2>资料筛选</h2>
        <span>查看验收归档后的维修资料卡</span>
      </div>
      <el-form label-position="top" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable filterable placeholder="全部可见项目" @change="handleProjectChange">
            <el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="filters.deviceId" clearable filterable placeholder="全部设备" @change="loadCards">
            <el-option v-for="item in devices" :key="item.id" :label="`${item.deviceName} / ${item.protocolType}`" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="hint-card">
        <strong>自动归档</strong>
        <span>工单验收通过并关闭后，系统会把故障原因、处理措施和结果沉淀成资料卡。</span>
      </div>
    </aside>

    <section class="card-main">
      <div class="headline">
        <div>
          <h2>维修资料卡</h2>
          <span>用于后续同类设备报警时推荐维修经验</span>
        </div>
        <div class="headline-actions">
          <el-input v-model="keyword" clearable placeholder="搜索标题、原因、措施或关键词" :prefix-icon="Search" @input="filterLocal" />
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="pagedCards" height="100%" empty-text="暂无维修资料卡" @row-click="openDetail">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="faultType" label="故障类型" width="140" show-overflow-tooltip />
        <el-table-column prop="faultReason" label="故障原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="processMeasure" label="处理措施" min-width="240" show-overflow-tooltip />
        <el-table-column prop="processResult" label="处理结果" min-width="220" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" width="180" show-overflow-tooltip />
        <el-table-column label="归档时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" plain @click.stop="openDetail(row)">详情</el-button>
            <el-button size="small" type="danger" plain :icon="Delete" @click.stop="deleteCard(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <span>共 {{ visibleCards.length }} 条</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="visibleCards.length"
          layout="sizes, prev, pager, next"
          background
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" size="62%" :with-header="false">
      <div class="detail-panel" v-if="detailCard">
        <div class="detail-head">
          <div>
            <h2>{{ detailCard.title }}</h2>
            <span>归档时间：{{ formatTime(detailCard.createTime) }}</span>
          </div>
          <div class="detail-actions">
            <el-button @click="printDetail">导出 PDF</el-button>
            <el-button type="danger" plain :icon="Delete" @click="deleteCard(detailCard)">删除</el-button>
            <el-button @click="detailVisible = false">关闭</el-button>
          </div>
        </div>

        <div class="detail-meta">
          <div><span>故障类型</span><strong>{{ detailCard.faultType || '-' }}</strong></div>
          <div><span>关键词</span><strong>{{ detailCard.keywords || '-' }}</strong></div>
          <div><span>资料来源</span><strong>工单 #{{ detailCard.workOrderId }}</strong></div>
        </div>

        <div class="detail-section">
          <h3>故障原因</h3>
          <p>{{ detailCard.faultReason || '未填写' }}</p>
        </div>
        <div class="detail-section">
          <h3>处理措施</h3>
          <p>{{ detailCard.processMeasure || '未填写' }}</p>
        </div>
        <div class="detail-section">
          <h3>处理结果</h3>
          <p>{{ detailCard.processResult || '未填写' }}</p>
        </div>

        <div class="detail-section">
          <h3>现场图片</h3>
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
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])
const loading = ref(false)
const projects = ref([])
const devices = ref([])
const cards = ref([])
const visibleCards = ref([])
const detailVisible = ref(false)
const detailCard = ref(null)
const detailAttachments = ref([])
const keyword = ref('')
const filters = reactive({ projectId: null, deviceId: null })
const pagination = reactive({ page: 1, pageSize: 20 })

const pagedCards = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return visibleCards.value.slice(start, start + pagination.pageSize)
})

const loadBaseData = async () => {
  projects.value = await api.listProjects()
}

const loadDevices = async () => {
  devices.value = await api.listDevices({ projectId: filters.projectId || undefined })
}

const loadCards = async () => {
  loading.value = true
  try {
    cards.value = await api.listMaintenanceCards({
      projectId: filters.projectId || undefined,
      deviceId: filters.deviceId || undefined
    })
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
  await loadCards()
}

const handleProjectChange = async () => {
  filters.deviceId = null
  await loadDevices()
  await loadCards()
}

const filterLocal = () => {
  const text = keyword.value.trim().toLowerCase()
  visibleCards.value = text
    ? cards.value.filter((item) => [item.title, item.faultType, item.faultReason, item.processMeasure, item.processResult, item.keywords].some((value) => String(value ?? '').toLowerCase().includes(text)))
    : cards.value
  pagination.page = 1
}

const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'
const imageAttachments = computed(() => detailAttachments.value.filter((item) => String(item.fileUrl || '').match(/\.(png|jpe?g|gif|webp|bmp)$/i)))
const absoluteUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  const normalized = url.startsWith('/') ? url : `/${url}`
  if (window.location.port === '5173') {
    return `${window.location.protocol}//${window.location.hostname}:8080${normalized}`
  }
  return `${window.location.origin}${normalized}`
}

const openDetail = async (row) => {
  try {
    const result = await api.getMaintenanceCard(row.id)
    detailCard.value = result.card
    detailAttachments.value = result.attachments || []
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const deleteCard = async (row) => {
  if (!row) return
  await ElMessageBox.confirm(
    `确认删除维修资料卡「${row.title || row.id}」？原工单和附件不会删除。`,
    '删除维修资料卡',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  )
  await api.deleteMaintenanceCard(row.id)
  ElMessage.success('维修资料卡已删除')
  if (detailCard.value?.id === row.id) {
    detailVisible.value = false
    detailCard.value = null
    detailAttachments.value = []
  }
  await loadCards()
}

const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}[char]))

const printDetail = () => {
  if (!detailCard.value) return
  const images = imageAttachments.value.map((item) => `<img src="${absoluteUrl(item.fileUrl)}" />`).join('')
  const html = `
    <html>
      <head>
        <title>${escapeHtml(detailCard.value.title)}</title>
        <style>
          body { font-family: Arial, "Microsoft YaHei", sans-serif; color: #111827; padding: 32px; }
          h1 { font-size: 24px; margin: 0 0 8px; }
          .meta { color: #667085; margin-bottom: 24px; }
          section { margin-top: 22px; page-break-inside: avoid; }
          h2 { font-size: 16px; border-left: 4px solid #2563eb; padding-left: 10px; }
          p { white-space: pre-wrap; line-height: 1.8; }
          img { width: 220px; height: 160px; object-fit: cover; margin: 8px; border: 1px solid #d8e0ea; }
        </style>
      </head>
      <body>
        <h1>${escapeHtml(detailCard.value.title)}</h1>
        <div class="meta">归档时间：${escapeHtml(formatTime(detailCard.value.createTime))}　来源工单：${escapeHtml(detailCard.value.workOrderId)}</div>
        <section><h2>故障类型</h2><p>${escapeHtml(detailCard.value.faultType || '-')}</p></section>
        <section><h2>故障原因</h2><p>${escapeHtml(detailCard.value.faultReason || '未填写')}</p></section>
        <section><h2>处理措施</h2><p>${escapeHtml(detailCard.value.processMeasure || '未填写')}</p></section>
        <section><h2>处理结果</h2><p>${escapeHtml(detailCard.value.processResult || '未填写')}</p></section>
        <section><h2>现场图片</h2>${images || '<p>暂无图片附件</p>'}</section>
      </body>
    </html>
  `
  const win = window.open('', '_blank')
  win.document.write(html)
  win.document.close()
  win.focus()
  win.print()
}

onMounted(loadAll)
</script>

<style scoped>
.card-page {
  height: 100%;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.card-side,
.card-main {
  min-height: 0;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.card-side {
  padding: 18px;
}

.side-title h2,
.headline h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.side-title span,
.headline span,
.hint-card span {
  display: block;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.filter-form {
  margin-top: 18px;
}

.hint-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.hint-card strong {
  display: block;
  margin-bottom: 6px;
  color: #111827;
}

.card-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.headline {
  min-height: 78px;
  padding: 0 18px;
  border-bottom: 1px solid #e6edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.headline-actions {
  width: min(560px, 50%);
  display: flex;
  gap: 10px;
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

.detail-head {
  padding: 18px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
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

.detail-head span {
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

.detail-meta div,
.detail-section {
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
}

.detail-meta div {
  padding: 14px;
}

.detail-meta span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.detail-meta strong {
  display: block;
  margin-top: 6px;
  color: #111827;
}

.detail-section {
  margin-top: 14px;
  padding: 18px;
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
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.image-grid .el-image {
  width: 100%;
  height: 130px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #f8fafc;
}
</style>
