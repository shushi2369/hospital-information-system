<template>
  <div class="page-card">
    <!-- 视图切换（一百一十轮 P5：院感确认闭环入口） -->
    <el-radio-group v-model="view" class="view-switch" @change="handleViewChange">
      <el-radio-button value="card">传染病报告卡</el-radio-button>
      <el-radio-button value="hai">院感病例</el-radio-button>
    </el-radio-group>

    <!-- ========== 传染病报告卡视图 ========== -->
    <template v-if="view === 'card'">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in PUB_CARD_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button link type="primary" :icon="Refresh" :loading="loading" @click="fetchList">
          刷新
        </el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <span class="toolbar-title">传染病报告卡</span>
      <span class="status-legend">
        闭环流程：填卡 → 上报登记（公卫科）→ 审核 → 回执登记（反馈）
      </span>
      <el-button
        v-perm="'pub:hai:confirm'"
        type="warning"
        plain
        size="small"
        class="toolbar-action"
        @click="openHaiDialog"
      >
        院感病例报告
      </el-button>
    </div>

    <!-- 传染病卡表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="cardNo" label="卡片编号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="diseaseName" label="病种" min-width="110" show-overflow-tooltip />
      <el-table-column prop="diseaseCategory" label="分类" width="90" align="center">
        <template #default="{ row }">{{ row.diseaseCategory || '-' }}</template>
      </el-table-column>
      <el-table-column prop="patientName" label="患者" width="80" align="center" />
      <el-table-column label="就诊关联" width="110" align="center">
        <template #default="{ row }">
          <span v-if="row.admissionId">住院 {{ row.admissionId }}</span>
          <span v-else-if="row.visitId">门诊 {{ row.visitId }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="diagnoseDate" label="诊断日期" width="100" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="pubCardStatusTagType(row.status)">
            {{ pubCardStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reportTime" label="上报时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reportTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="回执" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.receiptNo">{{ row.receiptNo }}<br />{{ row.receiptTime }}</template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            v-perm="'pub:card:report'"
            link
            type="warning"
            @click="handleReport(row)"
          >
            上报
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'pub:card:report'"
            link
            type="primary"
            @click="handleApprove(row)"
          >
            审核
          </el-button>
          <el-button
            v-if="row.status === 30"
            v-perm="'pub:card:receipt'"
            link
            type="success"
            @click="openReceiptDialog(row)"
          >
            回执登记
          </el-button>
          <span v-if="row.status === 40">-</span>
        </template>
      </el-table-column>
    </el-table>

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
    </template>

    <!-- ========== 院感病例视图（一百一十轮 P5） ========== -->
    <template v-else>
      <el-form class="search-bar" inline @submit.prevent>
        <el-form-item label="状态">
          <el-radio-group v-model="haiQuery.status" @change="handleHaiSearch">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button v-for="opt in PUB_HAI_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleHaiSearch">查询</el-button>
          <el-button link type="primary" :icon="Refresh" :loading="haiLoading" @click="fetchHaiList">
            刷新
          </el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <span class="toolbar-title">院感病例</span>
        <span class="status-legend">闭环流程：报告 → 确认（整改中）→ 整改完成（闭环）</span>
        <el-button
          v-perm="'pub:hai:confirm'"
          type="warning"
          plain
          size="small"
          class="toolbar-action"
          @click="openHaiDialog"
        >
          院感病例报告
        </el-button>
      </div>

      <el-table v-loading="haiLoading" :data="haiList" border stripe size="small">
        <el-table-column prop="caseNo" label="病例编号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" width="90" align="center">
          <template #default="{ row }">{{ row.patientName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="admissionId" label="住院ID" width="85" align="center" />
        <el-table-column label="感染类型" width="100" align="center">
          <template #default="{ row }">{{ haiTypeLabel(row.infectionType) }}</template>
        </el-table-column>
        <el-table-column prop="infectionSite" label="感染部位" min-width="120" show-overflow-tooltip />
        <el-table-column prop="diagnoseDate" label="诊断日期" width="105" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="pubHaiStatusTagType(row.status)">
              {{ pubHaiStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认记录" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.confirmNote">{{ row.confirmNote }}<br />{{ row.confirmTime }}</template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 10"
              v-perm="'pub:hai:confirm'"
              link
              type="primary"
              @click="handleHaiConfirm(row, 20)"
            >
              确认
            </el-button>
            <el-button
              v-else-if="row.status === 20"
              v-perm="'pub:hai:confirm'"
              link
              type="success"
              @click="handleHaiConfirm(row, 30)"
            >
              整改完成
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="haiQuery.pageNum"
          v-model:page-size="haiQuery.pageSize"
          :total="haiTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleHaiSizeChange"
          @current-change="fetchHaiList"
        />
      </div>
    </template>

    <!-- 回执登记弹窗（30 → 40） -->
    <el-dialog v-model="receiptDialogVisible" title="疾控回执登记" width="480px" destroy-on-close append-to-body>
      <div v-if="receiptRow" class="dialog-line">{{ receiptRow.cardNo }}｜{{ receiptRow.diseaseName }}</div>
      <el-form label-width="80px">
        <el-form-item label="回执号" required>
          <el-input v-model="receiptNo" maxlength="50" placeholder="疾控反馈的回执编号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiptSubmitting" @click="handleReceiptSubmit">
          登记回执
        </el-button>
      </template>
    </el-dialog>

    <!-- 院感病例报告弹窗 -->
    <el-dialog v-model="haiDialogVisible" title="院感病例报告" width="520px" destroy-on-close append-to-body>
      <el-form label-width="90px">
        <el-form-item label="住院患者" required>
          <!-- 一百一十轮 P3：裸 ID 手输改在院住院单下拉，选中自动带出患者（admission.patientId） -->
          <el-select
            v-model="haiForm.admissionId"
            filterable
            style="width: 100%"
            placeholder="选择在院患者（自动带出患者ID）"
            :loading="admLoading"
            @change="onAdmissionPicked"
          >
            <el-option
              v-for="a in admOptions"
              :key="a.id"
              :value="a.id"
              :label="`${a.admissionNo}｜${a.patientName || '患者' + a.patientId}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="患者ID">
          <span class="hai-patient-id">{{ haiForm.patientId || '-' }}</span>
        </el-form-item>
        <el-form-item label="感染类型" required>
          <el-select v-model="haiForm.infectionType" style="width: 100%">
            <el-option v-for="t in HAI_TYPE_OPTIONS" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="感染部位" required>
          <el-input v-model="haiForm.infectionSite" maxlength="64" placeholder="如：下呼吸道、手术切口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="haiDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="haiSubmitting" @click="handleHaiSubmit">提交报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  PUB_CARD_STATUS_OPTIONS,
  PUB_HAI_STATUS_OPTIONS,
  approvePubCard,
  confirmPubHai,
  getPubCardPage,
  getPubHaiPage,
  pubCardStatusLabel,
  pubCardStatusTagType,
  pubHaiStatusLabel,
  pubHaiStatusTagType,
  receiptPubCard,
  reportHaiCase,
  reportPubCard,
  type PubCard,
  type PubHaiCase,
} from '@/api/pub'
import { getAdmissionPage, type Admission } from '@/api/inp'

const HAI_TYPE_OPTIONS = [
  { value: 1, label: '呼吸道' },
  { value: 2, label: '导管相关' },
  { value: 3, label: '切口感染' },
  { value: 4, label: '胃肠道' },
  { value: 5, label: '其他' },
]

function haiTypeLabel(t: number): string {
  return HAI_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? String(t)
}

// ---------------- 视图切换（传染病卡 / 院感病例） ----------------
const view = ref<'card' | 'hai'>('card')

function handleViewChange() {
  if (view.value === 'hai') fetchHaiList()
}

// ---------------- 院感病例列表与确认闭环（一百一十轮 P5） ----------------
const haiLoading = ref(false)
const haiList = ref<PubHaiCase[]>([])
const haiTotal = ref(0)
const haiQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  status: undefined as number | undefined,
})

async function fetchHaiList() {
  haiLoading.value = true
  try {
    const res = await getPubHaiPage({
      pageNum: haiQuery.pageNum,
      pageSize: haiQuery.pageSize,
      status: haiQuery.status,
    })
    haiList.value = res.list ?? []
    haiTotal.value = res.total ?? 0
  } finally {
    haiLoading.value = false
  }
}

function handleHaiSearch() {
  haiQuery.pageNum = 1
  fetchHaiList()
}

function handleHaiSizeChange() {
  haiQuery.pageNum = 1
  fetchHaiList()
}

/**
 * 过滤 tab 下操作成功后不重置页码（一百零九轮 #A4 同款）：留在当前页刷新；
 * 若当前页被推进掏空则向前回退一页（避免"操作成功后列表空白"）。
 */
async function refreshHaiKeepPage() {
  await fetchHaiList()
  if (!haiList.value.length && haiQuery.pageNum > 1) {
    haiQuery.pageNum -= 1
    await fetchHaiList()
  }
}

async function handleHaiConfirm(row: PubHaiCase, targetStatus: number) {
  const action = targetStatus === 20 ? '确认' : '整改完成'
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `${action}院感病例 ${row.caseNo}（${row.patientName || row.patientId}）？可填写备注。`,
      `院感${action}`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消', inputPlaceholder: '备注（选填）' }
    )
    note = (value ?? '').trim()
  } catch {
    return
  }
  try {
    await confirmPubHai(row.id, targetStatus, note || undefined)
    ElMessage.success(`院感病例已${action}`)
    await refreshHaiKeepPage()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 列表查询 ----------------
const loading = ref(false)
const list = ref<PubCard[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getPubCardPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
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

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 上报登记（10 → 20，PUB-02） ----------------
async function handleReport(row: PubCard) {
  try {
    await ElMessageBox.confirm(
      `确认登记上报传染病卡 ${row.cardNo}（${row.diseaseName}）至疾控？`,
      '传染病上报',
      { type: 'warning', confirmButtonText: '确认上报', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await reportPubCard(row.id)
    ElMessage.success('上报登记成功，待公卫科审核')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 审核（20 → 30） ----------------
async function handleApprove(row: PubCard) {
  try {
    await ElMessageBox.confirm(
      `审核通过传染病卡 ${row.cardNo}（${row.diseaseName}）？审核后等待疾控回执。`,
      '卡片审核',
      { type: 'warning', confirmButtonText: '审核通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await approvePubCard(row.id)
    ElMessage.success('审核通过，等待疾控回执')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 回执登记（30 → 40） ----------------
const receiptDialogVisible = ref(false)
const receiptSubmitting = ref(false)
const receiptRow = ref<PubCard | null>(null)
const receiptNo = ref('')

function openReceiptDialog(row: PubCard) {
  receiptRow.value = row
  receiptNo.value = ''
  receiptDialogVisible.value = true
}

async function handleReceiptSubmit() {
  if (!receiptRow.value) return
  if (!receiptNo.value.trim()) {
    ElMessage.warning('请填写疾控回执号')
    return
  }
  receiptSubmitting.value = true
  try {
    await receiptPubCard(receiptRow.value.id, receiptNo.value.trim())
    ElMessage.success('回执登记完成，卡片闭环')
    receiptDialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    receiptSubmitting.value = false
  }
}

// ---------------- 院感病例报告 ----------------
// 诊断日期由服务端取提交日（PubService.haiReport 覆盖），表单不再收集
const haiDialogVisible = ref(false)
const haiSubmitting = ref(false)
const haiForm = reactive({
  admissionId: undefined as number | undefined,
  patientId: undefined as number | undefined,
  infectionType: undefined as number | undefined,
  infectionSite: '',
})

// 在院住院单选项（一百一十轮 P3：裸 ID 手输改下拉）
const admOptions = ref<Admission[]>([])
const admLoading = ref(false)

async function loadAdmOptions() {
  admLoading.value = true
  try {
    const res = await getAdmissionPage({ pageNum: 1, pageSize: 200, status: 10 })
    admOptions.value = res.list ?? []
  } finally {
    admLoading.value = false
  }
}

function onAdmissionPicked(admissionId: number) {
  const adm = admOptions.value.find((a) => a.id === admissionId)
  haiForm.patientId = adm?.patientId
}

async function openHaiDialog() {
  resetHaiForm()
  haiDialogVisible.value = true
  await loadAdmOptions()
}

function resetHaiForm() {
  haiForm.admissionId = undefined
  haiForm.patientId = undefined
  haiForm.infectionType = undefined
  haiForm.infectionSite = ''
}

async function handleHaiSubmit() {
  if (!haiForm.admissionId || !haiForm.patientId || !haiForm.infectionType || !haiForm.infectionSite.trim()) {
    ElMessage.warning('请完整填写院感病例信息')
    return
  }
  haiSubmitting.value = true
  try {
    const caseNo = await reportHaiCase({
      admissionId: haiForm.admissionId,
      patientId: haiForm.patientId,
      infectionType: haiForm.infectionType,
      infectionSite: haiForm.infectionSite.trim(),
    })
    ElMessage.success(`院感病例已报告：${caseNo}`)
    haiDialogVisible.value = false
    resetHaiForm()
    if (view.value === 'hai') await refreshHaiKeepPage()
  } catch {
    // 拦截器已统一提示
  } finally {
    haiSubmitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.view-switch {
  margin-bottom: 12px;
}

.hai-patient-id {
  color: #606266;
  font-size: 13px;
}

.status-legend {
  font-size: 12px;
  color: #909399;
}

.toolbar-action {
  margin-left: auto;
}

.dialog-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}

.num-input {
  width: 100%;
}
</style>
