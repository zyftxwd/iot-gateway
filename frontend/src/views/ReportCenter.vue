<template>
  <div class="report-page">
    <aside class="report-side">
      <div class="side-head">
        <h2>筛选条件</h2>
        <span>当前报表类型由左侧菜单决定，这里只负责范围和时间。</span>
      </div>

      <div class="scheme-panel">
        <div class="scheme-head">
          <strong>常用方案</strong>
          <el-button size="small" type="primary" plain @click="saveScheme">保存当前</el-button>
        </div>
        <div v-if="reportSchemes.length" class="scheme-list">
          <button v-for="item in reportSchemes" :key="item.id" class="scheme-item" @click="applyScheme(item)">
            <span>{{ item.schemeName }}</span>
            <small>{{ formatTime(item.updateTime) }}</small>
            <el-button size="small" text type="danger" @click.stop="deleteScheme(item)">删除</el-button>
          </button>
        </div>
        <div v-else class="scheme-empty">暂无保存方案</div>
      </div>

      <el-form label-position="top" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable filterable placeholder="全部可见项目" @change="handleProjectChange">
            <el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="filters.groupId" clearable filterable placeholder="全部分组" @change="handleGroupChange">
            <el-option v-for="item in filteredGroups" :key="item.id" :label="item.groupName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="filters.deviceId" clearable filterable placeholder="全部设备" @change="handleDeviceChange">
            <el-option v-for="item in devices" :key="item.id" :label="deviceLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="点位">
          <el-select v-model="filters.pointId" clearable filterable placeholder="全部点位">
            <el-option v-for="item in points" :key="item.id" :label="pointLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="x"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="明细上限">
          <el-input-number v-model="filters.limit" :min="100" :max="5000" :step="100" style="width: 100%" />
        </el-form-item>
      </el-form>

      <div class="side-actions">
        <el-button type="primary" :loading="loading" @click="loadReport">生成报表</el-button>
        <el-button :loading="exporting" @click="exportExcel">导出 Excel</el-button>
      </div>
    </aside>

    <section class="report-main">
      <div class="report-toolbar">
        <div>
          <h2>{{ report?.title || '报表预览' }}</h2>
          <span>{{ report?.subtitle || '选择报表类型和范围后生成预览' }}</span>
        </div>
        <div class="toolbar-actions">
          <el-segmented v-model="layoutMode" :options="layoutOptions" />
          <el-button @click="fieldDrawerVisible = true">字段与版式</el-button>
          <el-button :disabled="!report" @click="openPrintPreview">打印/PDF</el-button>
          <el-button :loading="loading" @click="loadReport">刷新</el-button>
        </div>
      </div>

      <div class="metric-strip">
        <div v-for="metric in report?.metrics || []" :key="metric.key" :class="['metric-box', metric.level || '']">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.unit }}</small>
        </div>
      </div>

      <div :class="['chart-grid', layoutMode]">
        <div v-for="(chart, index) in report?.charts || []" :key="chart.key" class="chart-card">
          <div class="chart-title">{{ chart.title }}</div>
          <div :ref="(el) => setChartRef(el, index)" class="chart-canvas"></div>
        </div>
      </div>

      <div class="table-panel">
        <div class="table-head">
          <div>
            <strong>明细数据</strong>
            <span>{{ report?.rows?.length || 0 }} 条</span>
          </div>
          <span>生成时间：{{ formatTime(report?.generatedAt) }}</span>
        </div>
        <el-table
          v-loading="loading"
          :data="pagedRows"
          :size="tableSize"
          height="100%"
          class="report-table"
          empty-text="暂无报表数据"
        >
          <el-table-column
            v-for="column in visibleColumns"
            :key="column.key"
            :prop="column.key"
            :label="column.label"
            :min-width="column.width || 120"
            :align="column.align || 'left'"
            show-overflow-tooltip
          />
        </el-table>
        <div class="pager-row">
          <span>共 {{ report?.rows?.length || 0 }} 条</span>
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[20, 50, 100, 200]"
            :total="report?.rows?.length || 0"
            layout="sizes, prev, pager, next"
            background
          />
        </div>
      </div>
    </section>

    <el-drawer v-model="fieldDrawerVisible" title="字段与版式" size="360px">
      <div class="drawer-section">
        <h3>表格密度</h3>
        <el-radio-group v-model="tableSize">
          <el-radio-button label="small">紧凑</el-radio-button>
          <el-radio-button label="default">标准</el-radio-button>
          <el-radio-button label="large">宽松</el-radio-button>
        </el-radio-group>
      </div>
      <div class="drawer-section">
        <h3>显示字段</h3>
        <el-checkbox-group v-model="enabledColumnKeys">
          <el-checkbox v-for="column in report?.columns || []" :key="column.key" :label="column.key">
            {{ column.label }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <el-button type="primary" @click="saveLayout">保存当前版式</el-button>
    </el-drawer>

    <el-dialog
      v-model="printPreviewVisible"
      title="报表预览"
      width="1180px"
      top="3vh"
      class="report-print-dialog"
      destroy-on-close
    >
      <div class="report-preview-shell">
        <article class="print-report">
          <section class="print-page">
            <header class="print-page-head">
              <div>
                <div class="print-brand">IoT Gateway</div>
                <h1>{{ report?.title || '工业数据报表' }}</h1>
                <p>{{ report?.subtitle || '工业通讯管理平台报表输出' }}</p>
              </div>
              <div class="print-meta">
                <span>生成时间</span>
                <strong>{{ formatTime(report?.generatedAt) }}</strong>
                <span>数据范围</span>
                <strong>{{ report?.rows?.length || 0 }} 条明细</strong>
                <span>输出页数</span>
                <strong>{{ printPageTotal }} 页</strong>
              </div>
            </header>

            <div class="print-two-column">
              <section class="print-section">
                <h2>筛选范围</h2>
                <table class="print-kv-table">
                  <tbody>
                    <tr v-for="item in scopeRows" :key="item.label">
                      <th>{{ item.label }}</th>
                      <td>{{ item.value }}</td>
                    </tr>
                  </tbody>
                </table>
              </section>

              <section class="print-section">
                <h2>指标摘要</h2>
                <div class="print-metrics">
                  <div v-for="metric in report?.metrics || []" :key="metric.key" class="print-metric">
                    <span>{{ metric.label }}</span>
                    <strong>{{ metric.value }}{{ metric.unit || '' }}</strong>
                  </div>
                </div>
              </section>
            </div>

            <footer class="print-page-foot">摘要页 1 / {{ printPageTotal }}</footer>
          </section>

          <section v-if="printChartImages.length" class="print-page">
            <header class="print-page-title">
              <div>
                <span>图表分析</span>
                <strong>{{ report?.title || '工业数据报表' }}</strong>
              </div>
              <small>{{ formatTime(report?.generatedAt) }}</small>
            </header>
            <div class="print-chart-grid">
              <figure v-for="chart in printChartImages" :key="chart.title" class="print-chart">
                <figcaption>{{ chart.title }}</figcaption>
                <img :src="chart.image" :alt="chart.title" />
              </figure>
            </div>
            <footer class="print-page-foot">图表页 2 / {{ printPageTotal }}</footer>
          </section>

          <section v-for="(rows, pageIndex) in printTablePages" :key="pageIndex" class="print-page print-detail-page">
            <header class="print-page-title">
              <div>
                <span>明细附录</span>
                <strong>{{ report?.title || '工业数据报表' }}</strong>
              </div>
              <small>第 {{ pageIndex + 1 }} 组明细，共展示 {{ printRows.length }} 条</small>
            </header>
            <table class="print-detail-table">
              <thead>
                <tr>
                  <th v-for="column in visibleColumns" :key="column.key">{{ column.label }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
                  <td v-for="column in visibleColumns" :key="column.key">{{ row[column.key] ?? '-' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="pageIndex === printTablePages.length - 1" class="print-note">
              PDF 最多展示前 {{ printRows.length }} 条明细；完整明细请使用 Excel 导出。
            </p>
            <footer class="print-page-foot">明细页 {{ pageIndex + printDetailStart }} / {{ printPageTotal }}</footer>
          </section>
        </article>
      </div>
      <template #footer>
        <el-button @click="printPreviewVisible = false">关闭</el-button>
        <el-button :loading="exporting" @click="exportExcel">导出 Excel</el-button>
        <el-button type="primary" @click="printReport">打印 / 保存 PDF</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const emit = defineEmits(['api-state'])
const route = useRoute()

const projects = ref([])
const groups = ref([])
const devices = ref([])
const points = ref([])
const reportSchemes = ref([])
const report = ref(null)
const loading = ref(false)
const exporting = ref(false)
const autoQueryReady = ref(false)
const fieldDrawerVisible = ref(false)
const printPreviewVisible = ref(false)
const printChartImages = ref([])
const timeRange = ref([])
const enabledColumnKeys = ref([])
const tableSize = ref(localStorage.getItem('iiot_report_table_size') || 'small')
const layoutMode = ref(localStorage.getItem('iiot_report_layout') || 'standard')
const layoutOptions = [
  { label: '标准', value: 'standard' },
  { label: '重点', value: 'focus' },
  { label: '紧凑', value: 'compact' }
]
const pagination = reactive({ page: 1, pageSize: 50 })
const filters = reactive({
  reportType: 'HISTORY',
  projectId: null,
  groupId: null,
  deviceId: null,
  pointId: null,
  limit: 1000
})

let chartEls = []
let chartInstances = []
let autoQueryTimer = null
let pendingQuery = false

const typeMap = {
  history: 'HISTORY',
  alarm: 'ALARM',
  'work-order': 'WORK_ORDER'
}

const filteredGroups = computed(() => {
  if (!filters.projectId) return groups.value
  return groups.value.filter((item) => item.projectId === filters.projectId)
})

const visibleColumns = computed(() => {
  const columns = report.value?.columns || []
  if (!enabledColumnKeys.value.length) return columns
  return columns.filter((item) => enabledColumnKeys.value.includes(item.key))
})

const pagedRows = computed(() => {
  const rows = report.value?.rows || []
  const start = (pagination.page - 1) * pagination.pageSize
  return rows.slice(start, start + pagination.pageSize)
})

const printRows = computed(() => (report.value?.rows || []).slice(0, 300))
const printTablePages = computed(() => {
  const rows = printRows.value
  const pageSize = 18
  if (!rows.length) return [[]]
  const pages = []
  for (let index = 0; index < rows.length; index += pageSize) {
    pages.push(rows.slice(index, index + pageSize))
  }
  return pages
})
const printPageTotal = computed(() => 1 + (printChartImages.value.length ? 1 : 0) + printTablePages.value.length)
const printDetailStart = computed(() => (printChartImages.value.length ? 3 : 2))

const scopeRows = computed(() => [
  { label: '报表类型', value: report.value?.title || '-' },
  { label: '项目', value: selectedLabel(projects.value, filters.projectId, 'id', 'projectName', '全部可见项目') },
  { label: '分组', value: selectedLabel(groups.value, filters.groupId, 'id', 'groupName', '全部分组') },
  { label: '设备', value: selectedLabel(devices.value, filters.deviceId, 'id', 'deviceName', '全部设备') },
  { label: '点位', value: selectedLabel(points.value, filters.pointId, 'id', 'pointLabel', '全部点位') },
  { label: '时间范围', value: timeRange.value?.length === 2 ? `${formatTime(timeRange.value[0])} 至 ${formatTime(timeRange.value[1])}` : '未限制' },
  { label: '明细上限', value: `${filters.limit} 条` }
])

const requestPayload = () => ({
  reportType: filters.reportType,
  projectId: filters.projectId || null,
  groupId: filters.groupId || null,
  deviceId: filters.deviceId || null,
  pointId: filters.pointId || null,
  startTime: timeRange.value?.[0] || null,
  endTime: timeRange.value?.[1] || null,
  limit: filters.limit
})

const layoutPayload = () => ({
  columns: enabledColumnKeys.value,
  tableSize: tableSize.value,
  layoutMode: layoutMode.value,
  pageSize: pagination.pageSize
})

const loadBaseData = async () => {
  const [projectList, groupList] = await Promise.all([
    api.listProjects(),
    api.listProjectGroups()
  ])
  projects.value = projectList || []
  groups.value = groupList || []
}

const loadSchemes = async () => {
  reportSchemes.value = await api.listReportSchemes({ reportType: filters.reportType })
}

const loadDevices = async () => {
  devices.value = await api.listDevices({
    projectId: filters.projectId || undefined,
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

const syncTypeFromRoute = () => {
  filters.reportType = typeMap[route.params.type] || 'HISTORY'
}

const handleProjectChange = async () => {
  filters.groupId = null
  filters.deviceId = null
  filters.pointId = null
  points.value = []
  await loadDevices()
}

const handleGroupChange = async () => {
  filters.deviceId = null
  filters.pointId = null
  points.value = []
  await loadDevices()
}

const handleDeviceChange = async () => {
  filters.pointId = null
  await loadPoints()
}

const loadReport = async (shouldRestoreLayout = true) => {
  if (loading.value) {
    pendingQuery = true
    return
  }
  loading.value = true
  try {
    report.value = await api.previewReport(requestPayload())
    pagination.page = 1
    if (shouldRestoreLayout) {
      restoreLayout()
    }
    await nextTick()
    renderCharts()
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  } finally {
    loading.value = false
    if (pendingQuery) {
      pendingQuery = false
      scheduleAutoQuery(120)
    }
  }
}

const scheduleAutoQuery = (delay = 500) => {
  if (!autoQueryReady.value) return
  window.clearTimeout(autoQueryTimer)
  autoQueryTimer = window.setTimeout(() => {
    loadReport()
  }, delay)
}

const exportExcel = async () => {
  exporting.value = true
  try {
    const blob = await api.exportReport(requestPayload())
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${report.value?.title || '报表'}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    exporting.value = false
  }
}

const preparePrintAssets = async () => {
  if (!report.value) {
    ElMessage.warning('请先生成报表')
    return false
  }
  await nextTick()
  resizeCharts()
  printChartImages.value = chartInstances.map((instance, index) => ({
    title: report.value?.charts?.[index]?.title || `图表 ${index + 1}`,
    image: instance.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff' })
  }))
  await nextTick()
  return true
}

const openPrintPreview = async () => {
  if (await preparePrintAssets()) {
    printPreviewVisible.value = true
  }
}

const printReport = async () => {
  const printWindow = window.open('', '_blank')
  if (!printWindow) {
    ElMessage.error('浏览器阻止了打印窗口，请允许弹窗后重试')
    return
  }
  if (!(await preparePrintAssets())) {
    printWindow.close()
    return
  }
  printWindow.document.open()
  printWindow.document.write(buildPrintDocument())
  printWindow.document.close()
  printWindow.focus()
  window.setTimeout(() => {
    printWindow.print()
  }, 300)
}

const buildPrintDocument = () => {
  const title = escapeHtml(report.value?.title || '工业数据报表')
  const subtitle = escapeHtml(report.value?.subtitle || '工业通讯管理平台报表输出')
  const generatedAt = escapeHtml(formatTime(report.value?.generatedAt))
  const rowCount = report.value?.rows?.length || 0
  const pages = []
  const total = printPageTotal.value

  pages.push(`
    <section class="print-page">
      <header class="print-page-head">
        <div>
          <div class="print-brand">IoT Gateway</div>
          <h1>${title}</h1>
          <p>${subtitle}</p>
        </div>
        <div class="print-meta">
          <span>生成时间</span><strong>${generatedAt}</strong>
          <span>数据范围</span><strong>${rowCount} 条明细</strong>
          <span>输出页数</span><strong>${total} 页</strong>
        </div>
      </header>
      <div class="print-two-column">
        <section class="print-section">
          <h2>筛选范围</h2>
          <table class="print-kv-table"><tbody>
            ${scopeRows.value.map((item) => `<tr><th>${escapeHtml(item.label)}</th><td>${escapeHtml(item.value)}</td></tr>`).join('')}
          </tbody></table>
        </section>
        <section class="print-section">
          <h2>指标摘要</h2>
          <div class="print-metrics">
            ${(report.value?.metrics || []).map((metric) => `
              <div class="print-metric">
                <span>${escapeHtml(metric.label)}</span>
                <strong>${escapeHtml(metric.value)}${escapeHtml(metric.unit || '')}</strong>
              </div>
            `).join('')}
          </div>
        </section>
      </div>
      <footer class="print-page-foot">摘要页 1 / ${total}</footer>
    </section>
  `)

  if (printChartImages.value.length) {
    pages.push(`
      <section class="print-page">
        <header class="print-page-title">
          <div><span>图表分析</span><strong>${title}</strong></div>
          <small>${generatedAt}</small>
        </header>
        <div class="print-chart-grid">
          ${printChartImages.value.map((chart) => `
            <figure class="print-chart">
              <figcaption>${escapeHtml(chart.title)}</figcaption>
              <img src="${chart.image}" alt="${escapeHtml(chart.title)}" />
            </figure>
          `).join('')}
        </div>
        <footer class="print-page-foot">图表页 2 / ${total}</footer>
      </section>
    `)
  }

  const detailStart = printChartImages.value.length ? 3 : 2
  printTablePages.value.forEach((rows, pageIndex) => {
    pages.push(`
      <section class="print-page print-detail-page">
        <header class="print-page-title">
          <div><span>明细附录</span><strong>${title}</strong></div>
          <small>第 ${pageIndex + 1} 组明细，共展示 ${printRows.value.length} 条</small>
        </header>
        <table class="print-detail-table">
          <thead><tr>${visibleColumns.value.map((column) => `<th>${escapeHtml(column.label)}</th>`).join('')}</tr></thead>
          <tbody>
            ${rows.map((row) => `
              <tr>${visibleColumns.value.map((column) => `<td>${escapeHtml(row[column.key])}</td>`).join('')}</tr>
            `).join('')}
          </tbody>
        </table>
        ${pageIndex === printTablePages.value.length - 1 ? `<p class="print-note">PDF 最多展示前 ${printRows.value.length} 条明细；完整明细请使用 Excel 导出。</p>` : ''}
        <footer class="print-page-foot">明细页 ${detailStart + pageIndex} / ${total}</footer>
      </section>
    `)
  })

  return `
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8" />
        <title>${title}</title>
        <style>${standalonePrintCss()}</style>
      </head>
      <body>
        <article class="print-report">${pages.join('')}</article>
      </body>
    </html>
  `
}

const escapeHtml = (value) => String(value ?? '-')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const standalonePrintCss = () => `
  @page { size: A4 landscape; margin: 0; }
  * { box-sizing: border-box; }
  html, body { width: 297mm; min-height: 210mm; }
  body { margin: 0; background: #fff; color: #0f172a; font-family: "Microsoft YaHei", Arial, sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .print-page { width: 297mm; height: 210mm; padding: 10mm 12mm 8mm; display: flex; flex-direction: column; page-break-after: always; break-after: page; overflow: hidden; background: #fff; }
  .print-page:last-child { page-break-after: auto; break-after: auto; }
  .print-page-head { padding-bottom: 18px; border-bottom: 2px solid #1d4ed8; display: flex; justify-content: space-between; gap: 28px; }
  .print-brand { color: #2563eb; font-size: 14px; font-weight: 800; }
  .print-page-head h1 { margin: 14px 0 8px; font-size: 26px; line-height: 1.25; }
  .print-page-head p { margin: 0; color: #475569; font-size: 14px; }
  .print-meta { min-width: 260px; display: grid; align-content: start; grid-template-columns: 86px 1fr; gap: 10px 14px; padding: 14px; border: 1px solid #cbd5e1; background: #f8fafc; font-size: 13px; }
  .print-meta span, .print-note { color: #64748b; }
  .print-two-column { margin-top: 20px; display: grid; grid-template-columns: 1fr 1.2fr; gap: 12px; }
  .print-section h2 { margin: 0 0 10px; padding-left: 10px; border-left: 4px solid #2563eb; font-size: 17px; }
  .print-kv-table, .print-detail-table { width: 100%; border-collapse: collapse; font-size: 11px; }
  .print-kv-table th, .print-kv-table td, .print-detail-table th, .print-detail-table td { border: 1px solid #dbe3ee; padding: 4px 6px; text-align: left; vertical-align: top; }
  .print-kv-table th { width: 110px; background: #f1f5f9; color: #475569; }
  .print-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .print-metric { min-height: 21mm; padding: 8px 10px; border: 1px solid #dbe3ee; background: #f8fafc; break-inside: avoid; }
  .print-metric span { display: block; color: #64748b; font-size: 12px; }
  .print-metric strong { display: block; margin-top: 6px; font-size: 18px; }
  .print-page-title { padding-bottom: 12px; border-bottom: 2px solid #2563eb; display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
  .print-page-title span { display: block; color: #2563eb; font-weight: 800; font-size: 13px; }
  .print-page-title strong { display: block; margin-top: 4px; font-size: 22px; }
  .print-page-title small { color: #64748b; }
  .print-chart-grid { flex: 1; margin-top: 18px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .print-chart { margin: 0; padding: 10px; border: 1px solid #dbe3ee; background: #fff; break-inside: avoid; }
  .print-chart figcaption { margin-bottom: 6px; font-weight: 700; }
  .print-chart img { width: 100%; height: 72mm; object-fit: contain; }
  .print-detail-page .print-detail-table { margin-top: 14px; }
  .print-detail-table th { background: #f1f5f9; color: #334155; }
  .print-detail-table thead { display: table-header-group; }
  .print-detail-table tr { break-inside: avoid; }
  .print-note { margin: 8px 0 0; font-size: 12px; }
  .print-page-foot { margin-top: auto; padding-top: 12px; border-top: 1px solid #e2e8f0; color: #64748b; font-size: 12px; text-align: right; }
`

const saveScheme = async () => {
  const { value } = await ElMessageBox.prompt('请输入方案名称', '保存报表方案', {
    inputPlaceholder: '例如：本月报警闭环分析',
    inputValue: report.value?.title || '',
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputValidator: (text) => {
      if (!text || !text.trim()) return '方案名称不能为空'
      if (text.trim().length > 50) return '方案名称不能超过50个字符'
      return true
    }
  })
  await api.saveReportScheme({
    reportType: filters.reportType,
    schemeName: value.trim(),
    filtersJson: JSON.stringify(requestPayload()),
    layoutJson: JSON.stringify(layoutPayload())
  })
  ElMessage.success('报表方案已保存')
  await loadSchemes()
}

const applyScheme = async (scheme) => {
  autoQueryReady.value = false
  try {
    const savedFilters = parseJson(scheme.filtersJson, {})
    const savedLayout = parseJson(scheme.layoutJson, {})
    filters.reportType = savedFilters.reportType || filters.reportType
    filters.projectId = savedFilters.projectId || null
    filters.groupId = savedFilters.groupId || null
    filters.deviceId = savedFilters.deviceId || null
    filters.pointId = savedFilters.pointId || null
    filters.limit = savedFilters.limit || 1000
    timeRange.value = savedFilters.startTime && savedFilters.endTime ? [savedFilters.startTime, savedFilters.endTime] : []
    await loadDevices()
    await loadPoints()
    applyLayout(savedLayout)
    await loadReport(false)
    applyLayout(savedLayout)
    await nextTick()
    renderCharts()
    ElMessage.success('已应用报表方案')
  } finally {
    autoQueryReady.value = true
  }
}

const deleteScheme = async (scheme) => {
  await ElMessageBox.confirm(`确认删除报表方案：${scheme.schemeName}？`, '删除方案', { type: 'warning' })
  await api.deleteReportScheme(scheme.id)
  ElMessage.success('报表方案已删除')
  await loadSchemes()
}

const setChartRef = (el, index) => {
  if (el) chartEls[index] = el
}

const renderCharts = () => {
  disposeCharts()
  const charts = report.value?.charts || []
  charts.forEach((chart, index) => {
    const el = chartEls[index]
    if (!el) return
    const instance = echarts.init(el)
    instance.setOption(chart.type === 'pie' ? pieOption(chart) : barLineOption(chart))
    chartInstances.push(instance)
  })
  window.setTimeout(resizeCharts, 60)
}

const pieOption = (chart) => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie',
    radius: ['48%', '72%'],
    center: ['50%', '52%'],
    data: chart.data || [],
    label: { color: '#475569' }
  }]
})

const barLineOption = (chart) => ({
  grid: { left: 42, right: 16, top: 24, bottom: 34 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: (chart.data || []).map((item) => item.name), axisLabel: { color: '#64748b' } },
  yAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: '#e5edf6' } } },
  series: [{
    type: chart.type === 'line' ? 'line' : 'bar',
    smooth: chart.type === 'line',
    data: (chart.data || []).map((item) => item.value),
    itemStyle: { color: '#2563eb' },
    lineStyle: { color: '#2563eb', width: 3 },
    areaStyle: chart.type === 'line' ? { color: 'rgba(37, 99, 235, 0.12)' } : undefined
  }]
})

