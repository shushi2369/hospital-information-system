<template>
  <div class="page-card">
    <el-tabs v-model="activeTab">
      <!-- ① 待执行单 -->
      <el-tab-pane label="待执行单" name="exec">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <span class="toolbar-title">医嘱待执行单</span>
            <el-date-picker
              v-model="execDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              style="width: 140px; margin-left: 12px"
              @change="fetchPending"
            />
          </div>
          <el-button link type="primary" :icon="Refresh" :loading="execLoading" @click="fetchPending">
            刷新
          </el-button>
        </div>
        <el-table v-loading="execLoading" :data="pendingList" border stripe size="small">
          <el-table-column prop="bedNo" label="床号" width="80" align="center">
            <template #default="{ row }">{{ row.bedNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="execDate" label="日期" width="110" align="center" />
          <el-table-column prop="execSlot" label="时段" width="110" align="center">
            <template #default="{ row }">{{ row.execSlot || '-' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="execTypeTagType(row.execType)">
                {{ execTypeLabel(row.execType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="execStatusTagType(row.status)">
                {{ execStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 1 && row.execType !== 3"
                v-perm="'nur:exec:do'"
                link
                type="primary"
                @click="handleExec(row)"
              >
                执行
              </el-button>
              <el-button
                v-if="row.status === 1 && row.execType === 3"
                v-perm="'nur:exec:do'"
                link
                type="danger"
                @click="openSkinTestDialog(row)"
              >
                皮试登记
              </el-button>
              <span v-if="row.status !== 1">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ② 体征录入与历史 -->
      <el-tab-pane label="体征管理" name="vital">
        <el-form class="search-bar" inline>
          <el-form-item label="病区">
            <el-select
              v-model="wardId"
              placeholder="选择病区"
              clearable
              style="width: 180px"
              @change="fetchNurPatients"
            >
              <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="在院患者">
            <el-select
              v-model="vitalAdmissionId"
              filterable
              placeholder="选择在院患者"
              style="width: 280px"
              @change="fetchVitals"
            >
              <el-option
                v-for="p in nurPatients"
                :key="p.admissionId"
                :label="`${p.patientName}｜${p.admissionNo}`"
                :value="p.admissionId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="日期范围">
            <el-date-picker
              v-model="vitalRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" :disabled="!vitalAdmissionId" @click="fetchVitals">
              查询
            </el-button>
            <el-button
              v-perm="'nur:vital:create'"
              type="success"
              :icon="Plus"
              :disabled="!vitalAdmissionId"
              @click="openVitalDialog"
            >
              录入体征
            </el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="vitalLoading" :data="vitals" border stripe size="small" empty-text="暂无体征记录">
          <el-table-column prop="recordTime" label="记录时间" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.recordTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="体温(℃)" width="90" align="center">
            <template #default="{ row }">{{ row.temperature ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="脉搏(次/分)" width="100" align="center">
            <template #default="{ row }">{{ row.pulse ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="呼吸(次/分)" width="100" align="center">
            <template #default="{ row }">{{ row.respiration ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="血压(mmHg)" width="110" align="center">
            <template #default="{ row }">{{ row.bpHigh ?? '-' }}/{{ row.bpLow ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="血氧(%)" width="85" align="center">
            <template #default="{ row }">{{ row.spo2 ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="疼痛评分" width="90" align="center">
            <template #default="{ row }">{{ row.painScore ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="护士" min-width="85" show-overflow-tooltip>
            <template #default="{ row }">{{ row.nurseName || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ③ 床位一览 -->
      <el-tab-pane label="床位一览" name="beds">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <span class="toolbar-title">病区床位一览</span>
            <el-select
              v-model="bedWardId"
              placeholder="选择病区"
              style="width: 180px; margin-left: 12px"
              @change="fetchNurBeds"
            >
              <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="w.id" />
            </el-select>
          </div>
          <el-button link type="primary" :icon="Refresh" :loading="bedLoading" @click="fetchNurBeds">
            刷新
          </el-button>
        </div>
        <div v-loading="bedLoading" class="bed-grid">
          <el-empty v-if="!bedLoading && nurBeds.length === 0" description="该病区暂无床位" />
          <div v-for="b in nurBeds" :key="b.id" class="bed-card" :class="`bed-card-${b.bedStatus}`">
            <div class="bed-head">
              <span class="bed-no">{{ b.bedNo }}</span>
              <el-tag size="small" :type="bedStatusTagType(b.bedStatus)">
                {{ bedStatusLabel(b.bedStatus) }}
              </el-tag>
            </div>
            <div class="bed-body">
              <template v-if="b.bedStatus === 2">
                <div class="bed-patient">{{ b.patientName || '住院患者' }}</div>
              </template>
              <template v-else>
                <div class="bed-sub">空闲床位</div>
              </template>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 皮试登记弹窗 -->
    <el-dialog v-model="skinDialogVisible" title="皮试登记" width="460px" destroy-on-close append-to-body>
      <div v-if="skinRow" class="skin-line">床号：{{ skinRow.bedNo || '-' }}｜执行日期：{{ skinRow.execDate }}</div>
      <el-form label-width="80px">
        <el-form-item label="皮试结果" required>
          <el-radio-group v-model="skinResult">
            <el-radio v-for="r in SKIN_TEST_RESULT_OPTIONS" :key="r" :value="r">{{ r }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="skinNote" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="皮试结果为阳性时，对应医嘱将自动作废并拦截摆药。"
      />
      <template #footer>
        <el-button @click="skinDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="skinSubmitting" @click="handleSkinSubmit">确认登记</el-button>
      </template>
    </el-dialog>

    <!-- 体征录入弹窗 -->
    <el-dialog v-model="vitalDialogVisible" title="录入体征" width="520px" destroy-on-close>
      <el-form ref="vitalFormRef" :model="vitalForm" :rules="vitalRules" label-width="90px">
        <el-form-item label="记录时间">
          <el-date-picker
            v-model="vitalForm.recordTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="默认当前时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="体温(℃)" prop="temperature">
          <el-input-number
            v-model="vitalForm.temperature"
            :min="VITAL_RANGES.temperature.min"
            :max="VITAL_RANGES.temperature.max"
            :precision="1"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">{{ VITAL_RANGES.temperature.min }} ~ {{ VITAL_RANGES.temperature.max }}</span>
        </el-form-item>
        <el-form-item label="脉搏" prop="pulse">
          <el-input-number
            v-model="vitalForm.pulse"
            :min="VITAL_RANGES.pulse.min"
            :max="VITAL_RANGES.pulse.max"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">{{ VITAL_RANGES.pulse.min }} ~ {{ VITAL_RANGES.pulse.max }} 次/分</span>
        </el-form-item>
        <el-form-item label="呼吸" prop="respiration">
          <el-input-number
            v-model="vitalForm.respiration"
            :min="VITAL_RANGES.respiration.min"
            :max="VITAL_RANGES.respiration.max"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">{{ VITAL_RANGES.respiration.min }} ~ {{ VITAL_RANGES.respiration.max }} 次/分</span>
        </el-form-item>
        <el-form-item label="收缩压" prop="bpHigh">
          <el-input-number
            v-model="vitalForm.bpHigh"
            :min="VITAL_RANGES.bpHigh.min"
            :max="VITAL_RANGES.bpHigh.max"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">{{ VITAL_RANGES.bpHigh.min }} ~ {{ VITAL_RANGES.bpHigh.max }} mmHg</span>
        </el-form-item>
        <el-form-item label="舒张压" prop="bpLow">
          <el-input-number
            v-model="vitalForm.bpLow"
            :min="VITAL_RANGES.bpLow.min"
            :max="VITAL_RANGES.bpLow.max"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">{{ VITAL_RANGES.bpLow.min }} ~ {{ VITAL_RANGES.bpLow.max }} mmHg</span>
        </el-form-item>
        <el-form-item label="血氧饱和度">
          <el-input-number
            v-model="vitalForm.spo2"
            :min="VITAL_RANGES.spo2.min"
            :max="VITAL_RANGES.spo2.max"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">选填，%</span>
        </el-form-item>
        <el-form-item label="疼痛评分">
          <el-input-number
            v-model="vitalForm.painScore"
            :min="VITAL_RANGES.painScore.min"
            :max="VITAL_RANGES.painScore.max"
            controls-position="right"
            style="width: 160px"
          />
          <span class="range-tip">选填，0 ~ 10</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vitalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="vitalSubmitting" @click="handleVitalSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  doExecution,
  execStatusLabel,
  execStatusTagType,
  execTypeLabel,
  execTypeTagType,
  SKIN_TEST_RESULT_OPTIONS,
  submitSkinTest,
  type ExecItem,
} from '@/api/doc'
import {
  bedStatusLabel,
  bedStatusTagType,
  getWards,
  type Bed,
  type Ward,
} from '@/api/inp'
import {
  createVitalSign,
  getNurPatients,
  getNurPendingExecutions,
  getNurWardBeds,
  getVitalSigns,
  VITAL_RANGES,
  type NurPatient,
  type VitalSign,
} from '@/api/nur'
import { todayStr } from '@/api/registration'

const activeTab = ref('exec')

// ---------------- 病区 ----------------
const wards = ref<Ward[]>([])
const wardId = ref<number | undefined>(undefined)

async function fetchWards() {
  try {
    wards.value = (await getWards()) ?? []
    if (wards.value.length > 0 && wardId.value === undefined) {
      wardId.value = wards.value[0].id
    }
  } catch {
    wards.value = []
  }
}

// ---------------- ① 待执行单 ----------------
const execDate = ref(todayStr())
const execLoading = ref(false)
const pendingList = ref<ExecItem[]>([])

async function fetchPending() {
  execLoading.value = true
  try {
    pendingList.value = (await getNurPendingExecutions({ execDate: execDate.value })) ?? []
  } catch {
    pendingList.value = []
  } finally {
    execLoading.value = false
  }
}

function handleExec(row: ExecItem) {
  ElMessageBox.confirm(`确认执行床号 ${row.bedNo || '-'} 的${execTypeLabel(row.execType)}单？`, '执行确认', {
    type: 'warning',
    confirmButtonText: '确认执行',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await doExecution(row.id)
      ElMessage.success('执行成功')
      fetchPending()
    })
    .catch(() => {
      // 取消或 B6102/B6103 拦截（拦截器已提示）
    })
}

// ---------------- 皮试登记 ----------------
const skinDialogVisible = ref(false)
const skinSubmitting = ref(false)
const skinRow = ref<ExecItem | null>(null)
const skinResult = ref('阴性')
const skinNote = ref('')

function openSkinTestDialog(row: ExecItem) {
  skinRow.value = row
  skinResult.value = '阴性'
  skinNote.value = ''
  skinDialogVisible.value = true
}

async function handleSkinSubmit() {
  if (!skinRow.value) return
  skinSubmitting.value = true
  try {
    await submitSkinTest(skinRow.value.id, skinResult.value, skinNote.value.trim() || undefined)
    ElMessage.success(`皮试登记成功：${skinResult.value}`)
    skinDialogVisible.value = false
    fetchPending()
  } catch {
    // 拦截器已统一提示
  } finally {
    skinSubmitting.value = false
  }
}

// ---------------- ② 体征管理 ----------------
const nurPatients = ref<NurPatient[]>([])
const vitalAdmissionId = ref<number | undefined>(undefined)
const vitalRange = ref<[string, string] | null>(null)
const vitalLoading = ref(false)
const vitals = ref<VitalSign[]>([])

async function fetchNurPatients() {
  try {
    nurPatients.value = (await getNurPatients(wardId.value ? { wardId: wardId.value } : {})) ?? []
  } catch {
    nurPatients.value = []
  }
  if (vitalAdmissionId.value && !nurPatients.value.some((p) => p.admissionId === vitalAdmissionId.value)) {
    vitalAdmissionId.value = undefined
    vitals.value = []
  }
}

async function fetchVitals() {
  if (!vitalAdmissionId.value) {
    vitals.value = []
    return
  }
  vitalLoading.value = true
  try {
    vitals.value =
      (await getVitalSigns({
        admissionId: vitalAdmissionId.value,
        // 日期选择器值是 YYYY-MM-DD，后端收 LocalDateTime——拼接当日起止时刻（契约审计 P1）
        startTime: vitalRange.value?.[0] ? `${vitalRange.value[0]}T00:00:00` : undefined,
        endTime: vitalRange.value?.[1] ? `${vitalRange.value[1]}T23:59:59` : undefined,
      })) ?? []
  } catch {
    vitals.value = []
  } finally {
    vitalLoading.value = false
  }
}

const vitalDialogVisible = ref(false)
const vitalSubmitting = ref(false)
const vitalFormRef = ref<FormInstance>()
const vitalForm = reactive({
  recordTime: '',
  temperature: 36.5,
  pulse: 80,
  respiration: 18,
  bpHigh: 120,
  bpLow: 80,
  spo2: undefined as number | undefined,
  painScore: undefined as number | undefined,
})

const vitalRules: FormRules = {
  temperature: [{ required: true, message: '请输入体温', trigger: 'change' }],
  pulse: [{ required: true, message: '请输入脉搏', trigger: 'change' }],
  respiration: [{ required: true, message: '请输入呼吸', trigger: 'change' }],
  bpHigh: [{ required: true, message: '请输入收缩压', trigger: 'change' }],
  bpLow: [{ required: true, message: '请输入舒张压', trigger: 'change' }],
}

function openVitalDialog() {
  vitalForm.recordTime = ''
  vitalForm.temperature = 36.5
  vitalForm.pulse = 80
  vitalForm.respiration = 18
  vitalForm.bpHigh = 120
  vitalForm.bpLow = 80
  vitalForm.spo2 = undefined
  vitalForm.painScore = undefined
  vitalDialogVisible.value = true
}

async function handleVitalSubmit() {
  const valid = await vitalFormRef.value?.validate().catch(() => false)
  if (!valid || !vitalAdmissionId.value) return
  vitalSubmitting.value = true
  try {
    await createVitalSign({
      admissionId: vitalAdmissionId.value,
      recordTime: vitalForm.recordTime || undefined,
      temperature: Number(vitalForm.temperature),
      pulse: Number(vitalForm.pulse),
      respiration: Number(vitalForm.respiration),
      bpHigh: Number(vitalForm.bpHigh),
      bpLow: Number(vitalForm.bpLow),
      spo2: vitalForm.spo2,
      painScore: vitalForm.painScore,
    })
    ElMessage.success('体征录入成功')
    vitalDialogVisible.value = false
    fetchVitals()
  } catch {
    // A0001 超范围等已在拦截器统一提示
  } finally {
    vitalSubmitting.value = false
  }
}

// ---------------- ③ 床位一览 ----------------
const bedWardId = ref<number | undefined>(undefined)
const nurBeds = ref<Bed[]>([])
const bedLoading = ref(false)

async function fetchNurBeds() {
  if (!bedWardId.value) {
    nurBeds.value = []
    return
  }
  bedLoading.value = true
  try {
    nurBeds.value = (await getNurWardBeds({ wardId: bedWardId.value })) ?? []
  } catch {
    nurBeds.value = []
  } finally {
    bedLoading.value = false
  }
}

// 切换病区时同步床位一览病区
watch(wardId, (v) => {
  if (v && bedWardId.value === undefined) {
    bedWardId.value = v
    fetchNurBeds()
  }
})

onMounted(async () => {
  await fetchWards()
  fetchPending()
  fetchNurPatients()
  if (bedWardId.value === undefined && wardId.value) {
    bedWardId.value = wardId.value
  }
  fetchNurBeds()
})
</script>

<style scoped>
.toolbar-left {
  display: flex;
  align-items: center;
}

.bed-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  min-height: 120px;
}

.bed-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
}

.bed-card-1 {
  border-top: 3px solid #67c23a;
}

.bed-card-2 {
  border-top: 3px solid #f56c6c;
}

.bed-card-3 {
  border-top: 3px solid #e6a23c;
}

.bed-card-4 {
  border-top: 3px solid #c0c4cc;
  background: #fafafa;
}

.bed-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bed-no {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.bed-body {
  margin-top: 8px;
}

.bed-patient {
  font-weight: 600;
  color: #303133;
}

.bed-sub {
  font-size: 12px;
  color: #909399;
}

.range-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.skin-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}
</style>
