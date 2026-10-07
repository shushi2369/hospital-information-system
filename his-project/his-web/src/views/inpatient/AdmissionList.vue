<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="住院号">
        <el-input
          v-model="query.admissionNo"
          placeholder="请输入住院号"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="o in INP_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">住院记录</span>
      <el-button v-perm="'inp:admission:create'" type="primary" :icon="Plus" @click="openAdmissionDialog">
        入院登记
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="admissionNo" label="住院号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="patientName" label="患者" min-width="85" show-overflow-tooltip />
      <el-table-column prop="patientNo" label="建档号" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.patientNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="deptName" label="科室" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deptName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="wardName" label="病区" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.wardName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="bedNo" label="床位" width="70" align="center">
        <template #default="{ row }">{{ row.bedNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="doctorName" label="主治医生" min-width="85" show-overflow-tooltip>
        <template #default="{ row }">{{ row.doctorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="入院类型" width="90" align="center">
        <template #default="{ row }">{{ admissionTypeLabel(row.admissionType) }}</template>
      </el-table-column>
      <el-table-column prop="admissionTime" label="入院时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.admissionTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="累计押金" width="100" align="right">
        <template #default="{ row }">¥{{ fmtMoney(row.depositTotal) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="95" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="inpStatusTagType(row.status)">
            {{ inpStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 10"
            v-perm="'inp:deposit:create'"
            link
            type="success"
            @click="openDepositDialog(row)"
          >
            补押金
          </el-button>
          <el-button
            v-if="row.status === 10"
            v-perm="'inp:transfer:create'"
            link
            type="warning"
            @click="openTransferDialog(row)"
          >
            转科
          </el-button>
          <el-button
            v-if="row.status === 10"
            v-perm="'inp:discharge:create'"
            link
            type="danger"
            @click="openDischargeDialog(row)"
          >
            出院
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

    <!-- 入院登记弹窗 -->
    <el-dialog v-model="admDialogVisible" title="入院登记" width="640px" destroy-on-close>
      <el-form ref="admFormRef" :model="admForm" :rules="admRules" label-width="90px">
        <el-form-item label="患者" prop="patientId">
          <el-select
            v-model="admForm.patientId"
            filterable
            remote
            :remote-method="searchPatients"
            :loading="patientSearching"
            placeholder="输入姓名/建档号搜索患者"
            style="width: 100%"
          >
            <el-option
              v-for="p in patientOptions"
              :key="p.id"
              :label="`${p.name}（${p.patientNo}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="入院科室" prop="deptId">
          <el-select
            v-model="admForm.deptId"
            placeholder="选择临床科室"
            style="width: 100%"
            @change="handleDeptChange"
          >
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="病区" prop="wardId">
          <el-select
            v-model="admForm.wardId"
            placeholder="先选科室，再选病区"
            style="width: 100%"
            :disabled="!admForm.deptId"
            @change="handleWardChange"
          >
            <el-option v-for="w in wardOptions" :key="w.id" :label="w.wardName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="床位" prop="bedId">
          <el-select
            v-model="admForm.bedId"
            placeholder="仅显示空闲床位"
            style="width: 100%"
            :disabled="!admForm.wardId"
            :loading="bedLoading"
          >
            <el-option
              v-for="b in bedOptions"
              :key="b.id"
              :label="`${b.bedNo}${b.bedFee ? `（¥${fmtMoney(b.bedFee)}）` : ''}`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主治医生" prop="doctorId">
          <el-select
            v-model="admForm.doctorId"
            filterable
            placeholder="选择医生"
            style="width: 100%"
            :loading="doctorLoading"
          >
            <el-option v-for="d in doctorOptions" :key="d.id" :label="d.doctorName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入院类型" prop="admissionType">
          <el-radio-group v-model="admForm.admissionType">
            <el-radio v-for="o in ADMISSION_TYPE_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="入院诊断" prop="plannedDiagnosis">
          <el-input
            v-model="admForm.plannedDiagnosis"
            placeholder="拟诊/入院诊断"
            maxlength="128"
          />
        </el-form-item>
        <el-form-item label="首笔押金" prop="depositAmount">
          <el-input-number
            v-model="admForm.depositAmount"
            :min="0.01"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 160px"
          />
          <el-radio-group v-model="admForm.payMethod" class="deposit-pay">
            <el-radio-button v-for="o in PAY_METHOD_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="admDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="admSubmitting" @click="handleAdmissionSubmit">
          确认登记
        </el-button>
      </template>
    </el-dialog>

    <!-- 补押金弹窗 -->
    <el-dialog v-model="depDialogVisible" title="补缴押金" width="440px" destroy-on-close>
      <div v-if="currentRow" class="dep-line">
        住院号：{{ currentRow.admissionNo }}｜患者：{{ currentRow.patientName }}｜累计押金：¥{{ fmtMoney(currentRow.depositTotal) }}
      </div>
      <el-form ref="depFormRef" :model="depForm" :rules="depRules" label-width="90px">
        <el-form-item label="补缴金额" prop="amount">
          <el-input-number
            v-model="depForm.amount"
            :min="0.01"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="支付方式" prop="payMethod">
          <el-radio-group v-model="depForm.payMethod">
            <el-radio-button v-for="o in PAY_METHOD_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="depDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="depSubmitting" @click="handleDepositSubmit">
          确认补缴
        </el-button>
      </template>
    </el-dialog>

    <!-- 转科弹窗 -->
    <el-dialog v-model="trDialogVisible" title="转科（转床）" width="520px" destroy-on-close>
      <div v-if="currentRow" class="dep-line">
        住院号：{{ currentRow.admissionNo }}｜患者：{{ currentRow.patientName }}｜当前：{{ currentRow.wardName || '-' }} {{ currentRow.bedNo || '' }}
      </div>
      <el-form ref="trFormRef" :model="trForm" :rules="trRules" label-width="90px">
        <el-form-item label="目标病区" prop="toWardId">
          <el-select
            v-model="trForm.toWardId"
            placeholder="选择目标病区"
            style="width: 100%"
            @change="handleTargetWardChange"
          >
            <el-option v-for="w in wardAllOptions" :key="w.id" :label="w.wardName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标床位" prop="toBedId">
          <el-select
            v-model="trForm.toBedId"
            placeholder="仅显示目标病区空闲床位"
            style="width: 100%"
            :disabled="!trForm.toWardId"
            :loading="trBedLoading"
          >
            <el-option v-for="b in trBedOptions" :key="b.id" :label="b.bedNo" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="转科原因" prop="reason">
          <el-input
            v-model="trForm.reason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请输入转科原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="trDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="trSubmitting" @click="handleTransferSubmit">
          确认转科
        </el-button>
      </template>
    </el-dialog>

    <!-- 出院申请弹窗 -->
    <el-dialog v-model="dcDialogVisible" title="出院申请" width="520px" destroy-on-close>
      <div v-if="currentRow" class="dep-line">
        住院号：{{ currentRow.admissionNo }}｜患者：{{ currentRow.patientName }}｜累计押金：¥{{ fmtMoney(currentRow.depositTotal) }}
      </div>
      <div v-if="currentRow" class="dep-line">
        <!-- 一百零八轮 D5：出院前费用合计回显（未结一日清费用），让"去结算"有预期 -->
        费用合计：<span class="dep-amount">¥{{ fmtMoney(dcFeeTotal) }}</span>
        <span v-if="dcFeeLoading" class="form-tip">（计算中…）</span>
        <span v-else class="form-tip">（未结一日清费用，实际以出院结算为准）</span>
      </div>
      <el-form ref="dcFormRef" :model="dcForm" :rules="dcRules" label-width="90px">
        <el-form-item label="出院方式" prop="dischargeWay">
          <el-select v-model="dcForm.dischargeWay" placeholder="选择出院方式" style="width: 100%">
            <el-option v-for="o in DISCHARGE_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="出院诊断" prop="dischargeDiagnosis">
          <el-input
            v-model="dcForm.dischargeDiagnosis"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请输入出院诊断（必填）"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="出院后住院状态变为「出院未结」，请到出院结算页办理费用结算。"
      />
      <template #footer>
        <el-button @click="dcDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dcSubmitting" @click="handleDischargeSubmit">
          确认出院
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="住院详情" size="760px" destroy-on-close>
      <div v-loading="drawerLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="住院号">{{ detail.admissionNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="inpStatusTagType(detail.status)">
                {{ inpStatusLabel(detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="患者">{{ detail.patientName }}（{{ detail.patientNo || '-' }}）</el-descriptions-item>
            <el-descriptions-item label="入院类型">{{ admissionTypeLabel(detail.admissionType) }}</el-descriptions-item>
            <el-descriptions-item label="科室">{{ detail.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主治医生">{{ detail.doctorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="病区">{{ detail.wardName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="床位">{{ detail.bedNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入院时间">{{ detail.admissionTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="累计押金">
              <span class="dep-amount">¥{{ fmtMoney(detail.depositTotal) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="入院诊断" :span="2">
              {{ detail.plannedDiagnosis || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="出院方式">{{ detail.dischargeWay ? dischargeWayLabel(detail.dischargeWay) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="出院时间">{{ detail.dischargeTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出院诊断" :span="2">
              {{ detail.dischargeDiagnosis || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="drawer-section">费用一日清</div>
          <el-empty
            v-if="feeGroups.length === 0"
            description="暂无费用记录"
            :image-size="70"
          />
          <div v-for="g in feeGroups" :key="g.feeDate" class="fee-group">
            <div class="fee-group-head">
              <span>{{ g.feeDate }}</span>
              <span class="fee-group-total">日合计：¥{{ fmtMoney(g.totalAmount) }}</span>
            </div>
            <el-table :data="g.items" border size="small">
              <el-table-column prop="itemName" label="项目" min-width="140" show-overflow-tooltip />
              <el-table-column label="类别" width="80" align="center">
                <template #default="{ row }">{{ feeTypeLabel(row.feeType) }}</template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="65" align="center" />
              <el-table-column label="单价" width="85" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
              </el-table-column>
              <el-table-column label="金额" width="90" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { getDepartmentListCached, getDoctorListCached, type Department, type Doctor } from '@/api/basedata'
import { getPatientPage, type Patient } from '@/api/patient'
import { PAY_METHOD_OPTIONS } from '@/api/billing'
import { feeTypeLabel } from '@/api/billing'
import { fmtMoney } from '@/api/registration'
import {
  addDeposit,
  ADMISSION_TYPE_OPTIONS,
  admissionTypeLabel,
  createAdmission,
  dischargeAdmission,
  DISCHARGE_WAY_OPTIONS,
  dischargeWayLabel,
  getAdmissionDetail,
  getAdmissionPage,
  getBeds,
  getDailyFees,
  INP_STATUS_OPTIONS,
  inpStatusLabel,
  inpStatusTagType,
  normalizeDepositTotal,
  transferAdmission,
  type Admission,
  type Bed,
  type DailyFeeGroup,
  type Ward,
} from '@/api/inp'
import { getWards } from '@/api/inp'

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<Admission[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  admissionNo: '',
  status: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getAdmissionPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      admissionNo: query.admissionNo.trim() || undefined,
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
  query.admissionNo = ''
  query.status = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 患者远程搜索 ----------------
const patientOptions = ref<Patient[]>([])
const patientSearching = ref(false)

async function searchPatients(kw: string) {
  patientSearching.value = true
  try {
    const key = kw.trim()
    let res = await getPatientPage({ pageNum: 1, pageSize: 20, name: key || undefined })
    let rows = res.list ?? []
    // 按姓名搜不到时尝试按建档号搜索
    if (key && rows.length === 0) {
      res = await getPatientPage({ pageNum: 1, pageSize: 20, patientNo: key })
      rows = res.list ?? []
    }
    patientOptions.value = rows
  } catch {
    patientOptions.value = []
  } finally {
    patientSearching.value = false
  }
}

// ---------------- 入院登记 ----------------
const admDialogVisible = ref(false)
const admSubmitting = ref(false)
const admFormRef = ref<FormInstance>()
const deptOptions = ref<Department[]>([])
const wardAllOptions = ref<Ward[]>([])
const doctorOptions = ref<Doctor[]>([])
const doctorLoading = ref(false)
const bedOptions = ref<Bed[]>([])
const bedLoading = ref(false)

const admForm = reactive({
  patientId: undefined as number | undefined,
  deptId: undefined as number | undefined,
  wardId: undefined as number | undefined,
  bedId: undefined as number | undefined,
  doctorId: undefined as number | undefined,
  admissionType: 1,
  plannedDiagnosis: '',
  depositAmount: 0,
  payMethod: 1,
})

const admRules: FormRules = {
  patientId: [{ required: true, message: '请搜索并选择患者', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择入院科室', trigger: 'change' }],
  wardId: [{ required: true, message: '请选择病区', trigger: 'change' }],
  bedId: [{ required: true, message: '请选择床位', trigger: 'change' }],
  doctorId: [{ required: true, message: '请选择主治医生', trigger: 'change' }],
  admissionType: [{ required: true, message: '请选择入院类型', trigger: 'change' }],
  depositAmount: [
    { required: true, message: '请输入首笔押金（需大于 0）', trigger: 'change' },
    {
      validator: (_rule: unknown, value: number, callback: (err?: Error) => void) => {
        if (!value || value <= 0) callback(new Error('首笔押金必须大于 0'))
        else callback()
      },
      trigger: 'change',
    },
  ],
}

/** 病区按所选科室过滤（一百零八轮 D4：无匹配时不回退全部——后端强校验 ward.deptId，回退必败组合） */
const wardOptions = computed(() => {
  if (!admForm.deptId) return wardAllOptions.value
  return wardAllOptions.value.filter((w) => w.deptId === admForm.deptId)
})

async function fetchWards() {
  if (wardAllOptions.value.length > 0) return
  try {
    wardAllOptions.value = (await getWards()) ?? []
  } catch {
    wardAllOptions.value = []
  }
}

async function fetchDepts() {
  if (deptOptions.value.length > 0) return
  try {
    deptOptions.value = (await getDepartmentListCached({ deptType: 1, status: 1 })) ?? []
  } catch {
    deptOptions.value = []
  }
}

// 一百零八轮 D7：科室→医生联动竞态守卫——快速切换科室时旧请求后到不得覆盖新结果
let doctorSeq = 0
async function handleDeptChange() {
  admForm.wardId = undefined
  admForm.bedId = undefined
  admForm.doctorId = undefined
  bedOptions.value = []
  doctorOptions.value = []
  if (!admForm.deptId) return
  const seq = ++doctorSeq
  doctorLoading.value = true
  try {
    const opts = (await getDoctorListCached({ deptId: admForm.deptId, status: 1 })) ?? []
    if (seq === doctorSeq) doctorOptions.value = opts
  } catch {
    if (seq === doctorSeq) doctorOptions.value = []
  } finally {
    if (seq === doctorSeq) doctorLoading.value = false
  }
}

// 一百零八轮 D7：病区→床位联动竞态守卫（同款）
let bedSeq = 0
async function handleWardChange() {
  admForm.bedId = undefined
  bedOptions.value = []
  if (!admForm.wardId) return
  const seq = ++bedSeq
  bedLoading.value = true
  try {
    const opts = ((await getBeds({ wardId: admForm.wardId, bedStatus: 1 })) ?? []).filter(
      (b) => b.bedStatus === 1
    )
    if (seq === bedSeq) bedOptions.value = opts
  } catch {
    if (seq === bedSeq) bedOptions.value = []
  } finally {
    if (seq === bedSeq) bedLoading.value = false
  }
}

async function openAdmissionDialog() {
  admForm.patientId = undefined
  admForm.deptId = undefined
  admForm.wardId = undefined
  admForm.bedId = undefined
  admForm.doctorId = undefined
  admForm.admissionType = 1
  admForm.plannedDiagnosis = ''
  admForm.depositAmount = 0
  admForm.payMethod = 1
  patientOptions.value = []
  admDialogVisible.value = true
  await Promise.all([fetchDepts(), fetchWards(), searchPatients('')])
}

async function handleAdmissionSubmit() {
  const valid = await admFormRef.value?.validate().catch(() => false)
  if (!valid || admForm.patientId === undefined) return
  admSubmitting.value = true
  try {
    const res = await createAdmission({
      patientId: admForm.patientId,
      deptId: admForm.deptId as number,
      wardId: admForm.wardId as number,
      bedId: admForm.bedId as number,
      doctorId: admForm.doctorId as number,
      admissionType: admForm.admissionType,
      plannedDiagnosis: admForm.plannedDiagnosis.trim() || undefined,
      depositAmount: Number(admForm.depositAmount),
      payMethod: admForm.payMethod,
    })
    ElMessage.success(`入院登记成功，住院号：${res.admissionNo}`)
    admDialogVisible.value = false
    fetchList()
  } catch {
    // B6002 已有在院记录等已在拦截器统一提示
  } finally {
    admSubmitting.value = false
  }
}

// ---------------- 行操作公共 ----------------
const currentRow = ref<Admission | null>(null)

// ---------------- 补押金 ----------------
const depDialogVisible = ref(false)
const depSubmitting = ref(false)
const depFormRef = ref<FormInstance>()
const depForm = reactive({ amount: 0, payMethod: 1 })

const depRules: FormRules = {
  amount: [{ required: true, message: '请输入补缴金额', trigger: 'change' }],
  payMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }],
}

function openDepositDialog(row: Admission) {
  currentRow.value = row
  depForm.amount = 0
  depForm.payMethod = 1
  depDialogVisible.value = true
}

async function handleDepositSubmit() {
  const valid = await depFormRef.value?.validate().catch(() => false)
  if (!valid || !currentRow.value) return
  depSubmitting.value = true
  try {
    const res = await addDeposit(currentRow.value.id, {
      amount: Number(depForm.amount),
      payMethod: depForm.payMethod,
    })
    const totalText = fmtMoney(normalizeDepositTotal(res))
    ElMessageBox.alert(`补缴成功，累计押金：¥${totalText}`, '补押金成功', {
      type: 'success',
      confirmButtonText: '知道了',
    }).catch(() => {})
    depDialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    depSubmitting.value = false
  }
}

// ---------------- 转科 ----------------
const trDialogVisible = ref(false)
const trSubmitting = ref(false)
const trBedLoading = ref(false)
const trFormRef = ref<FormInstance>()
const trBedOptions = ref<Bed[]>([])
const trForm = reactive({
  toWardId: undefined as number | undefined,
  toBedId: undefined as number | undefined,
  reason: '',
})

const trRules: FormRules = {
  toWardId: [{ required: true, message: '请选择目标病区', trigger: 'change' }],
  toBedId: [{ required: true, message: '请选择目标床位', trigger: 'change' }],
  reason: [{ required: true, message: '请输入转科原因', trigger: 'blur' }],
}

function openTransferDialog(row: Admission) {
  currentRow.value = row
  trForm.toWardId = undefined
  trForm.toBedId = undefined
  trForm.reason = ''
  trBedOptions.value = []
  trDialogVisible.value = true
  fetchWards()
}

async function handleTargetWardChange() {
  trForm.toBedId = undefined
  trBedOptions.value = []
  if (!trForm.toWardId) return
  trBedLoading.value = true
  try {
    const beds = (await getBeds({ wardId: trForm.toWardId, bedStatus: 1 })) ?? []
    trBedOptions.value = beds.filter((b) => b.bedStatus === 1 && b.id !== currentRow.value?.bedId)
  } catch {
    trBedOptions.value = []
  } finally {
    trBedLoading.value = false
  }
}

async function handleTransferSubmit() {
  const valid = await trFormRef.value?.validate().catch(() => false)
  if (!valid || !currentRow.value) return
  trSubmitting.value = true
  try {
    await transferAdmission(currentRow.value.id, {
      toWardId: trForm.toWardId as number,
      toBedId: trForm.toBedId as number,
      reason: trForm.reason.trim(),
    })
    ElMessage.success('转科成功')
    trDialogVisible.value = false
    fetchList()
  } catch {
    // B6005 无可用床位等已在拦截器统一提示
  } finally {
    trSubmitting.value = false
  }
}

// ---------------- 出院申请 ----------------
const dcDialogVisible = ref(false)
const dcSubmitting = ref(false)
const dcFormRef = ref<FormInstance>()
const dcForm = reactive({ dischargeWay: undefined as number | undefined, dischargeDiagnosis: '' })
// 一百零八轮 D5：出院弹窗费用合计回显
const dcFeeTotal = ref(0)
const dcFeeLoading = ref(false)

const dcRules: FormRules = {
  dischargeWay: [{ required: true, message: '请选择出院方式', trigger: 'change' }],
  dischargeDiagnosis: [{ required: true, message: '请输入出院诊断', trigger: 'blur' }],
}

function openDischargeDialog(row: Admission) {
  currentRow.value = row
  dcForm.dischargeWay = undefined
  dcForm.dischargeDiagnosis = ''
  dcDialogVisible.value = true
  // 一百零八轮 D5：出院前回显未结费用合计
  dcFeeTotal.value = 0
  dcFeeLoading.value = true
  getDailyFees(row.id)
    .then((groups) => {
      dcFeeTotal.value = (groups ?? []).reduce(
        (sum, g) => sum + (Number(g.totalAmount) || 0),
        0
      )
    })
    .catch(() => {
      dcFeeTotal.value = 0
    })
    .finally(() => {
      dcFeeLoading.value = false
    })
}

async function handleDischargeSubmit() {
  const valid = await dcFormRef.value?.validate().catch(() => false)
  if (!valid || !currentRow.value) return
  dcSubmitting.value = true
  try {
    await dischargeAdmission(currentRow.value.id, {
      dischargeWay: dcForm.dischargeWay as number,
      dischargeDiagnosis: dcForm.dischargeDiagnosis.trim(),
    })
    ElMessage.success('出院办理成功，状态已变为「出院未结」')
    dcDialogVisible.value = false
    fetchList()
  } catch {
    // B6006 有未完成事项等已在拦截器统一提示
  } finally {
    dcSubmitting.value = false
  }
}

// ---------------- 详情抽屉 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const detail = ref<Admission | null>(null)
const feeGroups = ref<DailyFeeGroup[]>([])

async function openDetail(row: Admission) {
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  feeGroups.value = []
  try {
    detail.value = await getAdmissionDetail(row.id)
    feeGroups.value = (await getDailyFees(row.id)) ?? []
  } catch {
    detail.value = null
  } finally {
    drawerLoading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.deposit-pay {
  margin-left: 12px;
}

.dep-line {
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.dep-amount {
  color: #f56c6c;
  font-weight: 600;
}

.drawer-section {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}

.fee-group {
  margin-bottom: 12px;
}

.fee-group-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  margin-bottom: 6px;
  background: #f5f7fa;
  border-radius: 3px;
  font-weight: 600;
  color: #303133;
}

.fee-group-total {
  color: #f56c6c;
}
</style>
