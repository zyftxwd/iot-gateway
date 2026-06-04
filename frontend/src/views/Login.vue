<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <div class="brand-mark">IoT</div>
        <div>
          <h1>IoT Gateway</h1>
          <p>工业通讯管理平台</p>
        </div>
      </div>

      <el-form :model="form" label-position="top" @keyup.enter="submitLogin">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" class="login-button" :loading="loading" @click="submitLogin">登录</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: '123456'
})

const submitLogin = async () => {
  loading.value = true
  try {
    const result = await api.login(form)
    localStorage.setItem('iiot_token', result.token)
    ElMessage.success('登录成功')
    router.push('/devices')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  width: 100vw;
  height: 100vh;
  display: grid;
  place-items: center;
  background: #eef2f6;
}

.login-panel {
  width: 420px;
  padding: 32px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.12);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #2563eb;
  color: #fff;
  font-weight: 800;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
}

.login-button {
  width: 100%;
}
</style>
