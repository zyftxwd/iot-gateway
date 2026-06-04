import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/devices' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/devices',
    name: 'Devices',
    component: () => import('../views/DeviceList.vue')
  },
  {
    path: '/permissions',
    name: 'Permissions',
    component: () => import('../views/PermissionAdmin.vue')
  },
  {
    path: '/alarms',
    name: 'Alarms',
    component: () => import('../views/AlarmList.vue')
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('../views/HistoryData.vue')
  },
  {
    path: '/reports/:type?',
    name: 'Reports',
    component: () => import('../views/ReportCenter.vue')
  },
  {
    path: '/work-orders',
    name: 'WorkOrders',
    component: () => import('../views/WorkOrderList.vue')
  },
  {
    path: '/maintenance-cards',
    name: 'MaintenanceCards',
    component: () => import('../views/MaintenanceCards.vue')
  },
  {
    path: '/protocols',
    name: 'ProtocolPlugins',
    component: () => import('../views/ProtocolPlugins.vue')
  },
  {
    path: '/device-templates',
    name: 'DeviceTemplates',
    component: () => import('../views/DeviceTemplates.vue')
  },
  {
    path: '/interfaces',
    name: 'ApiTester',
    component: () => import('../views/ApiTester.vue')
  },
  {
    path: '/diagnostics',
    name: 'Diagnostics',
    component: () => import('../views/Diagnostics.vue')
  },
  {
    path: '/license',
    name: 'LicenseAdmin',
    component: () => import('../views/LicenseAdmin.vue')
  },
  { path: '/:pathMatch(.*)*', redirect: '/devices' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('iiot_token')) {
    return '/login'
  }
  return true
})

export default router
