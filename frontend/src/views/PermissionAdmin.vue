<template>
  <div v-if="noAccess" class="permission-denied surface">
    <el-icon><Lock /></el-icon>
    <h2>当前账号没有权限管理权限</h2>
    <p>权限管理只开放给系统管理员。请使用 admin 账号登录后创建账号、分配部门和项目权限。</p>
  </div>

  <div v-else class="permission-page">
    <section class="surface account-panel">
      <div class="panel-toolbar">
        <div class="toolbar-title">
          <h2>账号与部门</h2>
          <span>创建账号，维护部门和总角色。</span>
        </div>
        <div class="toolbar-actions">
          <el-input v-model="keyword" placeholder="搜索账号或名称" clearable />
          <el-button type="primary" :icon="Plus" @click="openCreateUser">新增</el-button>
          <el-button class="refresh-btn" :icon="Refresh" @click="loadData" />
        </div>
      </div>

      <div class="summary-strip">
        <div>
          <span>账号</span>
          <strong>{{ users.length }}</strong>
        </div>
        <div>
          <span>部门</span>
          <strong>{{ depts.length }}</strong>
        </div>
        <div>
          <span>角色</span>
          <strong>{{ roles.length }}</strong>
        </div>
      </div>

      <div class="account-list">
        <button
          v-for="user in filteredUsers"
          :key="user.userId"
          :class="['account-item', { active: activeUser?.userId === user.userId }]"
          @click="selectUser(user)"
        >
          <span class="avatar">{{ initials(user) }}</span>
          <span class="account-main">
            <strong>{{ user.nickName || user.username }}</strong>
            <small>{{ user.username }} · {{ deptName(user.deptId) }}</small>
          </span>
          <span class="account-tags">
            <el-tag :type="user.roleKey === 'admin' ? 'danger' : 'info'" effect="plain">
              {{ roleName(user.roleKey) }}
            </el-tag>
            <el-tag v-if="user.status === 'DISABLED'" type="warning" effect="plain">禁用</el-tag>
          </span>
        </button>
      </div>
    </section>

    <section class="surface detail-panel">
      <div class="panel-toolbar detail-head">
        <div>
          <h2>权限分配</h2>
          <span>{{ activeUser ? `${activeUser.nickName || activeUser.username} / ${activeUser.username}` : '请选择账号' }}</span>
        </div>
        <div class="toolbar-actions">
          <el-button
            v-if="activeUser"
            plain
            :icon="Key"
            @click="openResetPassword"
          >
            重置密码
          </el-button>
          <el-button
            v-if="activeUser"
            :type="activeUser.status === 'DISABLED' ? 'success' : 'warning'"
            plain
            :icon="Lock"
            :disabled="isSelf"
            @click="toggleUserStatus"
          >
            {{ activeUser.status === 'DISABLED' ? '启用账号' : '禁用账号' }}
          </el-button>
          <el-button
            v-if="activeUser"
            type="danger"
            plain
            :icon="Delete"
            :disabled="isSelf"
            @click="deleteActiveUser"
          >
            删除账号
          </el-button>
        </div>
      </div>

      <div v-if="activeUser" class="identity-grid">
        <div class="field-card">
          <label>总角色</label>
          <el-select :model-value="activeUser.roleKey" :disabled="isSelf" @change="saveUserRole">
            <el-option
              v-for="role in roles"
              :key="role.roleKey"
              :label="`${role.roleName} / ${role.roleKey}`"
              :value="role.roleKey"
            />
          </el-select>
          <small v-if="isSelf">管理员不能修改自己的总角色。</small>
        </div>

        <div class="field-card">
          <label>所属部门</label>
          <el-select :model-value="activeUser.deptId" placeholder="选择部门" @change="saveUserDept">
            <el-option v-for="dept in depts" :key="dept.deptId" :label="dept.deptName" :value="dept.deptId" />
          </el-select>
        </div>
      </div>

      <div class="role-cards">
        <div v-for="role in roles" :key="role.roleKey" class="role-card">
          <strong>{{ role.roleName }}</strong>
          <span>{{ role.roleKey }} / {{ role.roleScope }}</span>
          <p>{{ role.remark }}</p>
        </div>
      </div>

      <div class="project-section">
        <div class="section-title">
          <h3>项目授权</h3>
          <span v-if="activeUser?.roleKey === 'admin'">系统管理员默认拥有全部项目权限，不需要逐个分配。</span>
          <span v-else>给普通账号分配每个项目的查看、操作、管理权限。</span>
        </div>

        <el-table :data="projects" height="100%">
          <el-table-column prop="projectName" label="项目" min-width="180" />
          <el-table-column prop="projectCode" label="编码" width="160" />
          <el-table-column label="项目权限" width="220" align="center">
            <template #default="{ row }">
              <el-tag v-if="activeUser?.roleKey === 'admin'" type="danger" effect="plain">全部权限</el-tag>
              <el-select
                v-else
                :model-value="projectPermission(row.id)"
                :disabled="!activeUser || isSelf"
                style="width: 160px"
                @change="(value) => saveProjectPermission(row.id, value)"
              >
                <el-option label="无权限" value="NONE" />
                <el-option label="查看 VIEW" value="VIEW" />
                <el-option label="操作 OPERATE" value="OPERATE" />
                <el-option label="管理 ADMIN" value="ADMIN" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增账号" width="560px">
      <el-form :model="createForm" label-width="92px">
        <el-form-item label="账号">
          <el-input v-model="createForm.username" placeholder="例如 zhang_san" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="createForm.nickName" placeholder="例如 张三" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="createForm.deptId" style="width: 100%">
            <el-option v-for="dept in depts" :key="dept.deptId" :label="dept.deptName" :value="dept.deptId" />
          </el-select>
        </el-form-item>
        <el-form-item label="总角色">
          <el-select v-model="createForm.roleKey" style="width: 100%">
            <el-option v-for="role in roles" :key="role.roleKey" :label="`${role.roleName} / ${role.roleKey}`" :value="role.roleKey" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetPasswordVisible" title="重置密码" width="430px">
      <el-form :model="resetPasswordForm" label-width="86px">
        <el-form-item label="账号">
          <el-input :model-value="activeUser ? `${activeUser.nickName || activeUser.username} / ${activeUser.username}` : '-'" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="resetPasswordForm.password" type="password" show-password placeholder="至少 4 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPasswordVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Key, Lock, Plus, Refresh } from '@element-plus/icons-vue'
