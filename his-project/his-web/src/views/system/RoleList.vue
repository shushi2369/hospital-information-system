<template>
  <div class="page-card">
    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">角色列表</span>
      <el-button v-perm="'sys:role:manage'" type="primary" :icon="Plus" @click="openCreate">
        新增角色
      </el-button>
    </div>

    <!-- 列表（角色数量少，客户端分页展示） -->
    <el-table v-loading="loading" :data="pagedList" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="roleCode" label="角色编码" min-width="140" />
      <el-table-column prop="roleName" label="角色名称" min-width="120" />
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button v-perm="'sys:role:manage'" link type="primary" @click="openAuth(row)">
            菜单授权
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="() => (pageNum = 1)"
      />
    </div>

    <!-- 新增角色弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新增角色" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 DOCTOR，2-32 位字母/数字/下划线" maxlength="32" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" maxlength="32" />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="128" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 菜单授权弹窗 -->
    <el-dialog
      v-model="authDialogVisible"
      :title="`菜单授权 - ${currentRole?.roleName || ''}`"
      width="480px"
      destroy-on-close
    >
      <div v-loading="authLoading" class="auth-tree-box">
        <el-tree
          ref="treeRef"
          :data="menuTree"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="{ label: 'menuName', children: 'children' }"
        />
      </div>
      <template #footer>
        <el-button @click="authDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="authSubmitting" @click="handleSaveAuth">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { ElTree } from 'element-plus'
import {
  createRole,
  getMenuTree,
  getRoleList,
  getRoleMenuIds,
  updateRoleMenus,
  type MenuTreeNode,
  type Role,
} from '@/api/system'
import { toList } from '@/api/request'

// ---------------- 角色列表（客户端分页） ----------------
const loading = ref(false)
const allList = ref<Role[]>([])
const pageNum = ref(1)
const pageSize = ref(10)

const total = computed(() => allList.value.length)
const pagedList = computed(() =>
  allList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value)
)

async function fetchList() {
  loading.value = true
  try {
    allList.value = toList<Role>(await getRoleList())
  } finally {
    loading.value = false
  }
}

// ---------------- 新增角色 ----------------
const createDialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  roleCode: '',
  roleName: '',
  description: '',
})

const formRules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,32}$/, message: '角色编码须为 2-32 位字母、数字或下划线', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function openCreate() {
  form.roleCode = ''
  form.roleName = ''
  form.description = ''
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createRole({
      roleCode: form.roleCode,
      roleName: form.roleName,
      description: form.description || undefined,
    })
    ElMessage.success('新增成功')
    createDialogVisible.value = false
    fetchList()
  } catch {
    // 错误提示已在拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 菜单授权 ----------------
const authDialogVisible = ref(false)
const authLoading = ref(false)
const authSubmitting = ref(false)
const currentRole = ref<Role | null>(null)
const menuTree = ref<MenuTreeNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

/** 收集叶子节点 ID：回显时仅勾选叶子，避免半选父节点把未授权子项全选 */
function collectLeafIds(nodes: MenuTreeNode[], set: Set<number> = new Set<number>()): Set<number> {
  nodes.forEach((node) => {
    if (node.children && node.children.length > 0) {
      collectLeafIds(node.children, set)
    } else {
      set.add(node.id)
    }
  })
  return set
}

const leafIds = computed(() => collectLeafIds(menuTree.value))

async function openAuth(row: Role) {
  currentRole.value = row
  authDialogVisible.value = true
  authLoading.value = true
  try {
    const [tree, menuRes] = await Promise.all([
      getMenuTree(),
      getRoleMenuIds(row.id).catch(() => [] as number[]),
    ])
    menuTree.value = tree ?? []
    await nextTick()
    const echoIds = (menuRes ?? []).filter((id) => leafIds.value.has(id))
    treeRef.value?.setCheckedKeys(echoIds)
  } catch {
    // 错误提示已在拦截器中统一处理
  } finally {
    authLoading.value = false
  }
}

async function handleSaveAuth() {
  if (!currentRole.value) return
  const checked = (treeRef.value?.getCheckedKeys() ?? []) as number[]
  const halfChecked = (treeRef.value?.getHalfCheckedKeys() ?? []) as number[]
  const menuIds = [...halfChecked, ...checked]
  authSubmitting.value = true
  try {
    await updateRoleMenus(currentRole.value.id, menuIds)
    ElMessage.success('授权成功')
    authDialogVisible.value = false
  } catch {
    // 同上
  } finally {
    authSubmitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.auth-tree-box {
  max-height: 420px;
  overflow: auto;
  border: 1px solid #e6e8eb;
  border-radius: 4px;
  padding: 8px;
}
</style>
