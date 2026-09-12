<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="科室类型">
        <el-select v-model="query.deptType" placeholder="全部类型" clearable style="width: 160px">
          <el-option v-for="opt in DEPT_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="fetchList">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">科室列表</span>
      <el-button v-perm="'basedata:dept:manage'" type="primary" :icon="Plus" @click="openCreate">
        新增科室
      </el-button>
    </div>

    <!-- 列表（接口非分页，客户端分页展示） -->
    <el-table v-loading="loading" :data="pagedList" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="deptCode" label="科室编码" min-width="120" />
      <el-table-column prop="deptName" label="科室名称" min-width="130" />
      <el-table-column label="科室类型" min-width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.deptType)">{{ deptTypeLabel(row.deptType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="location" label="位置" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.location || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-perm="'basedata:dept:manage'"
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="() => handleToggleStatus(row)"
          />
          <el-tag v-if="!canManage" :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">{{ row.updatedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'basedata:dept:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑科室' : '新增科室'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="科室编码" prop="deptCode">
          <el-input v-model="form.deptCode" placeholder="字母/数字，如 DEPT006" maxlength="32" />
        </el-form-item>
        <el-form-item label="科室名称" prop="deptName">
          <el-input v-model="form.deptName" maxlength="64" placeholder="请输入科室名称" />
        </el-form-item>
        <el-form-item label="科室类型" prop="deptType">
          <el-select v-model="form.deptType" placeholder="请选择科室类型" style="width: 100%">
            <el-option v-for="opt in DEPT_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" maxlength="64" placeholder="楼层/诊区，选填" />
        </el-form-item>
        <el-form-item v-if="editingId" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createDepartment,
  DEPT_TYPE_OPTIONS,
  deptTypeLabel,
  getDepartmentList,
  updateDepartment,
  type Department,
} from '@/api/basedata'
import { toList } from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPerm('basedata:dept:manage'))

// ---------------- 列表（非分页接口 + 客户端分页） ----------------
const loading = ref(false)
const allList = ref<Department[]>([])
const pageNum = ref(1)
const pageSize = ref(10)

const query = reactive({
  deptType: undefined as number | undefined,
  status: undefined as number | undefined,
})

const total = computed(() => allList.value.length)
const pagedList = computed(() =>
  allList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value)
)

async function fetchList() {
  loading.value = true
  try {
    const res = await getDepartmentList({
      deptType: query.deptType,
      status: query.status,
    })
    allList.value = toList<Department>(res)
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.deptType = undefined
  query.status = undefined
  fetchList()
}

function typeTag(type: number) {
  const map: Record<number, 'primary' | 'success' | 'warning' | 'info'> = {
    1: 'primary',
    2: 'success',
    3: 'warning',
    4: 'info',
  }
  return map[type] ?? 'info'
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  deptCode: '',
  deptName: '',
  deptType: undefined as number | undefined,
  location: '',
  status: 1,
})

const formRules: FormRules = {
  deptCode: [
    { required: true, message: '请输入科室编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{1,32}$/, message: '编码仅允许字母与数字', trigger: 'blur' },
  ],
  deptName: [{ required: true, message: '请输入科室名称', trigger: 'blur' }],
  deptType: [{ required: true, message: '请选择科室类型', trigger: 'change' }],
}

function openCreate() {
  editingId.value = null
  form.deptCode = ''
  form.deptName = ''
  form.deptType = undefined
  form.location = ''
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: Department) {
  editingId.value = row.id
  form.deptCode = row.deptCode
  form.deptName = row.deptName
  form.deptType = row.deptType
  form.location = row.location ?? ''
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateDepartment(editingId.value, {
        deptCode: form.deptCode,
        deptName: form.deptName,
        deptType: form.deptType as number,
        location: form.location || undefined,
        status: form.status,
      })
      ElMessage.success('修改成功')
    } else {
      await createDepartment({
        orgId: 1, // 阶段一固定单机构
        deptCode: form.deptCode,
        deptName: form.deptName,
        deptType: form.deptType as number,
        location: form.location || undefined,
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

// ---------------- 启用 / 停用 ----------------
async function handleToggleStatus(row: Department) {
  const target = row.status === 1 ? 0 : 1
  try {
    await updateDepartment(row.id, {
      deptCode: row.deptCode,
      deptName: row.deptName,
      deptType: row.deptType,
      location: row.location ?? undefined,
      status: target,
    })
    row.status = target
    ElMessage.success(target === 1 ? '已启用' : '已停用')
  } catch {
    // 失败保持原状态
  }
}

onMounted(fetchList)
</script>
