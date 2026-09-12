<template>
  <div class="report-page">
    <!-- 顶部：日期范围 + 查询 -->
    <div class="page-card query-bar">
      <span class="toolbar-title">统计报表</span>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
        style="width: 260px"
      />
      <el-button type="primary" :icon="Search" :loading="anyLoading" @click="handleQuery">
        查询
      </el-button>
      <span class="query-tip">
        统计范围：{{ dateRange ? `${dateRange[0]} 至 ${dateRange[1]}` : '-' }}；药品库存为实时数据，不受日期影响
      </span>
    </div>

    <div class="page-card">
      <el-tabs v-model="activeTab">
        <!-- ① 挂号量 -->
        <el-tab-pane label="挂号量" name="reg">
          <div class="tab-section-title">挂号量日趋势</div>
          <el-table
            v-loading="regLoading"
            :data="regDaily"
            border
            stripe
            size="small"
            max-height="380"
            empty-text="所选日期范围内暂无挂号数据"
          >
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="date" label="日期" min-width="140" align="center" />
            <el-table-column prop="count" label="挂号量（人次）" min-width="140" align="right" />
          </el-table>

          <div class="tab-section-title">科室挂号排名（降序）</div>
          <el-table
            v-loading="deptLoading"
            :data="deptRank"
            border
            stripe
            size="small"
            max-height="380"
            empty-text="所选日期范围内暂无科室挂号数据"
          >
            <el-table-column label="排名" width="70" align="center">
              <template #default="{ $index }">
                <el-tag v-if="$index < 3" size="small" type="danger">{{ $index + 1 }}</el-tag>
                <span v-else>{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="科室" min-width="180" show-overflow-tooltip />
            <el-table-column prop="count" label="挂号量（人次）" min-width="140" align="right" />
          </el-table>
        </el-tab-pane>

        <!-- ② 门诊量 -->
        <el-tab-pane label="门诊量" name="visit">
          <div class="tab-section-title">门诊量日趋势（按就诊完成时间）</div>
          <el-table
            v-loading="visitLoading"
            :data="visitDaily"
            border
            stripe
            size="small"
            max-height="520"
            empty-text="所选日期范围内暂无完成就诊数据"
          >
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="date" label="日期" min-width="140" align="center" />
            <el-table-column prop="count" label="门诊量（人次）" min-width="140" align="right" />
          </el-table>
        </el-tab-pane>

        <!-- ③ 收入汇总 -->
        <el-tab-pane label="收入汇总" name="revenue">
          <div class="tab-section-title">收入日趋势</div>
          <el-table
            v-loading="revLoading"
            :data="revDaily"
            border
            stripe
            size="small"
            max-height="380"
            empty-text="所选日期范围内暂无收入数据"
          >
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="date" label="日期" min-width="130" align="center" />
            <el-table-column label="收费金额（元）" min-width="130" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.chargeAmount) }}</template>
            </el-table-column>
            <el-table-column label="退费金额（元）" min-width="130" align="right">
              <template #default="{ row }">
                <span class="refund-amount">¥{{ fmtMoney(row.refundAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="净额（元）" min-width="130" align="right">
              <template #default="{ row }">
                <span class="net-amount">¥{{ fmtMoney(row.netAmount) }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="tab-section-title">费用类别分布</div>
          <el-table
            v-loading="distLoading"
            :data="distList"
            border
            stripe
            size="small"
            max-height="380"
            empty-text="所选日期范围内暂无收入分布数据"
          >
            <el-table-column label="费用类别" min-width="140" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="feeTypeTagType(row.feeType)">
                  {{ feeTypeLabel(row.feeType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="金额（元）" min-width="140" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="占比" min-width="120" align="right">
              <template #default="{ row }">{{ pct(row.amount, distTotal) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ④ 收入明细 -->
        <el-tab-pane label="收入明细" name="detail">
          <div class="detail-head">
            <span class="tab-section-title no-margin">收入明细（收费 / 退费流水）</span>
            <el-button
              v-perm="'report:query'"
              type="success"
              :icon="Download"
              :loading="exporting"
              @click="handleExport"
            >
              导出 CSV
            </el-button>
          </div>
          <el-table v-loading="detailLoading" :data="detailList" border stripe size="small">
            <el-table-column label="时间" width="160" align="center">
              <template #default="{ row }">{{ row.time || '-' }}</template>
            </el-table-column>
            <el-table-column prop="docNo" label="单号" min-width="150" show-overflow-tooltip />
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="revenueTypeTagType(row.type)">
                  {{ revenueTypeLabel(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="患者" min-width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ row.patientName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="itemName" label="项目" min-width="160" show-overflow-tooltip />
            <el-table-column label="类别" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="feeTypeTagType(row.feeType)">
                  {{ feeTypeLabel(row.feeType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="70" align="center" />
            <el-table-column label="单价" width="95" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="detailQuery.pageNum"
              v-model:page-size="detailQuery.pageSize"
              :total="detailTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="handleDetailSizeChange"
              @current-change="fetchRevenueDetail"
            />
          </div>
        </el-tab-pane>

        <!-- ⑤ 药品库存 -->
        <el-tab-pane label="药品库存" name="drug" lazy>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="drug-alert"
            title="红色行为库存预警药品（可用总量已低于或等于预警下限），请及时入库补货。"
          />
          <el-table
            v-loading="drugLoading"
            :data="drugList"
            border
            stripe
            size="small"
            :row-class-name="drugRowClass"
            empty-text="暂无药品库存数据"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-wrap">
                  <div class="expand-title">批次明细</div>
                  <el-table :data="row.batches" border size="small" empty-text="暂无批次">
                    <el-table-column
                      prop="batchNo"
                      label="批号"
                      min-width="140"
                      show-overflow-tooltip
                    />
                    <el-table-column label="有效期至" width="120" align="center">
                      <template #default="{ row: b }">{{ b.expiryDate || '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="quantity" label="当前库存" width="110" align="right" />
                    <el-table-column label="状态" width="90" align="center">
                      <template #default="{ row: b }">
                        <el-tag size="small" :type="batchStatusTagType(b.status)">
                          {{ batchStatusLabel(b.status) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="drugName" label="药品" min-width="180" show-overflow-tooltip />
            <el-table-column prop="drugId" label="药品ID" width="90" align="center" />
            <el-table-column label="可用总量" width="110" align="right">
              <template #default="{ row }">
                <span :class="{ 'warn-qty': row.warning }">{{ row.totalQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="stockWarningQty" label="预警下限" width="100" align="right" />
            <el-table-column label="批次数" width="80" align="center">
              <template #default="{ row }">{{ (row.batches || []).length }}</template>
            </el-table-column>
            <el-table-column label="库存状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.warning" size="small" type="danger">库存预警</el-tag>
                <el-tag v-else size="small" type="success">正常</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Search } from '@element-plus/icons-vue'
import {
  batchStatusLabel,
  batchStatusTagType,
  exportRevenueDetail,
  feeTypeLabel,
  feeTypeTagType,
  fmtMoney,
  getDrugInventory,
  getRegistrationByDept,
  getRegistrationDaily,
  getRevenueDaily,
  getRevenueDetailPage,
  getRevenueDistribution,
  getVisitDaily,
  revenueTypeLabel,
  revenueTypeTagType,
  todayStr,
  type DailyCountPoint,
  type DailyRevenuePoint,
  type DeptRegStat,
  type DrugInventoryRow,
  type FeeTypeAmount,
  type RevenueDetailRow,
} from '@/api/report'
import { toList } from '@/api/request'

/** 本月 1 号（yyyy-MM-dd） */
function monthFirstDayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}-01`
}

// ---------------- 顶部查询条件 ----------------
const activeTab = ref('reg')
const dateRange = ref<[string, string]>([monthFirstDayStr(), todayStr()])

// ---------------- ① 挂号量 ----------------
const regLoading = ref(false)
const regDaily = ref<DailyCountPoint[]>([])
const deptLoading = ref(false)
const deptRank = ref<DeptRegStat[]>([])

async function fetchRegDaily() {
  const [startDate, endDate] = dateRange.value
  regLoading.value = true
  try {
    regDaily.value = toList<DailyCountPoint>(await getRegistrationDaily(startDate, endDate))
  } finally {
    regLoading.value = false
  }
}

async function fetchDeptRank() {
  const [startDate, endDate] = dateRange.value
  deptLoading.value = true
  try {
    deptRank.value = toList<DeptRegStat>(await getRegistrationByDept(startDate, endDate))
  } finally {
    deptLoading.value = false
  }
}

// ---------------- ② 门诊量 ----------------
const visitLoading = ref(false)
const visitDaily = ref<DailyCountPoint[]>([])

async function fetchVisitDaily() {
  const [startDate, endDate] = dateRange.value
  visitLoading.value = true
  try {
    visitDaily.value = toList<DailyCountPoint>(await getVisitDaily(startDate, endDate))
  } finally {
    visitLoading.value = false
  }
}

// ---------------- ③ 收入汇总 ----------------
const revLoading = ref(false)
const revDaily = ref<DailyRevenuePoint[]>([])
const distLoading = ref(false)
const distList = ref<FeeTypeAmount[]>([])
const distTotal = computed(() =>
  distList.value.reduce((sum, r) => sum + (Number(r.amount) || 0), 0)
)

/** 金额占比（合计为 0 或非法时显示 -） */
function pct(amount: string | number, total: number): string {
  const n = Number(amount)
  if (!Number.isFinite(n) || total <= 0) return '-'
  return `${((n / total) * 100).toFixed(2)}%`
}

async function fetchRevenueDaily() {
  const [startDate, endDate] = dateRange.value
  revLoading.value = true
  try {
    revDaily.value = toList<DailyRevenuePoint>(await getRevenueDaily(startDate, endDate))
  } finally {
    revLoading.value = false
  }
}

async function fetchRevenueDistribution() {
  const [startDate, endDate] = dateRange.value
  distLoading.value = true
  try {
    distList.value = toList<FeeTypeAmount>(await getRevenueDistribution(startDate, endDate))
  } finally {
    distLoading.value = false
  }
}

// ---------------- ④ 收入明细 ----------------
const detailLoading = ref(false)
const detailList = ref<RevenueDetailRow[]>([])
const detailTotal = ref(0)
const exporting = ref(false)
const detailQuery = reactive({ pageNum: 1, pageSize: 10 })

async function fetchRevenueDetail() {
  const [startDate, endDate] = dateRange.value
  detailLoading.value = true
  try {
    const res = await getRevenueDetailPage({
      startDate,
      endDate,
      pageNum: detailQuery.pageNum,
      pageSize: detailQuery.pageSize,
    })
    detailList.value = res.list ?? []
    detailTotal.value = res.total ?? 0
  } finally {
    detailLoading.value = false
  }
}

function handleDetailSizeChange() {
  detailQuery.pageNum = 1
  fetchRevenueDetail()
}

async function handleExport() {
  const [startDate, endDate] = dateRange.value
  exporting.value = true
  try {
    await exportRevenueDetail(startDate, endDate)
    ElMessage.success(`已导出：收入明细_${startDate}_to_${endDate}.csv`)
  } catch {
    // 错误提示已在 exportRevenueDetail 内统一处理
  } finally {
    exporting.value = false
  }
}

// ---------------- ⑤ 药品库存 ----------------
const drugLoading = ref(false)
const drugList = ref<DrugInventoryRow[]>([])

async function fetchDrugInventory() {
  drugLoading.value = true
  try {
    drugList.value = toList<DrugInventoryRow>(await getDrugInventory())
  } finally {
    drugLoading.value = false
  }
}

function drugRowClass({ row }: { row: DrugInventoryRow }): string {
  return row.warning ? 'warn-row' : ''
}

// ---------------- 汇总 ----------------
const anyLoading = computed(
  () =>
    regLoading.value ||
    deptLoading.value ||
    visitLoading.value ||
    revLoading.value ||
    distLoading.value ||
    detailLoading.value
)

/** 查询：刷新全部日期范围内的统计数据（药品库存为实时数据，切换到页签时加载） */
function handleQuery() {
  fetchRegDaily()
  fetchDeptRank()
  fetchVisitDaily()
  fetchRevenueDaily()
  fetchRevenueDistribution()
  detailQuery.pageNum = 1
  fetchRevenueDetail()
}

watch(activeTab, (tab) => {
  // 药品库存为实时数据，每次进入页签时刷新
  if (tab === 'drug') fetchDrugInventory()
})

onMounted(handleQuery)
</script>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.query-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.query-tip {
  color: #909399;
  font-size: 12px;
}

.tab-section-title {
  margin: 4px 0 10px;
  font-weight: 600;
  color: #303133;
}

.tab-section-title.no-margin {
  margin-top: 0;
}

.refund-amount {
  color: #e6a23c;
}

.net-amount {
  color: #f56c6c;
  font-weight: 600;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.drug-alert {
  margin-bottom: 12px;
}

.expand-wrap {
  padding: 4px 12px 8px 48px;
}

.expand-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.warn-qty {
  color: #f56c6c;
  font-weight: 700;
}

/* 库存预警行红色底色（el-table 单元格自带背景，需覆盖到 td） */
:deep(.el-table .warn-row) {
  background: #fef0f0;
}

:deep(.el-table .warn-row td.el-table__cell) {
  background: #fef0f0;
}
</style>
