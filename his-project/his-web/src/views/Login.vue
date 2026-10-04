<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-title">医院信息系统</div>
      <div class="login-subtitle">HIS · 门诊到绩效全闭环（四期）</div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">演示环境初始账号：admin / His@2026</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function redirectAfterLogin() {
  const raw = route.query.redirect
  const redirect = Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '')
  // 仅允许站内相对路径，避免开放重定向
  if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) {
    router.replace(redirect)
  } else {
    router.replace('/')
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    redirectAfterLogin()
  } catch {
    // 错误信息已在响应拦截器中统一提示
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 已登录则直接进入系统
  if (userStore.token) {
    router.replace('/')
  }
})
</script>
