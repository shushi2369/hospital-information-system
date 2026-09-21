<template>
  <div class="page-card">
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="姓名">
        <el-input v-model="query.name" placeholder="姓名" style="width: 140px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="1">在职</el-radio-button>
          <el-radio-button :value="0">离职</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button v-perm="'hr:staff:manage'" type="success" @click="openForm()">新建档案</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="staffNo" label="员工号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="deptId" label="科室ID" width="90" align="center" />
      <el-table-column prop="title" label="职称" width="110" />
      <el-table-column prop="phone" label="电话" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="entryDate" label="入职日期" width="110" align="center" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '在职' : '离职' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'hr:staff:manage'" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-if="row.status === 1" v-perm="'hr:staff:manage'" link type="warning" @click="openTitle(row)">职称变更</el-button>
          <el-button v-if="row.status === 1" v-perm="'hr:staff:manage'" link type="danger" @click="handleExit(row)">离职</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" background
        layout="total, sizes, prev, pager, next" @current-change="fetchList" />
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑档案' : '新建档案'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="科室ID" required><el-input-number v-model="form.deptId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="职称" required><el-input v-model="form.title" placeholder="如 主治医师" /></el-form-item>
        <el-form-item label="执业证号"><el-input v-model="form.licenseNo" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="入职日期" required><el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="titleVisible" title="职称变更" width="420px" destroy-on-close>
      <el-form :model="titleForm" label-width="90px">
        <el-form-item label="新职称" required><el-input v-model="titleForm.newTitle" /></el-form-item>
        <el-form-item label="生效日期" required><el-date-picker v-model="titleForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="titleForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="titleVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTitle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { createStaff, exitStaff, getStaffPage, titleChange, updateStaff, type HrStaff } from '@/api/hr'

const loading = ref(false)
const list = ref<HrStaff[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, name: '', status: undefined as number | undefined })

async function fetchList() {
  loading.value = true
  try {
    const res = await getStaffPage({ pageNum: query.pageNum, pageSize: query.pageSize, name: query.name || undefined, status: query.status })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { query.pageNum = 1; fetchList() }

const formVisible = ref(false)
const form = reactive<{ id: number; name: string; deptId?: number; title: string; licenseNo: string; phone: string; entryDate: string }>(
  { id: 0, name: '', deptId: undefined, title: '', licenseNo: '', phone: '', entryDate: '' })

function openForm(row?: HrStaff) {
  form.id = row?.id ?? 0
  form.name = row?.name ?? ''
  form.deptId = row?.deptId
  form.title = row?.title ?? ''
  form.licenseNo = row?.licenseNo ?? ''
  form.phone = row?.phone ?? ''
  form.entryDate = row?.entryDate ?? ''
  formVisible.value = true
}

async function handleSave() {
  if (!form.name || !form.deptId || !form.title || !form.entryDate) {
    ElMessage.warning('请完整填写档案信息')
    return
  }
  const data = { name: form.name, deptId: form.deptId, title: form.title, licenseNo: form.licenseNo || undefined, phone: form.phone || undefined, entryDate: form.entryDate }
  if (form.id) {
    await updateStaff(form.id, data)
    ElMessage.success('档案已更新')
  } else {
    await createStaff(data)
    ElMessage.success('档案已创建')
  }
  formVisible.value = false
  fetchList()
}

async function handleExit(row: HrStaff) {
  try {
    await ElMessageBox.confirm(`确认登记 ${row.name} 离职？`, '离职登记')
    await exitStaff(row.id)
    ElMessage.success('已登记离职')
    fetchList()
  } catch { /* 取消 */ }
}

const titleVisible = ref(false)
const titleForm = reactive<{ staffId: number; newTitle: string; effectiveDate: string; note: string }>({ staffId: 0, newTitle: '', effectiveDate: '', note: '' })

function openTitle(row: HrStaff) {
  titleForm.staffId = row.id
  titleForm.newTitle = ''
  titleForm.effectiveDate = ''
  titleForm.note = ''
  titleVisible.value = true
}

async function handleTitle() {
  if (!titleForm.newTitle || !titleForm.effectiveDate) {
    ElMessage.warning('请填写新职称与生效日期')
    return
  }
  await titleChange(titleForm.staffId, { newTitle: titleForm.newTitle, effectiveDate: titleForm.effectiveDate, note: titleForm.note || undefined })
  ElMessage.success('职称已变更')
  titleVisible.value = false
  fetchList()
}

onMounted(fetchList)
</script>
