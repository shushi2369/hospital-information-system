<template>
  <div class="page-card">
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
        @click="haiDialogVisible = true"
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
        <el-form-item label="住院ID" required>
          <el-input-number v-model="haiForm.admissionId" :min="1" :controls="false" class="num-input" />
        </el-form-item>
        <el-form-item label="患者ID" required>
          <el-input-number v-model="haiForm.patientId" :min="1" :controls="false" class="num-input" />
        </el-form-item>
        <el-form-item label="感染类型" required>
          <el-select v-model="haiForm.infectionType" style="width: 100%">
            <el-option label="呼吸道" :value="1" />
            <el-option label="消化道" :value="2" />
            <el-option label="切口感染" :value="3" />
            <el-option label="血液相关" :value="4" />
            <el-option label="泌尿道" :value="5" />
            <el-option label="其他" :value="9" />
          </el-select>
        </el-form-item>
        <el-form-item label="感染部位" required>
          <el-input v-model="haiForm.infectionSite" maxlength="100" placeholder="如：下呼吸道、手术切口" />
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
  approvePubCard,
  getPubCardPage,
  pubCardStatusLabel,
  pubCardStatusTagType,
  receiptPubCard,
  reportHaiCase,
  reportPubCard,
  type PubCard,
} from '@/api/pub'

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
  } catch {
    // 拦截器已统一提示
  } finally {
    haiSubmitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
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
