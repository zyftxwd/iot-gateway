import axios from 'axios'

const http = axios.create({
  baseURL: '',
  timeout: 8000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('iiot_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use((response) => {
  const payload = response.data
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (payload.code !== 200) {
      return Promise.reject(new Error(payload.msg || '接口调用失败'))
    }
    return payload.data
  }
  return payload
})

export const api = {
  login(data) {
    return http.post('/api/user/login', data)
  },
  currentUser() {
    return http.get('/api/user/current')
  },
  listAuthUsers() {
    return http.get('/api/auth-admin/users')
  },
  listRoles() {
    return http.get('/api/auth-admin/roles')
  },
  listDepts() {
    return http.get('/api/auth-admin/depts')
  },
  createUser(data) {
    return http.post('/api/auth-admin/users', data)
  },
  deleteUser(userId) {
    return http.delete(`/api/auth-admin/users/${userId}`)
  },
  saveUserStatus(userId, data) {
    return http.post(`/api/auth-admin/users/${userId}/status`, data)
  },
  resetUserPassword(userId, data) {
    return http.post(`/api/auth-admin/users/${userId}/password`, data)
  },
  listUserProjects(userId) {
    return http.get(`/api/auth-admin/users/${userId}/projects`)
  },
  saveUserProject(userId, data) {
    return http.post(`/api/auth-admin/users/${userId}/projects`, data)
  },
  saveUserRole(userId, data) {
    return http.post(`/api/auth-admin/users/${userId}/role`, data)
  },
  saveUserDept(userId, data) {
    return http.post(`/api/auth-admin/users/${userId}/dept`, data)
  },
  listProjects(params) {
    return http.get('/api/projects', { params })
  },
  createProject(data) {
    return http.post('/api/projects', data)
  },
  updateProject(id, data) {
    return http.put(`/api/projects/${id}`, data)
  },
  deleteProject(id) {
    return http.delete(`/api/projects/${id}`)
  },
  listProjectGroups(params) {
    return http.get('/api/projects/groups', { params })
  },
  createProjectGroup(data) {
    return http.post('/api/projects/groups', data)
  },
  updateProjectGroup(id, data) {
    return http.put(`/api/projects/groups/${id}`, data)
  },
  deleteProjectGroup(id) {
    return http.delete(`/api/projects/groups/${id}`)
  },
  getProjectTree(params) {
    return http.get('/api/projects/tree', { params })
  },
  listDevices(params) {
    return http.get('/api/devices', { params })
  },
  listProtocols() {
    return http.get('/api/protocols')
  },
  listInstalledProtocols() {
    return http.get('/api/protocols/installed')
  },
  importProtocolDefinition(data) {
    return http.post('/api/protocols/definitions', data)
  },
  listDeviceTemplates(params) {
    return http.get('/api/device-templates', { params })
  },
  getDeviceTemplate(templateKey) {
    return http.get(`/api/device-templates/${templateKey}`)
  },
  deleteDeviceTemplate(templateKey) {
    return http.delete(`/api/device-templates/${templateKey}`)
  },
  applyDeviceTemplate(templateKey, data) {
    return http.post(`/api/device-templates/${templateKey}/apply`, data)
  },
  getDeviceFullConfig(id) {
    return http.get(`/api/devices/${id}/full-config`)
  },
  createDevice(data) {
    return http.post('/api/devices', data)
  },
  createDevices(data) {
    return http.post('/api/devices/batch', data)
  },
  createFullConfigs(data) {
    return http.post('/api/devices/full-config/batch', data)
  },
  updateDevice(id, data) {
    return http.put(`/api/devices/${id}`, data)
  },
  deleteDevice(id) {
    return http.delete(`/api/devices/${id}`)
  },
  listPoints(params) {
    return http.get('/api/points', { params })
  },
  listPointRuntime(params) {
    return http.get('/api/points/runtime', { params })
  },
  listPointHistory(params) {
    return http.get('/api/history/points', { params })
  },
  writePoint(id, value) {
    return http.post(`/api/points/${id}/write`, { value })
  },
  createPoint(data) {
    return http.post('/api/points', data)
  },
  createPoints(deviceId, data) {
    return http.post('/api/points/batch', data, { params: { deviceId } })
  },
  discoverPoints(deviceId) {
    return http.post('/api/points/discover', null, { params: { deviceId } })
  },
  previewDiscoveredPoints(deviceId) {
    return http.post('/api/points/discover/preview', null, { params: { deviceId } })
  },
  browsePointNodes(deviceId) {
    return http.post('/api/points/browse', null, { params: { deviceId } })
  },
  importPoints(deviceId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post('/api/points/import', formData, {
      params: { deviceId },
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  previewPointImport(deviceId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post('/api/points/import/preview', formData, {
      params: { deviceId },
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  confirmPointImport(deviceId, data) {
    return http.post('/api/points/import/confirm', data, { params: { deviceId } })
  },
  updatePoint(id, data) {
    return http.put(`/api/points/${id}`, data)
  },
  deletePoint(id) {
    return http.delete(`/api/points/${id}`)
  },
  deletePoints(ids) {
    return http.delete('/api/points/batch', { data: ids })
  },
  listAlarms(params) {
    return http.get('/api/alarms', { params })
  },
  listAlarmRules(params) {
    return http.get('/api/alarm-rules', { params })
  },
  createAlarmRule(data) {
    return http.post('/api/alarm-rules', data)
  },
  updateAlarmRule(id, data) {
    return http.put(`/api/alarm-rules/${id}`, data)
  },
  deleteAlarmRule(id) {
    return http.delete(`/api/alarm-rules/${id}`)
  },
  resolveAlarm(id) {
    return http.post(`/api/alarms/${id}/resolve`)
  },
  listWorkOrders(params) {
    return http.get('/api/work-orders', { params })
  },
  getWorkOrder(id) {
    return http.get(`/api/work-orders/${id}`)
  },
  deleteWorkOrder(id) {
    return http.delete(`/api/work-orders/${id}`)
  },
  listWorkOrderCandidates(projectId) {
    return http.get('/api/work-orders/candidates', { params: { projectId } })
  },
  getWorkOrderPolicy(projectId) {
    return http.get(`/api/work-orders/policies/${projectId}`)
  },
  saveWorkOrderPolicy(projectId, data) {
    return http.post(`/api/work-orders/policies/${projectId}`, data)
  },
  createWorkOrder(data) {
    return http.post('/api/work-orders', data)
  },
  createWorkOrderFromAlarm(alarmId) {
    return http.post(`/api/work-orders/from-alarm/${alarmId}`)
  },
  workOrderAction(id, data) {
    return http.post(`/api/work-orders/${id}/action`, data)
  },
  uploadWorkOrderAttachment(id, file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post(`/api/work-orders/${id}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  listMaintenanceCards(params) {
    return http.get('/api/maintenance-cards', { params })
  },
  getMaintenanceCard(id) {
    return http.get(`/api/maintenance-cards/${id}`)
  },
  deleteMaintenanceCard(id) {
    return http.delete(`/api/maintenance-cards/${id}`)
  },
  getCollectorStatus() {
    return http.get('/api/collector/status')
  },
  getDiagnosticsOverview() {
    return http.get('/api/diagnostics/overview')
  },
  getLicenseStatus() {
    return http.get('/api/license/status')
  },
  activateLicense(data) {
    return http.post('/api/license/activate', data)
  },
  listReportTemplates() {
    return http.get('/api/reports/templates')
  },
  listReportSchemes(params) {
    return http.get('/api/reports/schemes', { params })
  },
  saveReportScheme(data) {
    return http.post('/api/reports/schemes', data)
  },
  deleteReportScheme(id) {
    return http.delete(`/api/reports/schemes/${id}`)
  },
  previewReport(data) {
    return http.post('/api/reports/preview', data)
  },
  exportReport(data) {
    return http.post('/api/reports/export', data, { responseType: 'blob' })
  }
}
