<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="所属科室">
        <el-select v-model="query.deptId" placeholder="全部科室" clearable filterable style="width: 180px">
          <el-option v-for="dept in deptOptions" :key="dept.id" :label="dept.deptName" :value="dept.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="号别">
        <el-select v-model="query.isExpert" placeholder="全部" clearable style="width: 120px">
          <el-option label="普通号" :value="0" />
          <el-option label="专家号" :value="1" />
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
      <span class="toolbar-title">医生列表</span>
      <el-button v-perm="'basedata:doctor:manage'" type="primary" :icon="Plus" @click="openCreate">
        新增医生
      </el-button>
    </div>

    <!-- 列表（接口非分页，客户端分页展示） -->
    <el-table v-loading="loading" :data="pagedList" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="doctorCode" label="工号" min-width="100" />
      <el-table-column prop="doctorName" label="姓名" min-width="100" />
      <el-table-column label="所属科室" min-width="120">
        <template #default="{ row }">{{ deptName(row.deptId) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="职称" min-width="110" />
      <el-table-column label="号别" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isExpert === 1 ? 'warning' : 'info'" size="small">
            {{ row.isExpert === 1 ? '专家号' : '普通号' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="normalFee" label="普通号费(元)" min-width="115" align="right" />
      <el-table-column prop="expertFee" label="专家号费(元)" min-width="115" align="right" />
      <el-table-column prop="dailyQuota" label="每日限挂" min-width="95" align="right" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-perm="'basedata:doctor:manage'"
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
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'basedata:doctor:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
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
      :title="editingId ? '编辑医生' : '新增医生'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="绑定账号" prop="userId">
          <el-select
            v-model="form.userId"
            :disabled="!!editingId"
            filterable
            placeholder="选择用户登录账号（唯一绑定）"
            style="width: 100%"
            @change="handleUserChange"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.username}（${user.realName}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工号" prop="doctorCode">
          <el-input v-model="form.doctorCode" placeholder="字母/数字，如 D0004" maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名" prop="doctorName">
          <el-input v-model="form.doctorName" maxlength="32" placeholder="请输入医生姓名" />
        </el-form-item>
        <el-form-item label="所属科室" prop="deptId">
          <el-select v-model="form.deptId" filterable placeholder="请选择科室" style="width: 100%">
            <el-option v-for="dept in deptOptions" :key="dept.id" :label="dept.deptName" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职称" prop="title">
          <el-select
            v-model="form.title"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入职称"
            style="width: 100%"
          >
            <el-option v-for="t in TITLE_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="号别" prop="isExpert">
          <el-radio-group v-model="form.isExpert">
            <el-radio :value="0">普通号</el-radio>
            <el-radio :value="1">专家号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="普通号费" prop="normalFee">
          <el-input-number
            v-model="form.normalFee"
            :min="0.01"
            :max="99999"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-unit">元</span>
        </el-form-item>
        <el-form-item label="专家号费" prop="expertFee">
          <el-input-number
            v-model="form.expertFee"
            :min="0.01"
            :max="99999"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-unit">元</span>
        </el-form-item>
        <el-form-item label="每日限挂数" prop="dailyQuota">
          <el-input-number
            v-model="form.dailyQuota"
            :min="1"
            :max="999"
            :precision="0"
            :step="5"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-unit">上午/下午各限额</span>
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
  createDoctor,
  getDepartmentList,
  getDoctorList,
  updateDoctor,
  type Department,
  type Doctor,
} from '@/api/basedata'
import { getUserPage, type SystemUser } from '@/api/system'
import { toList } from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPerm('basedata:doctor:manage'))

const TITLE_OPTIONS = ['主任医师', '副主任医师', '主治医师', '住院医师']

// ---------------- 列表 ----------------
const loading = ref(false)
const allList = ref<Doctor[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const deptOptions = ref<Department[]>([])
const userOptions = ref<SystemUser[]>([])

const query = reactive({
  deptId: undefined as number | undefined,
  isExpert: undefined as number | undefined,
  status: undefined as number | undefined,
})

const total = computed(() => allList.value.length)
const pagedList = computed(() =>
  allList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value)
)

function deptName(deptId: number) {
  return deptOptions.value.find((d) => d.id === deptId)?.deptName ?? String(deptId)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getDoctorList({
      deptId: query.deptId,
      isExpert: query.isExpert,
      status: query.status,
    })
    allList.value = toList<Doctor>(res)
  } finally {
    loading.value = false
  }
}

