<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="病案号">
        <el-input
          v-model="query.mrcNo"
          placeholder="请输入病案号"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="归档状态">
        <el-select v-model="query.archiveStatus" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="o in ARCHIVE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="质控状态">
        <el-select v-model="query.qcStatus" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="o in MRC_QC_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="mrcNo" label="病案号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="patientName" label="患者" min-width="90" show-overflow-tooltip>
        <template #default="{ row }">{{ row.patientName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="admissionId" label="住院ID" width="90" align="center" />
      <el-table-column label="归档状态" width="95" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="archiveStatusTagType(row.archiveStatus)">
            {{ archiveStatusLabel(row.archiveStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质控状态" width="95" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="mrcQcStatusTagType(row.qcStatus)">
            {{ mrcQcStatusLabel(row.qcStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="archiveTime" label="归档时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.archiveTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openHomepageDrawer(row)">首页详情</el-button>
          <el-button v-perm="'mrc:homepage:code'" link type="primary" @click="openCodeDialog(row)">
            首页编码
          </el-button>
          <el-button v-perm="'mrc:homepage:qc'" link type="warning" @click="openQcDialog(row)">
            首页质控
          </el-button>
          <el-button
            v-if="row.archiveStatus === 10"
            v-perm="'mrc:archive:do'"
            link
            type="success"
            @click="handleArchive(row)"
          >
            归档
          </el-button>
          <el-button
            v-if="row.archiveStatus === 20"
            v-perm="'mrc:borrow:create'"
            link
            type="warning"
            @click="openBorrowDialog(row)"
          >
            借阅
          </el-button>
          <el-button
            v-if="row.archiveStatus === 30"
            v-perm="'mrc:borrow:create'"
            link
            type="success"
            @click="handleReturn(row)"
          >
            归还
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

    <!-- 首页编码弹窗 -->
    <el-dialog v-model="codeDialogVisible" title="病案首页编码" width="560px" destroy-on-close>
      <div v-if="codeRow" class="mrc-line">
        病案号：{{ codeRow.mrcNo }}｜患者：{{ codeRow.patientName || '-' }}｜住院ID：{{ codeRow.admissionId }}
      </div>
      <el-form ref="codeFormRef" :model="codeForm" :rules="codeRules" label-width="90px">
        <el-form-item label="主诊断" prop="mainDiagnosisCode" class="code-item">
          <el-select
            v-model="codeForm.mainDiagnosisCode"
            filterable
            remote
            :remote-method="searchIcd"
            :loading="icdLoading"
            placeholder="输入 ICD 编码或诊断名称搜索"
            style="width: 100%"
            @change="handleIcdChange"
          >
            <el-option
              v-for="i in icdOptions"
              :key="i.code"
              :label="`${i.code} ${i.name}`"
              :value="i.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="诊断名称" prop="mainDiagnosisName">
          <el-input v-model="codeForm.mainDiagnosisName" placeholder="选择主诊断后自动带出，可修改" maxlength="128" />
        </el-form-item>
        <el-form-item label="其他诊断">
          <el-input
            v-model="codeForm.otherDiagnoses"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="选填，多个诊断用分号分隔；每条建议「编码 名称」如 A01.0 伤寒"
          />
        </el-form-item>
        <el-form-item label="手术编码">
          <el-input v-model="codeForm.operationCode" placeholder="选填，如 ICD-9-CM3 手术操作编码" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="codeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="codeSubmitting" @click="handleCodeSubmit">保存编码</el-button>
      </template>
    </el-dialog>

    <!-- 首页质控弹窗 -->
    <el-dialog v-model="qcDialogVisible" title="病案首页质控" width="440px" destroy-on-close>
      <div v-if="qcRow" class="mrc-line">病案号：{{ qcRow.mrcNo }}｜患者：{{ qcRow.patientName || '-' }}</div>
      <el-form label-width="90px">
        <el-form-item label="质控结论">
          <el-radio-group v-model="qcPass">
            <el-radio :value="true">质控通过</el-radio>
            <el-radio :value="false">质控退回</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="未编码的病案无法质控；质控通过是归档的前置条件之一。"
      />
      <template #footer>
        <el-button @click="qcDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="qcSubmitting" @click="handleQcSubmit">确认</el-button>
      </template>
    </el-dialog>

    <!-- 借阅弹窗 -->
    <el-dialog v-model="borrowDialogVisible" title="病案借阅" width="440px" destroy-on-close>
      <div v-if="borrowRow" class="mrc-line">病案号：{{ borrowRow.mrcNo }}｜患者：{{ borrowRow.patientName || '-' }}</div>
      <el-form label-width="110px">
        <el-form-item label="预计归还天数">
          <el-input-number
            v-model="borrowDays"
            :min="1"
            :max="90"
            controls-position="right"
            style="width: 160px"
          />
          <span class="form-tip">选填</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="borrowDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="borrowSubmitting" @click="handleBorrowSubmit">确认借阅</el-button>
      </template>
    </el-dialog>

    <!-- 首页详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="病案首页详情" size="680px" destroy-on-close>
      <div v-loading="drawerLoading">
        <template v-if="homepage">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="主诊断编码">{{ homepage.mainDiagnosisCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主诊断名称">{{ homepage.mainDiagnosisName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手术编码">{{ homepage.operationCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="费用合计">
              <span class="dep-amount">¥{{ fmtMoney(chargeSummary.total) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="其他诊断" :span="2">
              {{ otherDiagnosesText(homepage.otherDiagnoses) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="drawer-section">费用分类汇总</div>
          <el-table
            :data="chargeRows"
            border
            size="small"
            empty-text="暂无费用汇总"
          >
            <el-table-column label="费用类别" min-width="120">
              <template #default="{ row }">{{ inpFeeTypeLabel(row.feeType) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { fmtMoney } from '@/api/registration'
import { inpFeeTypeLabel } from '@/api/inp'
import {
  archiveMrc,
  archiveStatusLabel,
  archiveStatusTagType,
  ARCHIVE_STATUS_OPTIONS,
  borrowMrc,
  getHomepage,
  getMrcPage,
  mrcQcStatusLabel,
  mrcQcStatusTagType,
  MRC_QC_STATUS_OPTIONS,
  otherDiagnosesText,
  parseChargeSummary,
  qcHomepage,
  returnMrc,
  saveHomepageCode,
  searchIcd10,
  type HomepageCodePayload,
  type Icd10Item,
  type MrcHomepage,
  type MrcRecord,
} from '@/api/mrc'

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<MrcRecord[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  mrcNo: '',
  archiveStatus: undefined as number | undefined,
  qcStatus: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getMrcPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      mrcNo: query.mrcNo.trim() || undefined,
      archiveStatus: query.archiveStatus,
      qcStatus: query.qcStatus,
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
  query.mrcNo = ''
  query.archiveStatus = undefined
  query.qcStatus = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 首页编码 ----------------
const codeDialogVisible = ref(false)
const codeSubmitting = ref(false)
const codeFormRef = ref<FormInstance>()
const codeRow = ref<MrcRecord | null>(null)
const icdOptions = ref<Icd10Item[]>([])
const icdLoading = ref(false)
const codeForm = reactive({
  mainDiagnosisCode: '',
  mainDiagnosisName: '',
  otherDiagnoses: '',
  operationCode: '',
})

const codeRules: FormRules = {
  mainDiagnosisCode: [{ required: true, message: '请搜索并选择主诊断', trigger: 'change' }],
  mainDiagnosisName: [{ required: true, message: '请填写主诊断名称', trigger: 'blur' }],
}

async function searchIcd(kw: string) {
  icdLoading.value = true
  try {
    icdOptions.value = (await searchIcd10(kw)) ?? []
  } catch {
    icdOptions.value = []
  } finally {
    icdLoading.value = false
  }
}

function handleIcdChange(code: string) {
  const hit = icdOptions.value.find((i) => i.code === code)
  if (hit) codeForm.mainDiagnosisName = hit.name
}

async function openCodeDialog(row: MrcRecord) {
  codeRow.value = row
  codeForm.mainDiagnosisCode = ''
  codeForm.mainDiagnosisName = ''
  codeForm.otherDiagnoses = ''
  codeForm.operationCode = ''
  icdOptions.value = []
  codeDialogVisible.value = true
  // 预填已有首页编码
  try {
    const hp = await getHomepage(row.admissionId)
    // 乱序守卫：期间用户已切换到另一行则丢弃本次预填（防 A 病案编码写进 B 弹窗）
    if (hp && codeRow.value === row) {
      codeForm.mainDiagnosisCode = hp.mainDiagnosisCode || ''
      codeForm.mainDiagnosisName = hp.mainDiagnosisName || ''
      codeForm.otherDiagnoses = otherDiagnosesText(hp.otherDiagnoses)
      if (codeForm.otherDiagnoses === '-') codeForm.otherDiagnoses = ''
      codeForm.operationCode = hp.operationCode || ''
      if (codeForm.mainDiagnosisCode && !icdOptions.value.some((i) => i.code === codeForm.mainDiagnosisCode)) {
        icdOptions.value = [
          { code: codeForm.mainDiagnosisCode, name: codeForm.mainDiagnosisName || codeForm.mainDiagnosisCode },
        ]
      }
    }
  } catch {
    // 首页未编码等情况忽略
  }
}

async function handleCodeSubmit() {
  const valid = await codeFormRef.value?.validate().catch(() => false)
  if (!valid || !codeRow.value) return
  // 后端 otherDiagnoses 为 List<{code,name}>：把「编码 名称」文本拆为结构化条目
  const otherItems = codeForm.otherDiagnoses
    .split(/[；;\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((entry) => {
      const m = entry.match(/^([A-Za-z0-9.\-]+)\s+(.+)$/)
      return m ? { code: m[1], name: m[2].trim() } : { code: '', name: entry }
    })
  const payload: HomepageCodePayload = {
    mainDiagnosisCode: codeForm.mainDiagnosisCode.trim(),
    mainDiagnosisName: codeForm.mainDiagnosisName.trim(),
    otherDiagnoses: otherItems.length > 0 ? otherItems : undefined,
    operationCode: codeForm.operationCode.trim() || undefined,
  }
  codeSubmitting.value = true
  try {
    await saveHomepageCode(codeRow.value.admissionId, payload)
    ElMessage.success('病案首页编码保存成功')
    codeDialogVisible.value = false
  } catch {
    // 拦截器已统一提示
  } finally {
    codeSubmitting.value = false
  }
}

// ---------------- 首页质控 ----------------
const qcDialogVisible = ref(false)
const qcSubmitting = ref(false)
const qcRow = ref<MrcRecord | null>(null)
const qcPass = ref(true)

function openQcDialog(row: MrcRecord) {
  qcRow.value = row
  qcPass.value = true
  qcDialogVisible.value = true
}

async function handleQcSubmit() {
  if (!qcRow.value) return
  qcSubmitting.value = true
  try {
    await qcHomepage(qcRow.value.admissionId, qcPass.value)
    ElMessage.success(qcPass.value ? '病案首页质控通过' : '病案首页已退回')
    qcDialogVisible.value = false
    fetchList()
  } catch {
    // B6302 未编码时质控报错等已在拦截器统一提示
  } finally {
    qcSubmitting.value = false
  }
}

// ---------------- 归档 / 借阅 / 归还 ----------------
function handleArchive(row: MrcRecord) {
  ElMessageBox.confirm(
    `确认归档病案 ${row.mrcNo}？归档要求住院已结算且首页质控通过。`,
    '病案归档',
    { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' }
  )
    .then(async () => {
      await archiveMrc(row.admissionId)
      ElMessage.success('病案归档成功')
      fetchList()
    })
    .catch(() => {
      // 取消或 B6301/B6302 拦截（拦截器已提示）
    })
}

const borrowDialogVisible = ref(false)
const borrowSubmitting = ref(false)
const borrowRow = ref<MrcRecord | null>(null)
const borrowDays = ref(14)

function openBorrowDialog(row: MrcRecord) {
  borrowRow.value = row
  borrowDays.value = 14
  borrowDialogVisible.value = true
}

async function handleBorrowSubmit() {
  if (!borrowRow.value) return
  borrowSubmitting.value = true
  try {
    await borrowMrc(borrowRow.value.admissionId, borrowDays.value)
    ElMessage.success('病案借阅成功')
    borrowDialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    borrowSubmitting.value = false
  }
}

function handleReturn(row: MrcRecord) {
  ElMessageBox.confirm(`确认归还病案 ${row.mrcNo}？`, '病案归还', {
    type: 'warning',
    confirmButtonText: '确认归还',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await returnMrc(row.admissionId)
      ElMessage.success('病案已归还')
      fetchList()
    })
    .catch(() => {
      // 取消
    })
}

// ---------------- 首页详情抽屉 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const homepage = ref<MrcHomepage | null>(null)

interface ChargeRow {
  feeType: number
  amount: string | number
}

/** chargeSummary 为 JSON 字符串（{"byType":{"3":120.00},"total":320.00}） */
const chargeSummary = computed(() => parseChargeSummary(homepage.value?.chargeSummary ?? null))
const chargeRows = computed<ChargeRow[]>(() => chargeSummary.value.rows)

async function openHomepageDrawer(row: MrcRecord) {
  drawerVisible.value = true
  drawerLoading.value = true
  homepage.value = null
  try {
    homepage.value = await getHomepage(row.admissionId)
  } catch {
    homepage.value = null
  } finally {
    drawerLoading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.mrc-line {
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.code-item :deep(.el-form-item__content) {
  display: block;
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
</style>
