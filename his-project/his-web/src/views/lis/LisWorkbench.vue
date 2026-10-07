<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="就诊ID">
        <el-input-number
          v-model="query.admissionId"
          :min="1"
          :precision="0"
          controls-position="right"
          placeholder="就诊ID"
          style="width: 130px"
        />
      </el-form-item>
      <el-form-item label="患者ID">
        <el-input-number
          v-model="query.patientId"
          :min="1"
          :precision="0"
          controls-position="right"
          placeholder="患者ID"
          style="width: 130px"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in LIS_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
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
      <span class="toolbar-title">检验申请单</span>
      <el-button link type="primary" :icon="Refresh" :loading="loading" @click="fetchList">
        刷新
      </el-button>
    </div>

    <!-- 申请单表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="requestNo" label="申请单号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="orderId" label="医嘱ID" width="90" align="center" />
      <el-table-column prop="admissionId" label="就诊ID" width="90" align="center">
        <template #default="{ row }">{{ row.admissionId ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="patientId" label="患者ID" width="90" align="center" />
      <el-table-column prop="doctorName" label="开单医生" width="100" align="center" show-overflow-tooltip />
      <el-table-column prop="specimenType" label="标本类型" width="100" align="center">
        <template #default="{ row }">{{ row.specimenType || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="lisStatusTagType(row.status)">
            {{ lisStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            v-perm="'lab:specimen:collect'"
            link
            type="primary"
            @click="handleCollect(row)"
          >
            采集
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'lab:specimen:collect'"
            link
            type="primary"
            @click="handleReceive(row)"
          >
            接收
          </el-button>
          <el-button
            v-if="row.status === 30"
            v-perm="'lab:result:entry'"
            link
            type="warning"
            @click="handleMockEntry(row)"
          >
            Mock 录入+危急判定
          </el-button>
          <el-button
            v-if="row.status === 30"
            v-perm="'lab:report:publish'"
            link
            type="success"
            @click="handlePublish(row)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === 40"
            v-perm="'lab:report:query'"
            link
            type="primary"
            @click="openReport(row)"
          >
            查看报告
          </el-button>
          <span v-if="row.status === 50">-</span>
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

    <!-- 报告抽屉 -->
    <el-drawer v-model="reportVisible" title="检验报告" size="640px" destroy-on-close>
      <template v-if="reportDetail">
        <el-descriptions :column="2" border size="small" class="report-desc">
          <el-descriptions-item label="申请单号">
            {{ reportDetail.request?.requestNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="报告号">
            {{ reportDetail.report?.reportNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="就诊ID">
            {{ reportDetail.request?.admissionId ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="患者ID">
            {{ reportDetail.request?.patientId ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="标本类型">
            {{ reportDetail.request?.specimenType || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="报告时间">
            {{ reportDetail.report?.reportTime || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="互认标识" :span="2">
            <el-tag v-if="reportDetail.report?.mutualFlag === 1" size="small" type="warning">
              HR 纳入互认 {{ reportDetail.report?.mutualNote || '' }}
            </el-tag>
            <span v-else>未标记</span>
          </el-descriptions-item>
          <el-descriptions-item label="结果小结" :span="2">
            {{ reportDetail.report?.resultSummary || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="report-results-title">结果明细</div>
        <el-table :data="reportDetail.results || []" border stripe size="small">
          <el-table-column prop="itemName" label="项目" min-width="110" show-overflow-tooltip />
          <el-table-column prop="resultValue" label="结果" width="90" align="right">
            <template #default="{ row }">
              <span :class="{ 'critical-value': row.criticalFlag === 1 }">{{ row.resultValue }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="80" align="center">
            <template #default="{ row }">{{ row.unit || '-' }}</template>
          </el-table-column>
          <el-table-column prop="referenceRange" label="参考范围" width="100" align="center">
            <template #default="{ row }">{{ row.referenceRange || '-' }}</template>
          </el-table-column>
          <el-table-column label="异常" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="abnormalTagType(row.abnormalFlag)">
                {{ abnormalLabel(row.abnormalFlag) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="危急" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.criticalFlag === 1" size="small" type="danger">危急</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
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
  collectSpecimen,
  entryResults,
  getReportDetail,
  getRequestPage,
  LIS_STATUS_OPTIONS,
  lisStatusLabel,
  lisStatusTagType,
  abnormalLabel,
  abnormalTagType,
  publishReport,
  receiveSpecimen,
  type LisReportDetail,
  type LisRequest,
} from '@/api/lis'

// ---------------- 列表查询 ----------------
const loading = ref(false)
const list = ref<LisRequest[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  admissionId: undefined as number | undefined,
  patientId: undefined as number | undefined,
  status: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getRequestPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      admissionId: query.admissionId,
      patientId: query.patientId,
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
  query.admissionId = undefined
  query.patientId = undefined
  query.status = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 标本采集 / 接收 ----------------
async function handleCollect(row: LisRequest) {
  try {
    await ElMessageBox.confirm(`确认采集申请单 ${row.requestNo} 的标本？`, '标本采集', {
      type: 'warning',
      confirmButtonText: '确认采集',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const res = await collectSpecimen(row.id)
    ElMessageBox.alert(
      `标本条码号：${res?.specimenNo || '-'}（申请单号：${res?.requestNo || row.requestNo}）`,
      '采集成功',
      { type: 'success', confirmButtonText: '知道了' }
    ).catch(() => {})
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

async function handleReceive(row: LisRequest) {
  try {
    await ElMessageBox.confirm(`确认接收申请单 ${row.requestNo} 的标本？接收后进入检验中。`, '标本接收', {
      type: 'warning',
      confirmButtonText: '确认接收',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await receiveSpecimen(row.id)
    ElMessage.success('标本接收成功')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- Mock 结果录入 + 危急判定 ----------------
async function handleMockEntry(row: LisRequest) {
  try {
    await ElMessageBox.confirm(
      `将从 Mock 检验仪器取数并录入申请单 ${row.requestNo} 的结果，同时按危急阈值判定危急项。确认执行？`,
      'Mock 录入+危急判定',
      { type: 'warning', confirmButtonText: '确认录入', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const criticalCount = await entryResults({ requestId: row.id, fetch: true })
    if (criticalCount > 0) {
      ElMessage.warning(`结果录入成功，判定危急项 ${criticalCount} 项，已生成危急值待处理`)
    } else {
      ElMessage.success('结果录入成功，未见危急项')
    }
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 报告发布 ----------------
async function handlePublish(row: LisRequest) {
  try {
    await ElMessageBox.confirm(`确认发布申请单 ${row.requestNo} 的检验报告？`, '报告发布', {
      type: 'warning',
      confirmButtonText: '确认发布',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const reportNo = await publishReport(row.id)
    ElMessage.success(`报告发布成功，报告号：${reportNo}`)
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 查看报告 ----------------
const reportVisible = ref(false)
const reportDetail = ref<LisReportDetail | null>(null)

async function openReport(row: LisRequest) {
  reportDetail.value = null
  reportVisible.value = true
  try {
    reportDetail.value = await getReportDetail(row.id)
  } catch {
    // 拦截器已统一提示
  }
}

onMounted(fetchList)
</script>

<style scoped>
.report-desc {
  margin-bottom: 14px;
}

.report-results-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.critical-value {
  color: #f56c6c;
  font-weight: 700;
}
</style>
