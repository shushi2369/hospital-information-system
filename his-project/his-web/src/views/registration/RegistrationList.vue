<template>
  <div class="registration-page">
    <!-- 上：现场挂号 -->
    <div class="page-card">
      <div class="table-toolbar">
        <span class="toolbar-title">现场挂号</span>
      </div>
      <el-form :model="regForm" inline label-width="80px">
        <el-form-item label="患者" required>
          <el-select
            v-model="regForm.patientId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchPatients"
            :loading="patientSearching"
            placeholder="输入姓名或手机号搜索患者"
            style="width: 230px"
          >
            <el-option
              v-for="p in patientOptions"
              :key="p.id"
              :label="`${p.name}（${p.phone || '无手机号'}）`"
              :value="p.id"
            >
              <div class="patient-option">
                <span>{{ p.name }}</span>
                <span class="option-sub">{{ p.phone || '-' }}｜{{ p.patientNo }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="科室" required>
          <el-select
            v-model="regForm.deptId"
            placeholder="临床科室"
            style="width: 150px"
            @change="handleDeptChange"
          >
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生" required>
          <el-select
            v-model="regForm.doctorId"
            :disabled="!regForm.deptId"
            placeholder="请先选择科室"
            style="width: 190px"
          >
            <el-option
              v-for="d in doctorOptions"
              :key="d.id"
              :label="`${d.doctorName}｜${d.title}${d.isExpert === 1 ? '｜专家' : ''}`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="就诊日期" required>
          <el-date-picker
            v-model="regForm.regDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :disabled-date="disablePastDate"
            :clearable="false"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="时段" required>
          <el-radio-group v-model="regForm.period">
            <el-radio v-for="opt in PERIOD_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="号别" required>
          <el-radio-group v-model="regForm.regType" @change="() => (regForm.doctorId = undefined)">
            <el-radio v-for="opt in REG_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button
            v-perm="'reg:ticket:create'"
            type="primary"
            :icon="Promotion"
            :loading="registering"
            @click="handleRegister"
          >
            挂号
          </el-button>
        </el-form-item>
      </el-form>
      <div class="reg-tips">
        <span v-if="selectedPatient">
          已选患者：{{ selectedPatient.name }}｜{{ genderLabel(selectedPatient.gender) }}｜{{
            selectedPatient.birthDate
          }}｜建档号 {{ selectedPatient.patientNo }}
        </span>
        <span v-if="feePreview" class="fee-preview">{{ feePreview }}</span>
      </div>
    </div>

    <!-- 下：挂号记录 -->
    <div class="page-card">
      <!-- 搜索栏 -->
      <el-form class="search-bar" :model="query" inline>
        <el-form-item label="就诊日期">
          <el-date-picker
            v-model="query.regDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="全部日期"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="患者ID">
          <el-input
            v-model="query.patientId"
            placeholder="患者ID"
            clearable
            style="width: 120px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="医生">
          <el-select
            v-model="query.doctorId"
            placeholder="全部医生"
            clearable
            filterable
            style="width: 170px"
          >
            <el-option
              v-for="d in allDoctors"
              :key="d.id"
              :label="`${d.doctorName}（${deptNameOf(d.deptId)}）`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="opt in REG_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <span class="toolbar-title">挂号记录</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="regDate" label="就诊日期" width="110" align="center" />
        <el-table-column prop="regNo" label="挂号单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" min-width="90" />
        <el-table-column prop="patientNo" label="建档号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="deptName" label="科室" min-width="110" />
        <el-table-column prop="doctorName" label="医生" min-width="90" />
        <el-table-column label="时段" width="75" align="center">
          <template #default="{ row }">{{ periodLabel(row.period) }}</template>
        </el-table-column>
        <el-table-column label="号别" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.regType === 2 ? 'warning' : 'info'">
              {{ regTypeLabel(row.regType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="queueNo" label="排队号" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="regStatusTagType(row.status)">
              {{ regStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收费状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="chargeStatusTagType(row.chargeStatus)">
              {{ chargeStatusLabel(row.chargeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-perm="'reg:ticket:cancel'"
              v-if="row.status === 10"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              退号
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Refresh, Search } from '@element-plus/icons-vue'
import { genderLabel, getPatientPage, type Patient } from '@/api/patient'
import { getDepartmentListCached, getDoctorListCached, type Department, type Doctor } from '@/api/basedata'
import {
  cancelRegistration,
  chargeStatusLabel,
  chargeStatusTagType,
  createRegistration,
  fmtMoney,
  getRegistrationPage,
  periodLabel,
  PERIOD_OPTIONS,
  REG_STATUS_OPTIONS,
  REG_TYPE_OPTIONS,
  regStatusTagType,
  regStatusLabel,
  regTypeLabel,
  todayStr,
  type Registration,
} from '@/api/registration'
import { toList } from '@/api/request'

const startOfToday = new Date(new Date().setHours(0, 0, 0, 0)).getTime()
const disablePastDate = (date: Date) => date.getTime() < startOfToday

// ---------------- 现场挂号 ----------------
const deptOptions = ref<Department[]>([])
const allDoctors = ref<Doctor[]>([])
const deptDoctors = ref<Doctor[]>([])
const patientOptions = ref<Patient[]>([])
const patientSearching = ref(false)
const registering = ref(false)

const regForm = reactive({
  patientId: undefined as number | undefined,
  deptId: undefined as number | undefined,
  doctorId: undefined as number | undefined,
  regDate: todayStr(),
  period: 1,
  regType: 1,
})

/** 专家号仅展示 is_expert=1 的医生 */
const doctorOptions = computed(() =>
  regForm.regType === 2 ? deptDoctors.value.filter((d) => d.isExpert === 1) : deptDoctors.value
)

const selectedPatient = computed(() =>
  patientOptions.value.find((p) => p.id === regForm.patientId)
)
const selectedDoctor = computed(() => deptDoctors.value.find((d) => d.id === regForm.doctorId))

const feePreview = computed(() => {
  const d = selectedDoctor.value
  if (!d) return ''
  const fee = Number(regForm.regType === 2 ? d.expertFee : d.normalFee)
  return Number.isFinite(fee) ? `预计费用：¥${fee.toFixed(2)}` : ''
})

/** 远程搜索患者：纯数字按手机号查，否则按姓名查 */
async function searchPatients(keyword: string) {
  const key = (keyword || '').trim()
  if (!key) {
    patientOptions.value = []
    return
  }
  patientSearching.value = true
  try {
    const isPhone = /^\d{6,11}$/.test(key)
    const res = await getPatientPage({
      pageNum: 1,
      pageSize: 20,
      name: isPhone ? undefined : key,
      phone: isPhone ? key : undefined,
    })
    patientOptions.value = res.list ?? []
  } catch {
    patientOptions.value = []
  } finally {
    patientSearching.value = false
  }
}

async function fetchDeptOptions() {
  try {
    deptOptions.value = toList<Department>(await getDepartmentListCached({ deptType: 1, status: 1 }))
  } catch {
    deptOptions.value = []
  }
}

/** 记录筛选用的全量医生下拉 */
async function fetchAllDoctors() {
  try {
    allDoctors.value = toList<Doctor>(await getDoctorListCached({ status: 1 }))
  } catch {
    allDoctors.value = []
  }
}

/** 科室联动医生下拉 */
async function handleDeptChange(deptId?: number) {
  regForm.doctorId = undefined
  deptDoctors.value = []
  if (!deptId) return
  try {
    deptDoctors.value = toList<Doctor>(await getDoctorListCached({ deptId, status: 1 }))
  } catch {
    // 拦截器已提示
  }
}

function deptNameOf(deptId: number): string {
  return deptOptions.value.find((d) => d.id === deptId)?.deptName ?? ''
}

async function handleRegister() {
  const patientId = regForm.patientId
  const deptId = regForm.deptId
  const doctorId = regForm.doctorId
  const regDate = regForm.regDate
  if (!patientId) {
    ElMessage.warning('请先搜索并选择患者')
    return
  }
  if (!deptId) {
    ElMessage.warning('请选择科室')
    return
  }
  if (!doctorId) {
    ElMessage.warning('请选择医生')
    return
  }
  if (!regDate) {
    ElMessage.warning('请选择就诊日期')
    return
  }
  registering.value = true
  try {
    const res = await createRegistration({
      patientId,
      doctorId,
      regDate,
      period: regForm.period,
      regType: regForm.regType,
    })
    ElMessageBox.alert(
      h('div', null, [
        h('p', null, `挂号单号：${res.regNo}`),
        h('p', null, `排队号：${res.queueNo}`),
        h('p', null, `挂号费：¥${fmtMoney(res.regFee)}　诊查费：¥${fmtMoney(res.consultationFee)}`),
        h('p', null, `费用合计：¥${fmtMoney(res.totalFee)}`),
      ]),
      '挂号成功',
      { type: 'success', confirmButtonText: '知道了' }
    ).catch(() => {})
    // 重置患者与医生便于连续挂号
    regForm.patientId = undefined
    regForm.doctorId = undefined
    fetchList()
  } catch {
    // B1002 重复挂号 / B1003 号源已满等已在拦截器中统一提示
  } finally {
    registering.value = false
  }
}

// ---------------- 挂号记录 ----------------
const loading = ref(false)
const list = ref<Registration[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  regDate: '',
  patientId: '',
  doctorId: undefined as number | undefined,
  status: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const pid = query.patientId ? Number(query.patientId) : NaN
    const res = await getRegistrationPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      regDate: query.regDate || undefined,
      patientId: Number.isFinite(pid) && pid > 0 ? pid : undefined,
      doctorId: query.doctorId,
      status: query.status,
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
  query.regDate = ''
  query.patientId = ''
  query.doctorId = undefined
  query.status = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 退号 ----------------
function handleCancel(row: Registration) {
  ElMessageBox.confirm(
    `确认为患者「${row.patientName}」退号（单号 ${row.regNo}）？退号不自动退费，已收费请到退费界面办理。`,
    '退号确认',
    { type: 'warning', confirmButtonText: '确认退号', cancelButtonText: '取消' }
  )
    .then(async () => {
      await cancelRegistration(row.id)
      ElMessage.success('退号成功')
      fetchList()
    })
    .catch(() => {
      // 取消或失败（拦截器已提示 B1004 等）
    })
}

onMounted(() => {
  fetchDeptOptions()
  fetchAllDoctors()
  fetchList()
})
</script>

<style scoped>
.registration-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.patient-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.option-sub {
  color: #909399;
  font-size: 12px;
}

.reg-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #606266;
  font-size: 13px;
}

.fee-preview {
  color: #e6a23c;
  font-weight: 600;
}
</style>
