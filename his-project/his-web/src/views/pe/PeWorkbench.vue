<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="toolbar-title">体检登记</span>
      <div>
        <el-button v-perm="'pe:package:manage'" type="primary" plain @click="pkgVisible = true">新建套餐</el-button>
        <el-button v-perm="'pe:record:create'" type="danger" @click="regVisible = true">体检登记</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="recordNo" label="登记号" min-width="140" />
      <el-table-column prop="patientId" label="患者ID" width="90" align="center" />
      <el-table-column prop="examDate" label="体检日期" width="110" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="peStatusTagType(row.status)">{{ peStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 10" v-perm="'pe:record:manage'" link type="primary" @click="handleStart(row)">开始</el-button>
          <el-button v-if="row.status === 20" v-perm="'pe:result:entry'" link type="warning" @click="openResult(row)">分项录入</el-button>
          <el-button v-if="row.status === 20" v-perm="'pe:record:manage'" link type="primary" @click="handleFinish(row)">完成</el-button>
          <el-button v-if="row.status === 30" v-perm="'pe:report:publish'" link type="success" @click="openReport(row)">总检发布</el-button>
          <el-button v-perm="'pe:record:query'" link type="info" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" background
        layout="total, sizes, prev, pager, next" @current-change="fetchList" />
    </div>

    <el-dialog v-model="regVisible" title="体检登记" width="440px" destroy-on-close>
      <el-form :model="regForm" label-width="90px">
        <el-form-item label="患者ID" required><el-input-number v-model="regForm.patientId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="套餐" required>
          <el-select v-model="regForm.packageId" style="width: 100%">
            <el-option v-for="p in packages" :key="p.id" :value="p.id" :label="`${p.packageNo} ${p.name}（¥${p.price}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="体检日期" required><el-date-picker v-model="regForm.examDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRegister">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="分项结果录入（覆盖式）" width="520px" destroy-on-close>
      <el-form :model="resultForm" label-width="90px">
        <el-form-item label="待录项目">
          <el-select v-model="resultForm.chargeItemId" style="width: 100%" placeholder="选择未录入的套餐项目">
            <el-option v-for="p in pendingItems" :key="p.chargeItemId" :value="p.chargeItemId" :label="p.itemName" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果值" required><el-input v-model="resultForm.resultValue" /></el-form-item>
        <el-form-item label="异常"><el-switch v-model="resultForm.abnormal" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="resultForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveResult">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportVisible" title="总检报告发布" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="总检结论" required><el-input v-model="reportSummary" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="handlePublish">发布</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="体检详情" size="560px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="登记号">{{ (detail.record as PeRecord)?.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ peStatusLabel((detail.record as PeRecord)?.status ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="套餐" :span="2">{{ (detail.package as PePackage)?.name || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="mt8">已录分项</div>
        <el-table :data="(detail.results as PeResultRow[]) || []" border size="small" class="mt4">
          <el-table-column prop="itemName" label="项目" min-width="120" />
          <el-table-column prop="resultValue" label="结果" min-width="100" />
          <el-table-column label="异常" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.abnormalFlag === 1" size="small" type="danger">异常</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt8">待录项目</div>
        <el-table :data="(detail.pendingItems as Array<Record<string, unknown>>) || []" border size="small" class="mt4">
          <el-table-column prop="itemName" label="项目" min-width="120" />
          <el-table-column prop="price" label="价格" width="90" align="right" />
        </el-table>
        <template v-if="(detail.report as Record<string, unknown>)?.summary">
          <div class="mt8">总检结论</div>
          <div class="mt4">{{ (detail.report as Record<string, unknown>).summary }}</div>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPackage, finishPe, getPackagePage, getRecordDetail, getRecordPage,
  peStatusLabel, peStatusTagType, publishPeReport, registerPe, savePeResult, startPe,
  type PePackage, type PeRecord,
} from '@/api/pe'

const loading = ref(false)
const list = ref<PeRecord[]>([])
const total = ref(0)
const packages = ref<PePackage[]>([])
const query = reactive({ pageNum: 1, pageSize: 10 })

async function fetchList() {
  loading.value = true
  try {
    const res = await getRecordPage({ pageNum: query.pageNum, pageSize: query.pageSize })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally { loading.value = false }
}
async function fetchPackages() {
  const res = await getPackagePage({ pageNum: 1, pageSize: 50 })
  packages.value = res.list ?? []
}

const regVisible = ref(false)
const regForm = reactive<{ patientId?: number; packageId?: number; examDate: string }>({ patientId: undefined, packageId: undefined, examDate: '' })

async function handleRegister() {
  if (!regForm.patientId || !regForm.packageId || !regForm.examDate) {
    ElMessage.warning('请完整填写登记信息')
    return
  }
  await registerPe({ patientId: regForm.patientId, packageId: regForm.packageId, examDate: regForm.examDate })
  ElMessage.success('体检登记成功')
  regVisible.value = false
  fetchList()
}

async function handleStart(row: PeRecord) {
  await startPe(row.id)
  ElMessage.success('检查已开始')
  fetchList()
}

let currentRow: PeRecord | null = null
const pendingItems = ref<Array<Record<string, unknown>>>([])
const resultVisible = ref(false)
const resultForm = reactive<{ chargeItemId?: number; resultValue: string; abnormal: boolean; note: string }>({ chargeItemId: undefined, resultValue: '', abnormal: false, note: '' })

async function openResult(row: PeRecord) {
  currentRow = row
  const detail = await getRecordDetail(row.id)
  pendingItems.value = (detail.pendingItems as Array<Record<string, unknown>>) ?? []
  resultForm.chargeItemId = undefined
  resultForm.resultValue = ''
  resultForm.abnormal = false
  resultForm.note = ''
  resultVisible.value = true
}

async function handleSaveResult() {
  if (!currentRow || !resultForm.chargeItemId || !resultForm.resultValue) {
    ElMessage.warning('请选择项目并填写结果')
    return
  }
  const item = pendingItems.value.find((p) => p.chargeItemId === resultForm.chargeItemId)
  await savePeResult(currentRow.id, {
    chargeItemId: resultForm.chargeItemId,
    itemName: String(item?.itemName ?? ''),
    resultValue: resultForm.resultValue,
    abnormalFlag: resultForm.abnormal ? 1 : 0,
    note: resultForm.note || undefined,
  })
  ElMessage.success('分项已保存')
  resultVisible.value = false
  fetchList()
}

async function handleFinish(row: PeRecord) {
  try {
    await ElMessageBox.confirm('完成前将校验分项齐全，确认完成？', '完成体检')
    await finishPe(row.id)
    ElMessage.success('体检已完成')
    fetchList()
  } catch { /* 取消 */ }
}

const reportVisible = ref(false)
const reportSummary = ref('')

function openReport(row: PeRecord) {
  currentRow = row
  reportSummary.value = ''
  reportVisible.value = true
}

async function handlePublish() {
  if (!currentRow || !reportSummary.value) {
    ElMessage.warning('请填写总检结论')
    return
  }
  await publishPeReport(currentRow.id, { summary: reportSummary.value })
  ElMessage.success('报告已发布')
  reportVisible.value = false
  fetchList()
}

const detailVisible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)

async function openDetail(row: PeRecord) {
  detail.value = await getRecordDetail(row.id)
  detailVisible.value = true
}

const pkgVisible = ref(false)

onMounted(() => { fetchList(); fetchPackages() })
</script>

<style scoped>
.mt4 { margin-top: 4px; }
.mt8 { margin-top: 8px; font-weight: 600; }
</style>