async function fetchDeptOptions() {
  try {
    // 下拉共用科室列表，仅取启用科室
    deptOptions.value = toList<Department>(await getDepartmentList({ status: 1 }))
  } catch {
    deptOptions.value = []
  }
}

/** 绑定账号下拉需要用户列表（医生维护者通常具有 sys:user:query 权限，失败时兜底） */
async function fetchUserOptions() {
  try {
    const res = await getUserPage({ pageNum: 1, pageSize: 200 })
    userOptions.value = res.list ?? []
  } catch {
    userOptions.value = []
  }
}

function handleReset() {
  query.deptId = undefined
  query.isExpert = undefined
  query.status = undefined
  fetchList()
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  userId: undefined as number | undefined,
  doctorCode: '',
  doctorName: '',
  deptId: undefined as number | undefined,
  title: '',
  isExpert: 0,
  normalFee: 10,
  expertFee: 20,
  dailyQuota: 40,
  status: 1,
})

const formRules: FormRules = {
  userId: [{ required: true, message: '请选择绑定的登录账号', trigger: 'change' }],
  doctorCode: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{1,32}$/, message: '工号仅允许字母与数字', trigger: 'blur' },
  ],
  doctorName: [{ required: true, message: '请输入医生姓名', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属科室', trigger: 'change' }],
  title: [{ required: true, message: '请选择或输入职称', trigger: 'change' }],
  normalFee: [{ required: true, message: '请输入普通号费', trigger: 'blur' }],
  expertFee: [{ required: true, message: '请输入专家号费', trigger: 'blur' }],
  dailyQuota: [{ required: true, message: '请输入每日限挂数', trigger: 'blur' }],
}

/** 选中绑定账号后自动回填医生姓名（可修改） */
function handleUserChange(userId: number) {
  const user = userOptions.value.find((u) => u.id === userId)
  if (user && !editingId.value) {
    form.doctorName = user.realName
  }
}

/** 编辑模式下若账号列表拉取失败，用当前行数据兜底展示 */
function ensureUserOption(row: Doctor) {
  if (!userOptions.value.some((u) => u.id === row.userId)) {
    userOptions.value = [
      { id: row.userId, username: `用户${row.userId}`, realName: row.doctorName },
      ...userOptions.value,
    ]
  }
}

function openCreate() {
  editingId.value = null
  form.userId = undefined
  form.doctorCode = ''
  form.doctorName = ''
  form.deptId = undefined
  form.title = ''
  form.isExpert = 0
  form.normalFee = 10
  form.expertFee = 20
  form.dailyQuota = 40
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: Doctor) {
  editingId.value = row.id
  ensureUserOption(row)
  form.userId = row.userId
  form.doctorCode = row.doctorCode
  form.doctorName = row.doctorName
  form.deptId = row.deptId
  form.title = row.title
  form.isExpert = row.isExpert
  // 金额为字符串，编辑时转数值
  form.normalFee = Number(row.normalFee)
  form.expertFee = Number(row.expertFee)
  form.dailyQuota = row.dailyQuota
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateDoctor(editingId.value, {
        deptId: form.deptId as number,
        doctorCode: form.doctorCode,
        doctorName: form.doctorName,
        title: form.title,
        isExpert: form.isExpert,
        normalFee: Number(form.normalFee),
        expertFee: Number(form.expertFee),
        dailyQuota: form.dailyQuota,
        status: form.status,
      })
      ElMessage.success('修改成功')
    } else {
      await createDoctor({
        userId: form.userId as number,
        deptId: form.deptId as number,
        doctorCode: form.doctorCode,
        doctorName: form.doctorName,
        title: form.title,
        isExpert: form.isExpert,
        normalFee: Number(form.normalFee),
        expertFee: Number(form.expertFee),
        dailyQuota: form.dailyQuota,
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
async function handleToggleStatus(row: Doctor) {
  const target = row.status === 1 ? 0 : 1
  try {
    await updateDoctor(row.id, {
      deptId: row.deptId,
      doctorCode: row.doctorCode,
      doctorName: row.doctorName,
      title: row.title,
      isExpert: row.isExpert,
      normalFee: Number(row.normalFee),
      expertFee: Number(row.expertFee),
      dailyQuota: row.dailyQuota,
      status: target,
    })
    row.status = target
    ElMessage.success(target === 1 ? '已启用' : '已停用')
  } catch {
    // 失败保持原状态
  }
}

onMounted(() => {
  fetchDeptOptions()
  fetchUserOptions()
  fetchList()
})
</script>

<style scoped>
.form-unit {
  margin-left: 8px;
  color: #909399;
}
</style>
