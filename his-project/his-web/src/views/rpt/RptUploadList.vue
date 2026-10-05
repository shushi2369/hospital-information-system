<template>
  <div class="page-card">
    <!-- 统计头 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :span="6" v-for="s in statCards" :key="s.label">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">{{ s.label }}</div>
          <div class="kpi-value">{{ s.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 过滤 -->
    <el-form inline @submit.prevent>
      <el-form-item label="业务类型">
        <el-select v-model="query.bizType" clearable placeholder="全部" style="width: 150px" @change="fetchList">
          <el-option label="传染病报告卡" :value="1" />
          <el-option label="病案归档" :value="2" />
          <el-option label="出院结算" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px" @change="fetchList">
          <el-option label="待上报" :value="10" />
          <el-option label="已上报" :value="20" />
          <el-option label="失败" :value="30" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchAll">查询</el-button>
        <el-button @click="deliverNow" v-perm="'rpt:upload:retry'">立即投递</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="uploadNo" label="上报号" min-width="150" />
      <el-table-column label="业务类型" width="120" align="center">
        <template #default="{ row }">{{ rptBizTypeLabel(row.bizType) }}</template>
      </el-table-column>
      <el-table-column prop="bizNo" label="业务单号" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="rptStatusTag(row.status)">{{ rptStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="receiptNo" label="受理号" min-width="150">
        <template #default="{ row }">{{ row.receiptNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试" width="70" align="center" />
      <el-table-column prop="lastError" label="最后错误" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.lastError || '-' }}</template>
      </el-table-column>
      <el-table-column prop="uploadedAt" label="上报时间" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.uploadedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'rpt:upload:query'" link type="info" @click="openDetail(row)">报文</el-button>
          <el-button
            v-if="row.status !== 20"
            v-perm="'rpt:upload:retry'"
            link
            type="warning"
            :loading="retrying === row.id"
            @click="handleRetry(row)"
          >
            重报
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top: 10px">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="fetchList"
      />
    </div>

    <!-- 报文预览 -->
    <el-drawer v-model="detailVisible" title="标准化报文" size="560px" destroy-on-close>
      <el-input v-if="detail" :model-value="pretty(detail.payload)" type="textarea" :rows="24" readonly />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  deliverRptBatch,
  getRptStats,
  getRptUploadDetail,
  getRptUploads,
  retryRptUpload,
  rptBizTypeLabel,
  rptStatusLabel,
  rptStatusTag,
  type RptUpload,
} from '@/api/rpt'

const statCards = computed(() => [
  { label: '上报总数', value: stats.value.total },
  { label: '已上报', value: stats.value.uploaded },
  { label: '待上报', value: stats.value.pending },
  { label: '失败', value: stats.value.failed },
])

const stats = ref<Record<string, number>>({})
const loading = ref(false)
const list = ref<RptUpload[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  bizType: undefined as number | undefined,
  status: undefined as number | undefined,
})
const retrying = ref<number | null>(null)
const detailVisible = ref(false)
const detail = ref<RptUpload | null>(null)

const pretty = (payload: string) => {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

async function fetchStats() {
  stats.value = await getRptStats()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getRptUploads({ ...query })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

async function fetchAll() {
  await Promise.all([fetchStats(), fetchList()])
}

async function deliverNow() {
  await deliverRptBatch()
  ElMessage.success('投递完成')
  await fetchAll()
}

async function handleRetry(row: RptUpload) {
  retrying.value = row.id
  try {
    await retryRptUpload(row.id)
    ElMessage.success('已重报')
    await fetchAll()
  } finally {
    retrying.value = null
  }
}

function openDetail(row: RptUpload) {
  detail.value = row
  detailVisible.value = true
}

onMounted(fetchAll)
</script>

<style scoped>
.kpi-row {
  margin-bottom: 12px;
}
.kpi-card {
  text-align: center;
}
.kpi-label {
  color: #909399;
  font-size: 13px;
}
.kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: #e8a040;
}
</style>
