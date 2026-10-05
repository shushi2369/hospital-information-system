<template>
  <div class="workbench">
    <!-- 左侧：候诊列表 -->
    <div class="page-card queue-panel">
      <div class="panel-head">
        <span class="toolbar-title">候诊列表</span>
        <el-button link type="primary" :icon="Refresh" :loading="queueLoading" @click="fetchQueue">
          刷新
        </el-button>
      </div>
      <el-table
        v-loading="queueLoading"
        :data="queue"
        size="small"
        border
        stripe
        highlight-current-row
      >
        <el-table-column prop="queueNo" label="号" width="55" align="center" />
        <el-table-column prop="patientName" label="患者" min-width="80" show-overflow-tooltip />
        <el-table-column label="号别" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.regType === 2 ? 'warning' : 'info'">
              {{ regTypeLabel(row.regType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时段" width="60" align="center">
          <template #default="{ row }">{{ periodLabel(row.period) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 10"
              link
              type="primary"
              :loading="startingId === row.id"
              @click="handleStart(row)"
            >
              {{ startedMap[row.id] ? '继续接诊' : '接诊' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 右侧：工作区 -->
    <div class="page-card work-panel" v-loading="detailLoading">
      <template v-if="detail">
        <!-- 顶部患者条 -->
        <div class="patient-bar">
          <div class="bar-main">
            <div class="bar-info">
              <span class="patient-name">{{ detail.patient.name }}</span>
              <el-tag size="small">{{ genderLabel(detail.patient.gender) }}</el-tag>
              <span v-if="ageText">{{ ageText }}</span>
              <span>建档号：{{ detail.patient.patientNo }}</span>
              <span>就诊号：{{ detail.visitNo }}</span>
              <span>{{ detail.deptName }}｜{{ detail.doctorName }}</span>
              <el-tag size="small" :type="visitStatusTagType(detail.status)">
                {{ visitStatusLabel(detail.status) }}
              </el-tag>
            </div>
            <div class="bar-actions">
              <el-button v-if="!readonly" type="primary" :loading="completing" @click="handleComplete">
                提交病历
              </el-button>
            </div>
          </div>
          <div v-if="detail.patient.allergyHistory" class="allergy-tip">
            过敏史：{{ detail.patient.allergyHistory }}
          </div>
        </div>

        <el-tabs v-model="activeTab">
          <!-- ① 病历 -->
          <el-tab-pane label="病历" name="record">
            <el-form :model="recordForm" label-width="90px" class="record-form">
              <el-form-item label="主诉">
                <el-input
                  v-model="recordForm.chiefComplaint"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  :disabled="readonly"
                  placeholder="请输入主诉"
                />
              </el-form-item>
              <el-form-item label="现病史">
                <el-input
                  v-model="recordForm.presentIllness"
                  type="textarea"
                  :rows="3"
                  maxlength="1000"
                  show-word-limit
                  :disabled="readonly"
                  placeholder="请输入现病史"
                />
              </el-form-item>
              <el-form-item label="体格检查">
                <el-input
                  v-model="recordForm.physicalExam"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  :disabled="readonly"
                  placeholder="请输入体格检查结果"
                />
              </el-form-item>
              <el-form-item label="处理意见">
                <el-input
                  v-model="recordForm.advice"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  :disabled="readonly"
                  placeholder="请输入处理意见"
                />
              </el-form-item>
              <el-form-item v-if="!readonly">
                <el-button type="primary" :loading="recordSaving" @click="handleSaveRecord">
                  暂存病历
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- ② 诊断 -->
          <el-tab-pane label="诊断" name="diagnosis">
            <div class="table-toolbar">
              <span class="toolbar-title">诊断信息</span>
              <el-button v-if="!readonly" v-perm="'clinic:diagnosis:create'" type="primary" size="small" :icon="Plus" @click="openDiagDialog">
                新增诊断
              </el-button>
            </div>
            <el-table :data="detail.diagnoses" border stripe size="small">
              <el-table-column label="主/次" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.diagnosisType === 1 ? 'primary' : 'info'">
                    {{ diagnosisTypeLabel(row.diagnosisType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="diagnosisCode" label="诊断编码" min-width="110">
                <template #default="{ row }">{{ row.diagnosisCode || '-' }}</template>
              </el-table-column>
              <el-table-column prop="diagnosisName" label="诊断名称" min-width="180" />
              <el-table-column v-if="!readonly" label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" v-perm="'clinic:diagnosis:create'" @click="handleDeleteDiagnosis(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- ③ 处方 -->
          <el-tab-pane label="处方" name="prescription">
            <div class="table-toolbar">
              <span class="toolbar-title">处方信息</span>
              <el-button v-if="!readonly" v-perm="'clinic:prescription:create'" type="primary" size="small" :icon="Plus" @click="openRxDialog">
                新开处方
              </el-button>
            </div>
            <el-empty
              v-if="!detail.prescriptions || detail.prescriptions.length === 0"
              description="暂无处方"
              :image-size="80"
            />
            <div v-for="rx in detail.prescriptions" :key="rx.id" class="rx-card">
              <div class="rx-head">
                <span class="rx-no">处方号：{{ rx.rxNo }}</span>
                <el-tag size="small" :type="prescriptionStatusTagType(rx.status)">
                  {{ prescriptionStatusLabel(rx.status) }}
                </el-tag>
                <el-tag size="small" :type="chargeStatusTagType(rx.chargeStatus)">
                  {{ chargeStatusLabel(rx.chargeStatus) }}
                </el-tag>
                <span class="rx-amount">金额：¥{{ fmtMoney(rx.totalAmount) }}</span>
                <el-button
                  v-if="!readonly && (rx.status === 10 || rx.status === 20)"
                  link
                  type="danger"
                  size="small"
                  @click="handleVoidRx(rx)"
                >
                  作废
                </el-button>
              </div>
              <el-table :data="rx.items" border size="small">
                <el-table-column prop="drugName" label="药品名称" min-width="130" show-overflow-tooltip />
                <el-table-column prop="spec" label="规格" min-width="100" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.spec || '-' }}</template>
                </el-table-column>
                <el-table-column prop="dosage" label="剂量" min-width="80" />
                <el-table-column prop="frequency" label="频次" width="70" align="center" />
                <el-table-column prop="usageRoute" label="用法" width="70" align="center" />
                <el-table-column prop="days" label="天数" width="60" align="center" />
                <el-table-column label="数量" width="80" align="center">
                  <template #default="{ row }">{{ row.quantity }}{{ row.unit || '' }}</template>
                </el-table-column>
                <el-table-column label="单价" width="80" align="right">
                  <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
                </el-table-column>
                <el-table-column label="金额" width="90" align="right">
                  <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
                </el-table-column>
                <el-table-column prop="usageNote" label="嘱托" min-width="110" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.usageNote || '-' }}</template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- ④ 检查/检验 -->
          <el-tab-pane label="检查/检验" name="exam">
            <div class="table-toolbar">
              <span class="toolbar-title">检查/检验申请</span>
              <el-button v-if="!readonly" v-perm="'clinic:exam:create'" type="primary" size="small" :icon="Plus" @click="openExamDialog">
                新增申请
              </el-button>
            </div>
            <el-table :data="detail.examApplications" border stripe size="small">
              <el-table-column prop="applyNo" label="申请单号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="itemName" label="项目名称" min-width="140" />
              <el-table-column label="类别" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.applyType === 2 || row.applyType === 4 ? 'success' : 'warning'">
                    {{ examApplyTypeLabel(row.applyType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="金额" width="90" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.price) }}</template>
              </el-table-column>
              <el-table-column label="收费状态" width="95" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="chargeStatusTagType(row.chargeStatus)">
                    {{ chargeStatusLabel(row.chargeStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="执行状态" width="95" align="center">
                <template #default="{ row }">{{ examApplyStatusLabel(row.status) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!detailLoading" description="请从左侧候诊列表选择患者接诊" />
    </div>

    <!-- 新增诊断弹窗 -->
    <el-dialog v-model="diagDialogVisible" title="新增诊断" width="460px" destroy-on-close append-to-body>
      <el-form ref="diagFormRef" :model="diagForm" :rules="diagRules" label-width="90px">
        <el-form-item label="诊断名称" prop="diagnosisName">
          <el-input v-model="diagForm.diagnosisName" placeholder="请输入诊断名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="诊断编码" prop="diagnosisCode">
          <el-input v-model="diagForm.diagnosisCode" placeholder="选填，如 ICD-10 编码" maxlength="32" />
        </el-form-item>
        <el-form-item label="主/次" prop="diagnosisType">
          <el-radio-group v-model="diagForm.diagnosisType">
            <el-radio v-for="opt in DIAGNOSIS_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="diagDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="diagSubmitting" @click="handleDiagSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新开处方弹窗 -->
    <el-dialog
      v-model="rxDialogVisible"
      title="新开处方"
      width="1080px"
      destroy-on-close
      append-to-body
      top="6vh"
    >
      <div class="rx-dialog-toolbar">
        <el-button type="primary" plain size="small" :icon="Plus" @click="addRxRow">添加明细</el-button>
        <span class="rx-dialog-tip">药品、剂量、频次、用法、天数、数量均需填写后才会提交</span>
      </div>
      <el-table :data="rxRows" border size="small" max-height="420">
        <el-table-column label="药品" min-width="210">
          <template #default="{ row }">
            <el-select
              v-model="row.drugId"
              filterable
              :loading="drugLoading"
              placeholder="选择药品"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="d in drugOptions"
                :key="d.id"
                :label="`${d.drugName}（${d.spec}）`"
                :value="d.id"
              >
                <div class="patient-option">
                  <span>{{ d.drugName }}</span>
                  <span class="option-sub">{{ d.spec }}｜¥{{ d.retailPrice }}/{{ d.unit }}</span>
                </div>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="规格" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ drugOf(row.drugId)?.spec || '-' }}</template>
        </el-table-column>
        <el-table-column label="单价" width="80" align="right">
          <template #default="{ row }">
            {{ drugOf(row.drugId) ? `¥${drugOf(row.drugId)?.retailPrice}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="剂量" width="110">
          <template #default="{ row }">
            <el-input v-model="row.dosage" placeholder="如 0.5g/次" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="频次" width="140">
          <template #default="{ row }">
            <el-select v-model="row.frequency" placeholder="频次" size="small" style="width: 100%">
              <el-option v-for="f in FREQUENCY_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="用法" width="95">
          <template #default="{ row }">
            <el-select v-model="row.usageRoute" placeholder="用法" size="small" style="width: 100%">
              <el-option v-for="u in USAGE_ROUTE_OPTIONS" :key="u.value" :label="u.label" :value="u.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="天数" width="115">
          <template #default="{ row }">
            <el-input-number
              v-model="row.days"
              :min="1"
              :max="90"
              :precision="0"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="115">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="999"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="嘱托" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.usageNote" placeholder="选填" size="small" maxlength="128" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeRxRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="rxDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rxSubmitting" @click="handleRxSubmit">确认开立</el-button>
      </template>
    </el-dialog>

    <!-- 新增检查/检验申请弹窗 -->
    <el-dialog
      v-model="examDialogVisible"
      title="新增检查/检验申请"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-form ref="examFormRef" :model="examForm" :rules="examRules" label-width="90px">
        <el-form-item label="项目" prop="chargeItemId">
          <el-select
            v-model="examForm.chargeItemId"
            filterable
            :loading="examLoading"
            placeholder="选择检查/检验项目"
            style="width: 100%"
          >
            <el-option-group v-for="group in examGroups" :key="group.label" :label="group.label">
              <el-option
                v-for="item in group.options"
                :key="item.id"
                :label="`${item.itemName}（¥${item.price}/${item.unit}）`"
                :value="item.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedExamItem" label="项目金额">
          <span class="exam-price">¥{{ fmtMoney(selectedExamItem.price) }}</span>
        </el-form-item>
        <el-form-item label="备注" prop="requirement">
          <el-input
            v-model="examForm.requirement"
            type="textarea"
            :rows="2"
            maxlength="200"
            placeholder="检查要求/注意事项，选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="examDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="examSubmitting" @click="handleExamSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { getChargeItemList, getDrugPage, type ChargeItem, type Drug } from '@/api/basedata'
import {
  addDiagnosis,
  chargeStatusLabel,
  chargeStatusTagType,
  completeVisit,
  createExamApplication,
  createPrescription,
  deleteDiagnosis,
  DIAGNOSIS_TYPE_OPTIONS,
  examApplyStatusLabel,
  examApplyTypeLabel,
  FREQUENCY_OPTIONS,
  getClinicQueue,
  getVisitDetail,
  saveVisitRecord,
  startVisit,
  prescriptionStatusLabel,
  prescriptionStatusTagType,
  USAGE_ROUTE_OPTIONS,
  voidPrescription,
  visitStatusLabel,
  visitStatusTagType,
  diagnosisTypeLabel,
  type VisitDetail,
  type VisitPrescription,
  type VisitDiagnosis,
} from '@/api/clinic'
import { genderLabel, calcAge } from '@/api/patient'
import { fmtMoney, periodLabel, regTypeLabel, type Registration } from '@/api/registration'
import { toList } from '@/api/request'

const route = useRoute()
const router = useRouter()

// ---------------- 左侧候诊队列 ----------------
const queue = ref<Registration[]>([])
const queueLoading = ref(false)
const startingId = ref<number | null>(null)

/** 本次会话已接诊的挂号 → visitId 映射（sessionStorage 持久化，用于展示"继续接诊"） */
const STARTED_KEY = 'his_started_visits'
const startedMap = reactive<Record<number, number>>({})

function loadStarted() {
  try {
    const raw = sessionStorage.getItem(STARTED_KEY)
    if (raw) Object.assign(startedMap, JSON.parse(raw) as Record<number, number>)
  } catch {
    // 忽略本地缓存异常
  }
}

function markStarted(regId: number, visitId: number) {
  startedMap[regId] = visitId
  try {
    sessionStorage.setItem(STARTED_KEY, JSON.stringify(startedMap))
  } catch {
    // 忽略
  }
}

async function fetchQueue() {
  queueLoading.value = true
  try {
    queue.value = toList<Registration>(await getClinicQueue())
  } finally {
    queueLoading.value = false
  }
}

async function handleStart(row: Registration) {
  // 已接诊过（后端队列返回 visitId 或本会话标记）：直接回工作台继续
  const known = row.visitId ?? startedMap[row.id]
  if (known) {
    await router.replace({ query: { ...route.query, visitId: String(known) } })
    return
  }
  startingId.value = row.id
  try {
    const res = await startVisit(row.id)
    markStarted(row.id, res)
    await router.replace({ query: { ...route.query, visitId: String(res) } })
  } catch {
    // B2001 就诊已完成等错误已在拦截器中统一提示
  } finally {
    startingId.value = null
  }
}

// ---------------- 就诊详情加载 ----------------
const detail = ref<VisitDetail | null>(null)
const detailLoading = ref(false)
const activeTab = ref('record')

const visitId = computed(() => {
  const raw = route.query.visitId
  const n = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(n) && n > 0 ? n : null
})

/** 已完成（status=30）后整页只读 */
const readonly = computed(() => detail.value?.status === 30)

const ageText = computed(() => {
  const age = calcAge(detail.value?.patient?.birthDate)
  return age === null ? '' : `${age} 岁`
})

async function loadDetail() {
  if (!visitId.value) {
    detail.value = null
    return
  }
  detailLoading.value = true
  try {
    detail.value = await getVisitDetail(visitId.value)
    fillRecordForm()
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

watch(visitId, () => loadDetail())

// ---------------- ① 病历 ----------------
const recordForm = reactive({
  chiefComplaint: '',
  presentIllness: '',
  physicalExam: '',
  advice: '',
})
const recordSaving = ref(false)

function fillRecordForm() {
  recordForm.chiefComplaint = detail.value?.chiefComplaint ?? ''
  recordForm.presentIllness = detail.value?.presentIllness ?? ''
  recordForm.physicalExam = detail.value?.physicalExam ?? ''
  recordForm.advice = detail.value?.advice ?? ''
}

async function handleSaveRecord() {
  if (!detail.value) return
  recordSaving.value = true
  try {
    await saveVisitRecord(detail.value.id, {
      chiefComplaint: recordForm.chiefComplaint || undefined,
      presentIllness: recordForm.presentIllness || undefined,
      physicalExam: recordForm.physicalExam || undefined,
      advice: recordForm.advice || undefined,
    })
    ElMessage.success('病历暂存成功')
  } catch {
    // 拦截器已统一提示
  } finally {
    recordSaving.value = false
  }
}

// ---------------- 提交病历 ----------------
const completing = ref(false)

function handleComplete() {
  if (!detail.value) return
  ElMessageBox.confirm(
    '提交后就诊将完成归档，病历内容不可再修改，确认提交？',
    '提交病历',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' }
  )
    .then(async () => {
      completing.value = true
      try {
        await completeVisit(detail.value!.id)
        ElMessage.success('病历已提交，就诊完成')
        await loadDetail()
        fetchQueue()
      } catch {
        // B2003 病历不完整等已在拦截器中统一提示
      } finally {
        completing.value = false
      }
    })
    .catch(() => {})
}

// ---------------- ② 诊断 ----------------
const diagDialogVisible = ref(false)
const diagSubmitting = ref(false)
const diagFormRef = ref<FormInstance>()
const diagForm = reactive({
  diagnosisName: '',
  diagnosisCode: '',
  diagnosisType: 1,
})

const diagRules: FormRules = {
  diagnosisName: [{ required: true, message: '请输入诊断名称', trigger: 'blur' }],
  diagnosisType: [{ required: true, message: '请选择主/次诊断', trigger: 'change' }],
}

function openDiagDialog() {
  diagForm.diagnosisName = ''
  diagForm.diagnosisCode = ''
  diagForm.diagnosisType = 1
  diagDialogVisible.value = true
}

async function handleDiagSubmit() {
  const valid = await diagFormRef.value?.validate().catch(() => false)
  if (!valid || !detail.value) return
  diagSubmitting.value = true
  try {
    await addDiagnosis(detail.value.id, {
      diagnosisName: diagForm.diagnosisName,
      diagnosisCode: diagForm.diagnosisCode || undefined,
      diagnosisType: diagForm.diagnosisType,
    })
    ElMessage.success('诊断添加成功')
    diagDialogVisible.value = false
    loadDetail()
  } catch {
    // 拦截器已统一提示
  } finally {
    diagSubmitting.value = false
  }
}

function handleDeleteDiagnosis(row: VisitDiagnosis) {
  ElMessageBox.confirm(`确认删除诊断「${row.diagnosisName}」？`, '删除诊断', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await deleteDiagnosis(row.id)
      ElMessage.success('诊断已删除')
      loadDetail()
    })
    .catch(() => {
      // 取消或失败（拦截器已提示）
    })
}

// ---------------- ③ 处方 ----------------
interface RxRow {
  drugId: number | undefined
  dosage: string
  frequency: string
  usageRoute: string
  days: number | undefined
  quantity: number | undefined
  usageNote: string
}

const rxDialogVisible = ref(false)
const rxSubmitting = ref(false)
const drugOptions = ref<Drug[]>([])
const drugLoading = ref(false)
const rxRows = ref<RxRow[]>([])

function emptyRow(): RxRow {
  return {
    drugId: undefined,
    dosage: '',
    frequency: '',
    usageRoute: '',
    days: undefined,
    quantity: undefined,
    usageNote: '',
  }
}

function drugOf(drugId?: number) {
  return drugOptions.value.find((d) => d.id === drugId)
}

function addRxRow() {
  rxRows.value.push(emptyRow())
}

function removeRxRow(index: number) {
  rxRows.value.splice(index, 1)
}

/** 拉取启用药品（一次加载 200 条供选择） */
async function fetchDrugs() {
  drugLoading.value = true
  try {
    const res = await getDrugPage({ pageNum: 1, pageSize: 200, status: 1 })
    drugOptions.value = res.list ?? []
  } catch {
    drugOptions.value = []
  } finally {
    drugLoading.value = false
  }
}

function openRxDialog() {
  rxRows.value = [emptyRow()]
  rxDialogVisible.value = true
  fetchDrugs()
}

function isRowTouched(r: RxRow): boolean {
  return !!(r.drugId || r.dosage.trim() || r.frequency || r.usageRoute || r.days || r.quantity)
}

function isRowValid(r: RxRow): boolean {
  return !!(r.drugId && r.dosage.trim() && r.frequency && r.usageRoute && r.days && r.quantity)
}

async function handleRxSubmit() {
  if (!detail.value) return
  const touched = rxRows.value.filter(isRowTouched)
  if (touched.length === 0) {
    ElMessage.warning('请至少填写一行处方明细')
    return
  }
  const invalid = touched.find((r) => !isRowValid(r))
  if (invalid) {
    ElMessage.warning('请将已填写的明细行补充完整（药品/剂量/频次/用法/天数/数量）')
    return
  }
  rxSubmitting.value = true
  try {
    const res = await createPrescription(detail.value.id, {
      items: touched.map((r) => ({
        drugId: r.drugId as number,
        dosage: r.dosage.trim(),
        frequency: r.frequency,
        usageRoute: r.usageRoute,
        days: Number(r.days),
        quantity: Number(r.quantity),
        usageNote: r.usageNote.trim() || undefined,
      })),
    })
    ElMessage.success(`处方 ${res.rxNo} 开立成功，金额 ¥${fmtMoney(res.totalAmount)}`)
    rxDialogVisible.value = false
    loadDetail()
  } catch {
    // B2005 处方明细为空等已在拦截器中统一提示
  } finally {
    rxSubmitting.value = false
  }
}

function handleVoidRx(rx: VisitPrescription) {
  ElMessageBox.confirm(`确认作废处方 ${rx.rxNo}？作废后不可恢复。`, '作废处方', {
    type: 'warning',
    confirmButtonText: '确认作废',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await voidPrescription(rx.id)
      ElMessage.success('处方已作废')
      loadDetail()
    })
    .catch(() => {
      // 取消或失败（B2006 不允许作废等已在拦截器中提示）
    })
}

// ---------------- ④ 检查/检验 ----------------
const examDialogVisible = ref(false)
const examSubmitting = ref(false)
const examFormRef = ref<FormInstance>()
const examForm = reactive({
  chargeItemId: undefined as number | undefined,
  requirement: '',
})
const examGroups = ref<Array<{ label: string; options: ChargeItem[] }>>([])
const examLoading = ref(false)

const examRules: FormRules = {
  chargeItemId: [{ required: true, message: '请选择检查/检验项目', trigger: 'change' }],
}

const selectedExamItem = computed(() => {
  for (const group of examGroups.value) {
    const hit = group.options.find((o) => o.id === examForm.chargeItemId)
    if (hit) return hit
  }
  return null
})

/** 合并加载检查（category=3）与检验（category=4）两类项目 */
async function openExamDialog() {
  examForm.chargeItemId = undefined
  examForm.requirement = ''
  examGroups.value = []
  examDialogVisible.value = true
  examLoading.value = true
  try {
    const [examItems, labItems] = await Promise.all([
      getChargeItemList({ category: 3, status: 1 }),
      getChargeItemList({ category: 4, status: 1 }),
    ])
    examGroups.value = [
      { label: '检查项目', options: toList<ChargeItem>(examItems) },
      { label: '检验项目', options: toList<ChargeItem>(labItems) },
    ]
  } catch {
    examGroups.value = []
  } finally {
    examLoading.value = false
  }
}

async function handleExamSubmit() {
  const valid = await examFormRef.value?.validate().catch(() => false)
  if (!valid || !detail.value) return
  examSubmitting.value = true
  try {
    const res = await createExamApplication(detail.value.id, {
      chargeItemId: examForm.chargeItemId as number,
      requirement: examForm.requirement || undefined,
    })
    ElMessage.success(`申请单 ${res.applyNo} 开立成功，金额 ¥${fmtMoney(res.price)}`)
    examDialogVisible.value = false
    loadDetail()
  } catch {
    // 拦截器已统一提示
  } finally {
    examSubmitting.value = false
  }
}

onMounted(() => {
  loadStarted()
  fetchQueue()
  loadDetail()
})
</script>

<style scoped>
.workbench {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.queue-panel {
  width: 400px;
  flex-shrink: 0;
}

.work-panel {
  flex: 1;
  min-width: 0;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

/* 顶部患者条 */
.patient-bar {
  padding-bottom: 12px;
  margin-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.bar-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.bar-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: #606266;
}

.patient-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.allergy-tip {
  width: 100%;
  margin-top: 8px;
  color: #f56c6c;
  font-weight: 600;
}

.record-form {
  max-width: 720px;
  padding-top: 8px;
}

/* 处方卡片 */
.rx-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 12px;
}

.rx-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.rx-no {
  font-weight: 600;
  color: #303133;
}

.rx-amount {
  margin-left: auto;
  color: #e6a23c;
  font-weight: 600;
}

.rx-dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.rx-dialog-tip {
  color: #909399;
  font-size: 12px;
}

.exam-price {
  color: #e6a23c;
  font-weight: 600;
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
</style>
