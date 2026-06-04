<template>
  <router-view v-if="route.path === '/login'" />
  <el-container v-else class="app-shell">
    <el-aside width="224px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">IoT</div>
        <div>
          <div class="brand-title">IoT Gateway</div>
          <div class="brand-subtitle">工业通讯管理平台</div>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="nav-menu"
        background-color="#111827"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/devices">
          <el-icon><Connection /></el-icon>
          <span>项目设备点表</span>
        </el-menu-item>
        <el-menu-item index="/alarms">
          <el-icon><Warning /></el-icon>
          <span>报警事件</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><TrendCharts /></el-icon>
          <span>历史数据</span>
        </el-menu-item>
        <el-sub-menu index="/reports">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>报表中心</span>
          </template>
          <el-menu-item index="/reports/history">数据采集报表</el-menu-item>
          <el-menu-item index="/reports/alarm">报警统计报表</el-menu-item>
          <el-menu-item index="/reports/work-order">工单维护报表</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/work-orders">
          <el-icon><Tickets /></el-icon>
          <span>工单管理</span>
        </el-menu-item>
        <el-menu-item index="/maintenance-cards">
          <el-icon><Document /></el-icon>
          <span>维修资料卡</span>
        </el-menu-item>
        <el-menu-item index="/device-templates">
          <el-icon><Files /></el-icon>
          <span>设备模板</span>
        </el-menu-item>
        <el-menu-item index="/interfaces">
          <el-icon><Connection /></el-icon>
          <span>接口测试</span>
        </el-menu-item>
        <el-menu-item index="/diagnostics">
          <el-icon><DataAnalysis /></el-icon>
          <span>系统诊断</span>
        </el-menu-item>
        <el-tooltip :disabled="currentUser?.roleKey === 'admin'" content="仅系统管理员可进入" placement="right">
          <el-menu-item index="/license" :disabled="currentUser?.roleKey !== 'admin'">
            <el-icon><Key /></el-icon>
            <span>授权管理</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip :disabled="currentUser?.roleKey === 'admin'" content="仅系统管理员可进入" placement="right">
          <el-menu-item index="/protocols" :disabled="currentUser?.roleKey !== 'admin'">
            <el-icon><SetUp /></el-icon>
            <span>协议插件</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip :disabled="currentUser?.roleKey === 'admin'" content="仅系统管理员可进入" placement="right">
          <el-menu-item index="/permissions" :disabled="currentUser?.roleKey !== 'admin'">
            <el-icon><Lock /></el-icon>
            <span>权限管理</span>
          </el-menu-item>
        </el-tooltip>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <div class="topbar-title">{{ pageTitle }}</div>
          <div class="topbar-subtitle">后端接口 http://127.0.0.1:8080</div>
        </div>
        <div class="topbar-right">
          <div v-if="!alarmBadgeHidden" class="alarm-badge" @click="router.push('/alarms')">
            <el-icon><Warning /></el-icon>
            <span>活动报警</span>
            <strong>{{ activeAlarmCount }}</strong>
            <el-button link size="small" @click.stop="hideAlarmBadge">隐藏</el-button>
          </div>
          <el-button v-else plain size="small" @click="showAlarmBadge">显示报警</el-button>
          <span class="user-chip">{{ currentUser?.nickName || currentUser?.username || '未登录' }} / {{ currentUser?.roleKey || '-' }}</span>
          <span :class="['status-dot', apiOnline ? 'ok' : 'bad']"></span>
          <span>{{ apiOnline ? 'API 在线' : 'API 离线' }}</span>
          <el-button :icon="Refresh" @click="checkApi" />
          <el-button @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view @api-state="apiOnline = $event" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, DataAnalysis, Document, Files, Key, Lock, Refresh, SetUp, Tickets, TrendCharts, Warning } from '@element-plus/icons-vue'
import { api } from './api'

const route = useRoute()
const router = useRouter()
const apiOnline = ref(false)
const currentUser = ref(null)
const activeAlarmCount = ref(0)
const alarmBadgeHidden = ref(localStorage.getItem('iiot_alarm_badge_hidden') === '1')
let monitorSocket = null
let alarmRefreshTimer = null