const disposeCharts = () => {
  chartInstances.forEach((item) => item.dispose())
  chartInstances = []
}

const resizeCharts = () => {
  chartInstances.forEach((item) => item.resize())
}

const saveLayout = () => {
  localStorage.setItem(`iiot_report_columns_${filters.reportType}`, JSON.stringify(enabledColumnKeys.value))
  localStorage.setItem('iiot_report_table_size', tableSize.value)
  localStorage.setItem('iiot_report_layout', layoutMode.value)
  ElMessage.success('版式已保存')
}

const applyLayout = (layout) => {
  if (!layout || typeof layout !== 'object') return
  if (layout.tableSize) tableSize.value = layout.tableSize
  if (layout.layoutMode) layoutMode.value = layout.layoutMode
  if (layout.pageSize) pagination.pageSize = layout.pageSize
  const columns = report.value?.columns || []
  if (Array.isArray(layout.columns) && layout.columns.length) {
    enabledColumnKeys.value = layout.columns.filter((key) => columns.some((item) => item.key === key))
  }
}

const parseJson = (text, fallback) => {
  try {
    return text ? JSON.parse(text) : fallback
  } catch (error) {
    return fallback
  }
}

const restoreLayout = () => {
  const columns = report.value?.columns || []
  const saved = localStorage.getItem(`iiot_report_columns_${filters.reportType}`)
  if (saved) {
    try {
      const keys = JSON.parse(saved)
      enabledColumnKeys.value = keys.filter((key) => columns.some((item) => item.key === key))
      if (enabledColumnKeys.value.length) return
    } catch (error) {
      // ignore broken local layout
    }
  }
  enabledColumnKeys.value = columns.map((item) => item.key)
}