import { api } from '../api'

const users = ref([])
const roles = ref([])
const depts = ref([])
const projects = ref([])
const activeUser = ref(null)
const currentUser = ref(null)
const userProjects = ref([])
const createDialogVisible = ref(false)
const resetPasswordVisible = ref(false)
const keyword = ref('')
const noAccess = ref(false)
const createForm = reactive({
  username: '',
  nickName: '',
  password: '123456',
  deptId: null,
  roleKey: ''
})
const resetPasswordForm = reactive({
  password: '123456'
})

const filteredUsers = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return users.value
  return users.value.filter((user) => {
    return [user.username, user.nickName, deptName(user.deptId), roleName(user.roleKey)]
      .some((text) => String(text || '').toLowerCase().includes(value))
  })
})

const isSelf = computed(() => activeUser.value && currentUser.value && activeUser.value.userId === currentUser.value.userId)

const roleName = (roleKey) => {
  const role = roles.value.find((item) => item.roleKey === roleKey)
  return role ? role.roleName : roleKey
}

const deptName = (deptId) => {
  const dept = depts.value.find((item) => item.deptId === deptId)
  return dept ? dept.deptName : '-'
}

const initials = (user) => {
  const text = user.nickName || user.username || '?'
  return text.slice(0, 1).toUpperCase()
}

const loadData = async () => {
  const me = await api.currentUser()
  currentUser.value = me
  if (me.roleKey !== 'admin') {
    noAccess.value = true
    return
  }
  noAccess.value = false

  const [userList, roleList, deptList, projectList] = await Promise.all([
    api.listAuthUsers(),
    api.listRoles(),
    api.listDepts(),
    api.listProjects()
  ])
  users.value = userList || []
  roles.value = roleList || []
  depts.value = deptList || []
  projects.value = projectList || []

  if (activeUser.value) {
    const refreshed = users.value.find((user) => user.userId === activeUser.value.userId)
    activeUser.value = refreshed || null
  }
  if (!activeUser.value && users.value.length) {
    await selectUser(users.value[0])
  } else if (activeUser.value) {
    userProjects.value = await api.listUserProjects(activeUser.value.userId)
  }
}

const selectUser = async (row) => {
  activeUser.value = row
  userProjects.value = await api.listUserProjects(row.userId)
}

const projectPermission = (projectId) => {
  const item = userProjects.value.find((permission) => permission.projectId === projectId)
  return item?.permissionLevel || 'NONE'
}

const openCreateUser = () => {
  createForm.username = ''
  createForm.nickName = ''
  createForm.password = '123456'
  createForm.deptId = depts.value[0]?.deptId || null
  createForm.roleKey = roles.value.find((role) => role.roleKey !== 'admin')?.roleKey || roles.value[0]?.roleKey || ''
  createDialogVisible.value = true
}

const createUser = async () => {
  const created = await api.createUser(createForm)
  ElMessage.success('账号已创建')
  createDialogVisible.value = false
  await loadData()
  const target = users.value.find((user) => user.userId === created.userId)
  if (target) await selectUser(target)
}

const deleteActiveUser = async () => {
  if (!activeUser.value || isSelf.value) return
  const user = activeUser.value
  await ElMessageBox.confirm(
    `确认删除账号「${user.nickName || user.username}」？删除后会同步清理该账号的项目授权。`,
    '删除账号',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  )
  await api.deleteUser(user.userId)
  ElMessage.success('账号已删除')
  activeUser.value = null
  userProjects.value = []
  await loadData()
}

const openResetPassword = () => {
  if (!activeUser.value) return
  resetPasswordForm.password = '123456'
  resetPasswordVisible.value = true
}

