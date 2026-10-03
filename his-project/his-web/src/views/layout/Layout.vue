<template>
  <el-container class="layout-root">
    <!-- 侧边菜单 -->
    <el-aside class="layout-aside" :width="isCollapse ? '64px' : '220px'">
      <div class="layout-logo">{{ isCollapse ? 'HIS' : '医院信息系统' }}</div>
      <el-menu
        class="layout-menu"
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="#0d0d0d"
        text-color="#909090"
        active-text-color="#e8a040"
      >
        <template v-for="item in visibleTopMenus" :key="item.id">
          <!-- 目录：渲染子菜单（页面） -->
          <el-sub-menu v-if="item.menuType === 1" :index="item.fullPath || `menu-${item.id}`">
            <template #title>
              <el-icon><component :is="menuIcon(item.path)" /></el-icon>
              <span>{{ item.menuName }}</span>
            </template>
            <el-menu-item
              v-for="child in visibleChildren(item)"
              :key="child.id"
              :index="child.fullPath || `menu-${child.id}`"
            >
              {{ child.menuName }}
            </el-menu-item>
          </el-sub-menu>
          <!-- 顶级页面菜单 -->
          <el-menu-item v-else-if="item.menuType === 2" :index="item.fullPath || `menu-${item.id}`">
            <el-icon><component :is="menuIcon(item.fullPath)" /></el-icon>
            <template #title>{{ item.menuName }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="layout-header" height="56px">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <component :is="isCollapse ? Expand : Fold" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle && pageTitle !== '首页'">
              {{ pageTitle }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password" :icon="Lock">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton" divided>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区（路由过渡） -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="ak-page" mode="out-in">
            <component :is="Component" v-if="userStore.firstMenuPath || route.path !== '/'" />
            <el-empty v-else description="暂无可用菜单，请联系管理员分配权限" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="440px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            placeholder="6-32 位，建议字母+数字组合"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ArrowDown,
  Expand,
  Fold,
  Lock,
  Menu as MenuIcon,
  OfficeBuilding,
  Setting,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import type { MenuNode } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta?.title as string) || '')

const visibleTopMenus = computed<MenuNode[]>(() =>
  userStore.menus.filter((m) => m.menuType === 1 || m.menuType === 2)
)

function visibleChildren(item: MenuNode): MenuNode[] {
  return (item.children || []).filter((c) => c.menuType === 1 || c.menuType === 2)
}

/** 按菜单路径映射图标（菜单数据无 icon 字段，此处做静态映射） */
function menuIcon(path?: string | null) {
  if (!path) return MenuIcon
  if (path.startsWith('/system')) return Setting
  if (path.startsWith('/basedata')) return OfficeBuilding
  return MenuIcon
}

const avatarText = computed(() => userStore.realName.slice(0, 1) || 'U')

// ---------------- 修改密码 ----------------
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function handleCommand(command: string) {
  if (command === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdDialogVisible.value = true
  } else if (command === 'logout') {
    handleLogout()
  }
}

async function handleChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await userStore.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功')
    pwdDialogVisible.value = false
  } catch {
    // 错误提示已在拦截器中统一处理
  } finally {
    pwdSubmitting.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>