const deviceLabel = (item) => `${item.deviceName} / ${item.protocolType}`
const pointLabel = (item) => `${item.pointLabel || item.pointKey} / ${item.pointKey}`
const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'
const selectedLabel = (list, id, key, label, fallback) => {
  if (!id) return fallback
  const item = list.find((entry) => entry[key] === id)
  if (!item) return fallback
  return item[label] || fallback
}

watch(layoutMode, () => {
  nextTick(renderCharts)
})

watch(
  () => [
    filters.projectId,
    filters.groupId,
    filters.deviceId,
    filters.pointId,
    filters.limit,
    timeRange.value?.[0] || null,
    timeRange.value?.[1] || null
  ],
  () => scheduleAutoQuery()
)

watch(() => route.params.type, async () => {
  syncTypeFromRoute()
  restoreLayout()
  await loadSchemes()
  await loadReport()
})

onMounted(async () => {
  try {
    window.addEventListener('resize', resizeCharts)
    syncTypeFromRoute()
    await loadBaseData()
    await loadSchemes()
    await loadDevices()
    await loadReport()
    autoQueryReady.value = true
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  window.clearTimeout(autoQueryTimer)
  disposeCharts()
  chartEls = []
})
</script>

<style scoped>
.report-page {
  height: 100%;
  display: grid;
  grid-template-columns: 328px minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.report-side,
.report-main {
  min-height: 0;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.report-side {
  padding: 18px;
  overflow: auto;
}

.side-head h2,
.report-toolbar h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.side-head span,
.report-toolbar span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 13px;
}

.filter-form {
  margin-top: 16px;
}

.scheme-panel {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #dbe6f2;
  border-radius: 8px;
  background: #f8fbff;
}

.scheme-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.scheme-head strong {
  color: #0f172a;
  font-size: 14px;
}

.scheme-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.scheme-item {
  width: 100%;
  min-height: 44px;
  padding: 7px 8px;
  border: 1px solid #dbe6f2;
  border-radius: 6px;
  background: #fff;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  column-gap: 8px;
  row-gap: 2px;
  text-align: left;
  cursor: pointer;
}

.scheme-item span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-weight: 600;
}

.scheme-item small {
  color: #64748b;
  font-size: 12px;
}

.scheme-item :deep(.el-button) {
  grid-row: span 2;
  align-self: center;
}

.scheme-empty {
  margin-top: 10px;
  color: #94a3b8;
  font-size: 13px;
}

.side-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.report-main {
  display: grid;
  grid-template-rows: auto auto auto minmax(360px, 1fr);
  overflow: hidden;
}

.report-toolbar {
  min-height: 68px;
  padding: 0 18px;
  border-bottom: 1px solid #e6edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(128px, 1fr));
  border-bottom: 1px solid #e6edf5;
  background: #f8fafc;
}

.metric-box {
  min-width: 0;
  min-height: 70px;
  padding: 11px 16px;
  border-right: 1px solid #e6edf5;
  border-bottom: 1px solid #e6edf5;
}

.metric-box span {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.metric-box strong {
  display: inline-block;
  margin-top: 5px;
  color: #0f172a;
  font-size: 23px;
}

.metric-box small {
  margin-left: 4px;
  color: #64748b;
}

.metric-box.danger strong {
  color: #dc2626;
}

.metric-box.warning strong {
  color: #d97706;
}

.metric-box.success strong {
  color: #059669;
}

.chart-grid {
  padding: 12px 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 12px;
  border-bottom: 1px solid #e6edf5;
  background: #fbfdff;
}

.chart-grid.focus {
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
}

.chart-grid.compact {
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  padding-top: 8px;
  padding-bottom: 8px;
}

.chart-card {
  height: 208px;
  border: 1px solid #dbe3ee;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}

.chart-grid.compact .chart-card {
  height: 172px;
}

.chart-title {
  height: 36px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #edf2f7;
  color: #0f172a;
  font-weight: 700;
  font-size: 14px;
}

.chart-canvas {
  height: calc(100% - 36px);
}

.table-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.table-head {
  min-height: 44px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6edf5;
  color: #64748b;
  font-size: 13px;
}

.table-head strong {
  margin-right: 8px;
  color: #0f172a;
  font-size: 15px;
}

.report-table {
  flex: 1;
  min-height: 0;
}

.pager-row {
  min-height: 44px;
  padding: 0 16px;
  border-top: 1px solid #e6edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #64748b;
  font-size: 13px;
}

.drawer-section {
  margin-bottom: 24px;
}

.drawer-section h3 {
  margin: 0 0 12px;
  color: #0f172a;
  font-size: 15px;
}

.drawer-section :deep(.el-checkbox) {
  display: flex;
  margin-right: 0;
  margin-bottom: 10px;
}

.report-preview-shell {
  max-height: 72vh;
  overflow: auto;
  padding: 28px;
  background: #dfe5ec;
}

.print-report {
  display: block;
  margin: 0 auto;
  color: #0f172a;
}

.print-page {
  box-sizing: border-box;
  width: 1120px;
  min-height: 792px;
  margin: 0 auto 26px;
  padding: 34px 40px 30px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
}

.print-page-head {
  padding-bottom: 18px;
  border-bottom: 3px solid #2563eb;
  display: flex;
  justify-content: space-between;
  gap: 28px;
}

.print-brand {
  color: #2563eb;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0;
}

.print-page-head h1 {
  margin: 14px 0 8px;
  font-size: 32px;
  line-height: 1.25;
}

.print-page-head p {
  margin: 0;
  color: #475569;
  font-size: 14px;
}

.print-meta {
  min-width: 260px;
  display: grid;
  align-content: start;
  grid-template-columns: 86px 1fr;
  gap: 10px 14px;
  padding: 16px;
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  font-size: 13px;
}

.print-meta span,
.print-note {
  color: #64748b;
}

.print-two-column {
  margin-top: 20px;
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 16px;
}

.print-section {
  min-width: 0;
}

.print-section h2 {
  margin: 0 0 10px;
  padding-left: 10px;
  border-left: 4px solid #2563eb;
  font-size: 17px;
}

.print-kv-table,
.print-detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.print-kv-table th,
.print-kv-table td,
.print-detail-table th,
.print-detail-table td {
  border: 1px solid #dbe3ee;
  padding: 7px 9px;
  text-align: left;
  vertical-align: top;
}

.print-kv-table th {
  width: 110px;
  background: #f1f5f9;
  color: #475569;
}

.print-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.print-metric {
  min-height: 72px;
  padding: 12px 14px;
  border: 1px solid #dbe3ee;
  background: #f8fafc;
}

.print-metric span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.print-metric strong {
  display: block;
  margin-top: 6px;
  font-size: 22px;
}

.print-page-title {
  padding-bottom: 12px;
  border-bottom: 2px solid #2563eb;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.print-page-title span {
  display: block;
  color: #2563eb;
  font-weight: 800;
  font-size: 13px;
}

.print-page-title strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
}

.print-page-title small {
  color: #64748b;
}

.print-chart-grid {
  flex: 1;
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.print-chart {
  margin: 0;
  padding: 12px;
  border: 1px solid #dbe3ee;
  background: #fff;
}

.print-chart figcaption {
  margin-bottom: 6px;
  font-weight: 700;
}

.print-chart img {
  width: 100%;
  height: 270px;
  object-fit: contain;
}

.print-note {
  margin: 0 0 8px;
  font-size: 12px;
}

.print-detail-table th {
  background: #f1f5f9;
  color: #334155;
}

.print-detail-page .print-detail-table {
  margin-top: 14px;
}

.print-page-foot {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
  color: #64748b;
  font-size: 12px;
  text-align: right;
}

@media (max-width: 1400px) {
  .report-page {
    grid-template-columns: 300px minmax(0, 1fr);
  }

  .metric-strip {
    grid-template-columns: repeat(3, minmax(128px, 1fr));
  }

  .chart-grid,
  .chart-grid.focus,
  .chart-grid.compact {
    grid-template-columns: 1fr;
  }
}

@media print {
  @page {
    size: A4 landscape;
    margin: 0;
  }

  :global(body.iiot-print-report) {
    background: #fff !important;
  }

  :global(body.iiot-print-report *) {
    visibility: hidden !important;
  }

  .print-report,
  .print-report * {
    visibility: visible !important;
  }

  .print-report {
    display: block !important;
    position: absolute !important;
    left: 0 !important;
    top: 0 !important;
    width: 297mm !important;
    min-height: 0 !important;
    padding: 0 !important;
    margin: 0 !important;
    box-shadow: none !important;
    background: #fff !important;
  }

  .report-preview-shell {
    display: block !important;
    max-height: none !important;
    overflow: visible !important;
    padding: 0 !important;
    background: #fff !important;
  }

  .print-page {
    width: 297mm !important;
    min-height: 210mm !important;
    height: 210mm !important;
    margin: 0 !important;
    padding: 10mm 12mm 8mm !important;
    box-shadow: none !important;
    page-break-after: always;
    break-after: page;
    overflow: hidden;
  }

  .print-page:last-child {
    page-break-after: auto;
    break-after: auto;
  }

  .print-section,
  .print-metric,
  .print-chart {
    break-inside: avoid;
  }

  .print-page-head {
    padding-bottom: 18px;
    border-bottom: 2px solid #1d4ed8;
  }

  .print-page-head h1 {
    font-size: 26px;
  }

  .print-two-column {
    grid-template-columns: 1fr 1.2fr;
    gap: 12px;
  }

  .print-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .print-metric {
    min-height: 21mm;
    padding: 8px 10px;
  }

  .print-metric strong {
    font-size: 18px;
  }

  .print-chart-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .print-chart img {
    height: 72mm;
  }

  .print-detail-table {
    page-break-inside: auto;
    font-size: 10px;
  }

  .print-kv-table th,
  .print-kv-table td,
  .print-detail-table th,
  .print-detail-table td {
    padding: 4px 6px;
  }

  .print-detail-table thead {
    display: table-header-group;
  }

  .print-detail-table th {
    background: #f1f5f9;
    color: #334155;
  }

  .print-detail-table tr {
    break-inside: avoid;
  }
}
</style>