const submitResetPassword = async () => {
  if (!activeUser.value) return
  await api.resetUserPassword(activeUser.value.userId, { password: resetPasswordForm.password })
  ElMessage.success('密码已重置')
  resetPasswordVisible.value = false
}

const toggleUserStatus = async () => {
  if (!activeUser.value || isSelf.value) return
  const user = activeUser.value
  const nextStatus = user.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED'
  const actionText = nextStatus === 'DISABLED' ? '禁用' : '启用'
  await ElMessageBox.confirm(
    `确认${actionText}账号「${user.nickName || user.username}」？`,
    `${actionText}账号`,
    {
      confirmButtonText: actionText,
      cancelButtonText: '取消',
      type: nextStatus === 'DISABLED' ? 'warning' : 'info'
    }
  )
  await api.saveUserStatus(user.userId, { status: nextStatus })
  user.status = nextStatus
  const target = users.value.find((item) => item.userId === user.userId)
  if (target) target.status = nextStatus
  ElMessage.success(`账号已${actionText}`)
}

const saveUserRole = async (roleKey) => {
  if (!activeUser.value || isSelf.value) return
  await api.saveUserRole(activeUser.value.userId, { roleKey })
  activeUser.value.roleKey = roleKey
  const target = users.value.find((user) => user.userId === activeUser.value.userId)
  if (target) target.roleKey = roleKey
  ElMessage.success('总角色已保存')
}

const saveUserDept = async (deptId) => {
  if (!activeUser.value) return
  await api.saveUserDept(activeUser.value.userId, { deptId })
  activeUser.value.deptId = deptId
  const target = users.value.find((user) => user.userId === activeUser.value.userId)
  if (target) target.deptId = deptId
  ElMessage.success('部门已保存')
}

const saveProjectPermission = async (projectId, value) => {
  if (!activeUser.value || isSelf.value || activeUser.value.roleKey === 'admin') return
  await api.saveUserProject(activeUser.value.userId, {
    projectId,
    permissionLevel: value
  })
  ElMessage.success('项目权限已保存')
  userProjects.value = await api.listUserProjects(activeUser.value.userId)
}

onMounted(loadData)
</script>

<style scoped>
.permission-page {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 14px;
}

.permission-denied {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  text-align: center;
  color: #344054;
}

.permission-denied .el-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: #eef4ff;
  color: #2563eb;
  font-size: 28px;
}

.permission-denied h2 {
  margin: 0;
  font-size: 20px;
}

.permission-denied p {
  margin: 0;
  color: #667085;
}

.account-panel,
.detail-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-toolbar {
  min-height: 120px;
  padding: 0 16px;
  border-bottom: 1px solid #d8e0ea;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.panel-toolbar h2,
.section-title h3 {
  margin: 0;
  font-size: 18px;
}

.panel-toolbar span,
.section-title span,
.field-card small {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 13px;
}

.account-panel .panel-toolbar {
  min-height: 122px;
  padding: 16px;
  align-items: stretch;
  flex-direction: column;
  position: relative;
}

.account-panel .toolbar-title {
  padding-right: 44px;
}

.account-panel .toolbar-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 82px;
  gap: 8px;
}

.account-panel .refresh-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 34px;
  padding: 0;
}

.detail-panel .panel-toolbar {
  min-height: 72px;
}

.detail-panel .toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-bottom: 1px solid #e6edf5;
  background: #f8fafc;
}

.summary-strip div {
  padding: 12px 16px;
  border-right: 1px solid #e6edf5;
}

.summary-strip span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.summary-strip strong {
  display: block;
  margin-top: 4px;
  color: #111827;
  font-size: 22px;
}

.account-list {
  flex: 1;
  min-height: 0;
  padding: 10px;
  overflow: auto;
}

.account-item {
  width: 100%;
  min-height: 68px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  text-align: left;
  cursor: pointer;
}

.account-item:hover {
  background: #f8fafc;
  border-color: #e4e9f0;
}

.account-item.active {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #e0ecff;
  color: #1d4ed8;
  font-weight: 700;
}

.account-main {
  min-width: 0;
}

.account-main strong,
.account-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-main strong {
  color: #111827;
  font-size: 14px;
}

.account-main small {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.account-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.identity-grid {
  padding: 14px 16px;
  border-bottom: 1px solid #d8e0ea;
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 14px;
}

.field-card {
  padding: 12px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #fbfdff;
}

.field-card label {
  display: block;
  margin-bottom: 8px;
  color: #344054;
  font-weight: 600;
}

.field-card :deep(.el-select) {
  width: 100%;
}

.role-cards {
  padding: 14px 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr));
  gap: 12px;
  border-bottom: 1px solid #d8e0ea;
}

.role-card {
  padding: 12px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #f8fafc;
}

.role-card strong {
  display: block;
  color: #111827;
}

.role-card span,
.role-card p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
}

.project-section {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.section-title {
  padding: 14px 16px 10px;
  border-bottom: 1px solid #e6edf5;
}

@media (max-width: 1400px) {
  .permission-page {
    grid-template-columns: 360px minmax(0, 1fr);
  }
}
</style>
