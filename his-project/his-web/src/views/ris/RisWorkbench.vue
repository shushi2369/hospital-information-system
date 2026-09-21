<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="就诊ID">
        <el-input-number v-model="query.admissionId" :min="1" :precision="0" controls-position="right" style="width: 130px" />
      </el-form-item>
      <el-form-item label="检查类别">
        <el-select v-model="query.modality" clearable placeholder="全部" style="width: 110px">
          <el-option v-for="opt in MODALITY_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in RIS_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <span class="toolbar-title">检查申请单（护士执行检查医嘱自动生成）</span>
      <el-button link type="primary" :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="requestNo" label="申请号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="admissionId" label="就诊ID" width="90" align="center" />
      <el-table-column prop="patientId" label="患者ID" width="90" align="center" />
      <el-table-column label="检查类别" width="90" align="center">
        <template #default="{ row }">{{ modalityLabel(row.modality) }}</template>
      </el-table-column>
      <el-table-column label="急查" width="70" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.urgency === 2" size="small" type="danger">急</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="risStatusTagType(row.status)">{{ risStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 10" v-perm="'ris:appt:manage'" link type="primary" @click="openAppoint(row)">预约</el-button>
          <el-button v-if="row.status === 20" v-perm="'ris:exam:execute'" link type="primary" @click="handleStart(row)">开始检查</el-button>
          <el-button v-if="row.status === 30" v-perm="'ris:exam:execute'" link type="warning" @click="handleMockArchive(row)">Mock 影像</el-button>
          <el-button v-if="row.status === 30" v-perm="'ris:exam:execute'" link type="primary" @click="handleFinish(row)">完成检查</el-button>
          <el-button v-if="row.status === 30" v-perm="'ris:report:write'" link type="warning" @click="openWrite(row)">书写报告</el-button>
          <el-button v-if="row.status === 30" v-perm="'ris:report:review'" link type="success" @click="handleReview(row)">审核发布</el-button>
          <el-button v-if="row.status === 40" v-perm="'ris:report:query'" link type="primary" @click="openReport(row)">查看报告</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" background
        layout="total, sizes, prev, pager, next" @size-change="handleSizeChange" @current-change="fetchList" />
    </div>

    <!-- 预约 -->
    <el-dialog v-model="appointVisible" title="检查预约" width="460px" destroy-on-close>
      <el-form :model="appointForm" label-width="90px">
        <el-form-item label="设备" required>
          <el-select v-model="appointForm.deviceId" style="width: 100%">
            <el-option v-for="d in devices.filter((x) => x.modality === currentRow?.modality)" :key="d.id" :value="d.id" :label="`${d.deviceNo} ${d.deviceName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约时间" required>
          <el-date-picker v-model="appointForm.apptTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appointVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAppoint">提交</el-button>
      </template>
    </el-dialog>

    <!-- 书写报告 -->
    <el-dialog v-model="writeVisible" title="书写报告" width="620px" destroy-on-close>
      <el-alert v-if="existingReport && existingReport.status === 30" type="warning" :closable="false" class="mb8"
        title="该报告曾被驳回，可修改后重新提交" />
      <el-form :model="writeForm" label-width="90px">
        <el-form-item label="影像所见" required>
          <el-input v-model="writeForm.finding" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="诊断意见" required>
          <el-input v-model="writeForm.conclusion" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="危急征象">
          <el-input v-model="writeForm.criticalSign" placeholder="命中危急征象时填写（如 主动脉夹层征象），将推送危急值" />
        </el-form-item>
        <el-form-item label="纳入互认">
          <el-switch v-model="writeForm.mutual" />
          <span class="mutual-tip">标记后医生站展示 HR 互认标识</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="writeVisible = false">取消</el-button>
        <el-button type="primary" @click="handleWrite">保存</el-button>
      </template>
    </el-dialog>

    <!-- 报告抽屉 -->
    <el-drawer v-model="reportVisible" title="检查报告" size="600px" destroy-on-close>
      <template v-if="reportDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请号">{{ (reportDetail.request as RisRequest)?.requestNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报告号">{{ (reportDetail.report as RisReport)?.reportNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检查类别">{{ modalityLabel((reportDetail.request as RisRequest)?.modality ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="影像张数">{{ (reportDetail.image as Record<string, unknown>)?.imageCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="影像所见" :span="2">{{ (reportDetail.report as RisReport)?.finding || '-' }}</el-descriptions-item>
          <el-descriptions-item label="诊断意见" :span="2">{{ (reportDetail.report as RisReport)?.conclusion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="互认标识" :span="2">
            <el-tag v-if="(reportDetail.report as RisReport)?.mutualFlag === 1" size="small" type="warning">
              HR 纳入互认 {{ (reportDetail.report as RisReport)?.mutualNote || '' }}
            </el-tag>
            <span v-else>未标记</span>
          </el-descriptions-item>
          <el-descriptions-item label="危急征象" :span="2">
            <el-tag v-if="(reportDetail.report as RisReport)?.criticalFlag === 1" size="small" type="danger">
              {{ (reportDetail.report as RisReport)?.criticalSign }}
            </el-tag>
            <span v-else>无</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="报告加载中或不存在" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  appointExam,
  archiveImages,
  finishExam,
  getRisDetail,
  getRisDevices,
  getRisRequestPage,
  modalityLabel,
  reviewReport,
  RIS_STATUS_OPTIONS,
  risStatusLabel,
  risStatusTagType,
  startExam,
  writeReport,
  type RisDevice,
  type RisReport,
  type RisRequest,
} from '@/api/ris'

const MODALITY_OPTIONS = [
  { value: 1, label: 'DR' },
  { value: 2, label: 'CT' },
  { value: 3, label: 'MR' },
]

const loading = ref(false)
const list = ref<RisRequest[]>([])
const total = ref(0)
const devices = ref<RisDevice[]>([])
const query = reactive({ pageNum: 1, pageSize: 10, admissionId: undefined as number | undefined, modality: undefined as number | undefined, status: undefined as number | undefined })

async function fetchList() {
  loading.value = true
  try {
    const res = await getRisRequestPage({ pageNum: query.pageNum, pageSize: query.pageSize, admissionId: query.admissionId, modality: query.modality, status: query.status })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { query.pageNum = 1; fetchList() }
function handleReset() { query.admissionId = undefined; query.modality = undefined; query.status = undefined; handleSearch() }
function handleSizeChange() { query.pageNum = 1; fetchList() }

const appointVisible = ref(false)
const appointForm = reactive<{ deviceId: undefined | number; apptTime: string }>({ deviceId: undefined, apptTime: '' })
let currentRow: RisRequest | null = null

async function openAppoint(row: RisRequest) {
  currentRow = row
  if (!devices.value.length) {
    devices.value = await getRisDevices()
  }
  appointForm.deviceId = undefined
  appointForm.apptTime = ''
  appointVisible.value = true
}

async function handleAppoint() {
  if (!currentRow || !appointForm.deviceId || !appointForm.apptTime) {
    ElMessage.warning('请选择设备与预约时间')
    return
  }
  await appointExam(currentRow.id, { deviceId: appointForm.deviceId, apptTime: appointForm.apptTime })
  ElMessage.success('预约成功')
  appointVisible.value = false
  fetchList()
}

async function handleStart(row: RisRequest) {
  await startExam(row.id)
  ElMessage.success('检查已开始')
  fetchList()
}

async function handleMockArchive(row: RisRequest) {
  const studyNo = await archiveImages(row.id, { fetch: true })
  ElMessage.success(`影像已归档（${studyNo}），请到报告中查看 Mock 所见`)
  fetchList()
}

async function handleFinish(row: RisRequest) {
  await finishExam(row.id)
  ElMessage.success('检查已完成，可书写报告')
  fetchList()
}

const writeVisible = ref(false)
const writeForm = reactive({ finding: '', conclusion: '', criticalSign: '', mutual: false })
const existingReport = ref<RisReport | null>(null)

async function openWrite(row: RisRequest) {
  currentRow = row
  const detail = await getRisDetail(row.id)
  existingReport.value = (detail.report as RisReport) ?? null
  writeForm.finding = (detail.image as Record<string, unknown>)?.impressionText as string || ''
  writeForm.conclusion = existingReport.value?.conclusion ?? ''
  writeForm.criticalSign = existingReport.value?.criticalSign ?? ''
  writeForm.mutual = existingReport.value?.mutualFlag === 1
  writeVisible.value = true
}

async function handleWrite() {
  if (!currentRow || !writeForm.finding || !writeForm.conclusion) {
    ElMessage.warning('请填写影像所见与诊断意见')
    return
  }
  await writeReport({
    requestId: currentRow.id,
    finding: writeForm.finding,
    conclusion: writeForm.conclusion,
    criticalSign: writeForm.criticalSign || undefined,
    mutualFlag: writeForm.mutual ? 1 : 0,
    mutualNote: writeForm.mutual ? 'HR 互认' : undefined,
  })
  ElMessage.success('报告已保存（书写中，待审核发布）')
  writeVisible.value = false
  fetchList()
}

async function handleReview(row: RisRequest) {
  const detail = await getRisDetail(row.id)
  const report = detail.report as RisReport | null
  if (!report) {
    ElMessage.warning('报告尚未书写')
    return
  }
  try {
    await ElMessageBox.confirm('审核通过将发布报告（危急征象将推送危急值，且不得自审自签），确认？', '报告审核')
    await reviewReport(report.id, { approved: true })
    ElMessage.success('报告已发布')
    fetchList()
  } catch {
    /* 用户取消 */
  }
}

const reportVisible = ref(false)
const reportDetail = ref<Record<string, unknown> | null>(null)

async function openReport(row: RisRequest) {
  reportDetail.value = await getRisDetail(row.id)
  reportVisible.value = true
}

onMounted(fetchList)
</script>

<style scoped>
.mb8 { margin-bottom: 8px; }
.mutual-tip { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