const pageTitle = computed(() => {
  if (route.path.startsWith('/reports')) return '报表中心'
  if (route.path === '/permissions') return '权限管理'
  if (route.path === '/alarms') return '报警事件'
  if (route.path === '/history') return '历史数据'
  if (route.path === '/work-orders') return '工单管理'
  if (route.path === '/maintenance-cards') return '维修资料卡'
  if (route.path === '/device-templates') return '设备模板'
  if (route.path === '/interfaces') return '接口测试'
  if (route.path === '/diagnostics') return '系统诊断'
  if (route.path === '/license') return '授权管理'
  if (route.path === '/protocols') return '协议插件'
  return '项目设备点表'
})

const checkApi = async () => {
  if (route.path === '/login') return
  try {
    currentUser.value = await api.currentUser()
    window.dispatchEvent(new CustomEvent('iiot-current-user', { detail: currentUser.value }))
    await api.listDevices()
    await refreshActiveAlarmCount()
    connectMonitorSocket()
    apiOnline.value = true
  } catch (error) {
    apiOnline.value = false
    localStorage.removeItem('iiot_token')
    router.push('/login')
  }
}

const refreshActiveAlarmCount = async () => {
  if (route.path === '/login') return
  try {
    const alarms = await api.listAlarms({ status: 'ACTIVE' })
    activeAlarmCount.value = Array.isArray(alarms) ? alarms.length : 0
  } catch (error) {
    activeAlarmCount.value = 0
  }
}

const scheduleAlarmRefresh = () => {
  if (alarmRefreshTimer) window.clearTimeout(alarmRefreshTimer)
  alarmRefreshTimer = window.setTimeout(async () => {
    await refreshActiveAlarmCount()
    window.dispatchEvent(new CustomEvent('iiot-alarm-changed'))
  }, 300)
}

const connectMonitorSocket = () => {
  if (monitorSocket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(monitorSocket.readyState)) return
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsHost = window.location.port === '5173' ? `${window.location.hostname}:8080` : window.location.host
  monitorSocket = new WebSocket(`${protocol}//${wsHost}/ws/monitor`)
  monitorSocket.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data)
      if (payload?.type === 'ALARM_CHANGED') {
        scheduleAlarmRefresh()
      }
    } catch (error) {
      // 兼容点位实时数据数组，非报警消息忽略。
    }
  }
  monitorSocket.onclose = () => {
    if (localStorage.getItem('iiot_token')) {
      window.setTimeout(connectMonitorSocket, 2000)
    }
  }
}

const hideAlarmBadge = () => {
  alarmBadgeHidden.value = true
  localStorage.setItem('iiot_alarm_badge_hidden', '1')
}

const showAlarmBadge = () => {
  alarmBadgeHidden.value = false
  localStorage.removeItem('iiot_alarm_badge_hidden')
  refreshActiveAlarmCount()
}

const logout = () => {
  localStorage.removeItem('iiot_token')
  router.push('/login')
}

onMounted(checkApi)
watch(() => route.path, checkApi)
onUnmounted(() => {
  if (monitorSocket) monitorSocket.close()
  if (alarmRefreshTimer) window.clearTimeout(alarmRefreshTimer)
})
</script>

<style scoped>
.app-shell {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #eef2f6;
}

.sidebar {
  background: #111827;
  border-right: 1px solid #1f2937;
}

.brand {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid #1f2937;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #2563eb;
  color: #fff;
  font-weight: 800;
  letter-spacing: 0;
}

.brand-title {
  color: #fff;
  font-weight: 700;
  font-size: 16px;
}

.brand-subtitle {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}

.nav-menu {
  border-right: none;
  padding: 10px;
}

.topbar {
  height: 72px;
  background: #fff;
  border-bottom: 1px solid #d8e0ea;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.topbar-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.topbar-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #667085;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #344054;
  font-size: 13px;
}

.user-chip {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d8e0ea;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  background: #f8fafc;
  color: #344054;
}

.alarm-badge {
  height: 32px;
  padding: 0 8px 0 10px;
  border: 1px solid #fecaca;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #fff1f2;
  color: #991b1b;
  cursor: pointer;
}

.alarm-badge strong {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #dc2626;
  color: #fff;
  font-size: 13px;
}

.main {
  height: calc(100vh - 72px);
  padding: 14px;
  overflow: hidden;
}
</style>
