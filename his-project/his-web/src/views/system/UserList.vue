<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="用户名">
        <el-input
          v-model="query.username"
          placeholder="请输入用户名"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input
          v-model="query.realName"
          placeholder="请输入姓名"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.roleId" placeholder="全部角色" clearable style="width: 160px">
          <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">用户列表</span>
      <el-button v-perm="'sys:user:create'" type="primary" :icon="Plus" @click="openCreate">
        新增用户
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="phone" label="联系电话" min-width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="170">
        <template #default="{ row }">{{ roleNames(row) || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-perm="'sys:user:update'"
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="() => handleToggleStatus(row)"
          />
          <el-tag v-if="!canUpdate" :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">{{ row.createdAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'sys:user:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-perm="'sys:user:manage'" link type="warning" @click="openResetPwd(row)">
            重置密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑用户' : '新增用户'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="!!editingId"
            placeholder="登录账号，3-32 位字母/数字"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="不少于 6 位"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色（可多选）" style="width: 100%">
            <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="440px" destroy-on-close>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="登录账号">
          <span>{{ resetTarget?.username }}（{{ resetTarget?.realName }}）</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="不少于 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="请再次输入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="handleResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createUser,
  getRoleList,
  getUserPage,
  resetUserPassword,
  updateUser,
  updateUserStatus,
  type Role,
  type SystemUser,
} from '@/api/system'
import { toList } from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPerm('sys:user:update'))

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<SystemUser[]>([])
const total = ref(0)
const roleOptions = ref<Role[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  realName: '',
  roleId: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getUserPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      realName: query.realName || undefined,
      roleId: query.roleId,
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

// 角色列表接口按约定返回数组，toList 做一次兼容防御
async function fetchRoles() {
  try {
    roleOptions.value = toList<Role>(await getRoleList())
  } catch {
    roleOptions.value = []
  }
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.username = ''
  query.realName = ''
  query.roleId = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

/** 兼容 roles 为对象数组/字符串数组、或仅有 roleIds 的多种返回形态 */
function rowRoleIds(row: SystemUser): number[] {
  if (Array.isArray(row.roleIds) && row.roleIds.length > 0) return row.roleIds
  if (Array.isArray(row.roles)) {
    return row.roles
      .map((r) => (typeof r === 'number' ? r : typeof r === 'string' ? Number(r) : r.id))
      .filter((id) => !Number.isNaN(id) && id > 0)
  }
  return []
}

function roleNames(row: SystemUser): string {
  if (Array.isArray(row.roles) && row.roles.length > 0) {
    return row.roles
      .map((r) => {
        if (typeof r === 'string') return r
        return r?.roleName || r?.roleCode || String(r?.id ?? '')
      })
      .join('、')
  }
  const ids = rowRoleIds(row)
  return ids
    .map((id) => roleOptions.value.find((r) => r.id === id)?.roleName ?? String(id))
    .join('、')
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  realName: '',
  password: '',
  phone: '',
  roleIds: [] as number[],
})

const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,32}$/, message: '用户名须为 3-32 位字母、数字或下划线', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  roleIds: [{ required: true, type: 'array', message: '请至少选择一个角色', trigger: 'change' }],
}

function openCreate() {
  editingId.value = null
  form.username = ''
  form.realName = ''
  form.password = ''
  form.phone = ''
  form.roleIds = []
  dialogVisible.value = true
}

function openEdit(row: SystemUser) {
  editingId.value = row.id
  form.username = row.username
  form.realName = row.realName
  form.password = ''
  form.phone = row.phone ?? ''
  form.roleIds = rowRoleIds(row)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        realName: form.realName,
        phone: form.phone || undefined,
        roleIds: form.roleIds,
      })
      ElMessage.success('修改成功')
    } else {
      await createUser({
        username: form.username,
        realName: form.realName,
        password: form.password,
        phone: form.phone || undefined,
        roleIds: form.roleIds,
      })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // 错误提示已在拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 启用 / 禁用 ----------------
async function handleToggleStatus(row: SystemUser) {
  const target = row.status === 1 ? 0 : 1
  try {
    await updateUserStatus(row.id, target)
    row.status = target
    ElMessage.success(target === 1 ? '已启用' : '已禁用')
  } catch {
    // 失败时保持原状态（:model-value 单向绑定自动回显）
  }
}

// ---------------- 重置密码 ----------------
const resetDialogVisible = ref(false)
const resetSubmitting = ref(false)
const resetTarget = ref<SystemUser | null>(null)
const resetFormRef = ref<FormInstance>()
const resetForm = reactive({
  newPassword: '',
  confirmPassword: '',
})

const resetRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== resetForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function openResetPwd(row: SystemUser) {
  resetTarget.value = row
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetDialogVisible.value = true
}

async function handleResetPwd() {
  const valid = await resetFormRef.value?.validate().catch(() => false)
  if (!valid || !resetTarget.value) return
  resetSubmitting.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetForm.newPassword)
    ElMessage.success('密码重置成功')
    resetDialogVisible.value = false
  } catch {
    // 同上
  } finally {
    resetSubmitting.value = false
  }
}

onMounted(() => {
  fetchRoles()
  fetchList()
})
</script>
