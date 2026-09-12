<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="姓名">
        <el-input
          v-model="query.name"
          placeholder="请输入姓名"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input
          v-model="query.phone"
          placeholder="请输入手机号"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="建档号">
        <el-input
          v-model="query.patientNo"
          placeholder="请输入建档号"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="身份证号">
        <el-input
          v-model="query.idCardNo"
          placeholder="请输入身份证号"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">患者档案</span>
      <el-button v-perm="'patient:archive:create'" type="primary" :icon="Plus" @click="openCreate">
        新建档案
      </el-button>
    </div>

    <!-- 列表（手机号/身份证号后端返回脱敏值） -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="patientNo" label="建档号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="name" label="姓名" min-width="90" />
      <el-table-column label="性别" width="70" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.gender === 1 ? 'primary' : 'danger'">
            {{ genderLabel(row.gender) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="birthDate" label="出生日期" width="110" align="center" />
      <el-table-column prop="phone" label="联系电话" min-width="120">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="idCardNo" label="身份证号" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ row.idCardNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="address" label="家庭住址" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.address || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="建档时间" width="170">
        <template #default="{ row }">{{ row.createdAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'patient:archive:query'" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button v-perm="'patient:archive:update'" link type="warning" @click="handleIssueCard(row)">
            补办就诊卡
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

    <!-- 建档弹窗 -->
    <el-dialog v-model="dialogVisible" title="新建患者档案" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入患者姓名" maxlength="32" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker
            v-model="form.birthDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择出生日期"
            :disabled-date="disableFutureDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCardNo">
          <el-input v-model="form.idCardNo" placeholder="18 位身份证号码" maxlength="18" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="家庭住址" prop="address">
          <el-input v-model="form.address" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="过敏史" prop="allergyHistory">
          <el-input
            v-model="form.allergyHistory"
            type="textarea"
            :rows="2"
            maxlength="256"
            placeholder="药物/食物过敏情况，无则留空"
          />
        </el-form-item>
        <el-form-item label="既往史" prop="pastHistory">
          <el-input
            v-model="form.pastHistory"
            type="textarea"
            :rows="2"
            maxlength="256"
            placeholder="既往病史/手术史等，无则留空"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 档案详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="患者档案详情" size="460px" destroy-on-close>
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="建档号">{{ archive?.patientNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ archive?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderLabel(archive?.gender) }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ archive?.birthDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ ageText }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ archive?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ archive?.idCardNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="家庭住址">{{ archive?.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过敏史">{{ archive?.allergyHistory || '无' }}</el-descriptions-item>
        <el-descriptions-item label="既往史">{{ archive?.pastHistory || '无' }}</el-descriptions-item>
        <el-descriptions-item label="建档时间">{{ archive?.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  calcAge,
  createPatient,
  genderLabel,
  getPatientDetail,
  getPatientPage,
  issuePatientCard,
  type Patient,
} from '@/api/patient'

// 18 位身份证号（含校验位 X/x）
const ID_CARD_PATTERN = /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]$/

function disableFutureDate(date: Date) {
  return date.getTime() > Date.now()
}

// ---------------- 列表（服务端分页） ----------------
const loading = ref(false)
const list = ref<Patient[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  phone: '',
  patientNo: '',
  idCardNo: '',
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getPatientPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      name: query.name || undefined,
      phone: query.phone || undefined,
      patientNo: query.patientNo || undefined,
      idCardNo: query.idCardNo || undefined,
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.name = ''
  query.phone = ''
  query.patientNo = ''
  query.idCardNo = ''
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 建档 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  gender: 1,
  birthDate: '',
  idCardNo: '',
  phone: '',
  address: '',
  allergyHistory: '',
  pastHistory: '',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }],
  idCardNo: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: ID_CARD_PATTERN, message: '身份证号须为 18 位有效格式', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

function openCreate() {
  form.name = ''
  form.gender = 1
  form.birthDate = ''
  form.idCardNo = ''
  form.phone = ''
  form.address = ''
  form.allergyHistory = ''
  form.pastHistory = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const patientNo = await createPatient({
      name: form.name,
      gender: form.gender,
      birthDate: form.birthDate,
      idCardNo: form.idCardNo,
      phone: form.phone,
      address: form.address || undefined,
      allergyHistory: form.allergyHistory || undefined,
      pastHistory: form.pastHistory || undefined,
    })
    dialogVisible.value = false
    ElMessage.success('建档成功')
    if (patientNo) {
      ElMessageBox.alert(`建档号：${patientNo}`, '建档成功', {
        type: 'success',
        confirmButtonText: '知道了',
      }).catch(() => {})
    }
    fetchList()
  } catch {
    // B1001 该身份证已建档等错误已在拦截器中统一提示
  } finally {
    submitting.value = false
  }
}

// ---------------- 档案详情 ----------------
const drawerVisible = ref(false)
const detailLoading = ref(false)
const archive = ref<Patient | null>(null)

const ageText = computed(() => {
  const age = calcAge(archive.value?.birthDate)
  return age === null ? '-' : `${age} 岁`
})

async function openDetail(row: Patient) {
  drawerVisible.value = true
  detailLoading.value = true
  archive.value = null
  try {
    archive.value = await getPatientDetail(row.id)
  } catch {
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// ---------------- 补办就诊卡 ----------------
function handleIssueCard(row: Patient) {
  ElMessageBox.confirm(`确认为患者「${row.name}」补办就诊卡？补办后原卡作废。`, '补办就诊卡', {
    type: 'warning',
    confirmButtonText: '确认补办',
    cancelButtonText: '取消',
  })
    .then(async () => {
      const cardNo = await issuePatientCard(row.id)
      ElMessage.success('补办成功')
      if (cardNo) {
        ElMessageBox.alert(`新就诊卡号：${cardNo}`, '补办成功', {
          type: 'success',
          confirmButtonText: '知道了',
        }).catch(() => {})
      }
      fetchList()
    })
    .catch(() => {
      // 取消或失败（拦截器已提示）
    })
}

onMounted(fetchList)
</script>
