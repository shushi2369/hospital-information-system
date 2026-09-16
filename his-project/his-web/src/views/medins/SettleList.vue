<template>
  <div class="medins-page">
    <!-- 上：已支付住院账单（可申报） -->
    <div class="page-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">已支付账单（住院）</span>
          <span class="toolbar-tip">展示最近 50 笔已支付账单，标记医保申报状态</span>
        </div>
        <el-button link type="primary" :icon="Refresh" :loading="billLoading" @click="fetchAll">
          刷新
        </el-button>
      </div>
      <el-table v-loading="billLoading" :data="billRows" border stripe size="small">
        <el-table-column prop="billNo" label="收费单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" min-width="90" show-overflow-tooltip />
        <el-table-column label="总金额" width="100" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="100" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="payTime" label="支付时间" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.payTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="申报状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="settledBillIds.has(row.id)" size="small" type="success">已申报</el-tag>
            <el-tag v-else size="small" type="info">未申报</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button
              v-if="!settledBillIds.has(row.id)"
              v-perm="'medins:settle:create'"
              link
              type="primary"
              @click="openApplyDialog(row)"
            >
              医保申报
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 下：申报单 / 日对账 -->
    <div class="page-card">
      <el-tabs v-model="activeTab">
        <!-- ① 医保申报单 -->
        <el-tab-pane label="医保申报单" name="settles">
          <el-form class="search-bar" :model="settleQuery" inline>
            <el-form-item label="结算单号">
              <el-input
                v-model="settleQuery.settleNo"
                placeholder="医保结算单号"
                clearable
                style="width: 180px"
                @keyup.enter="handleSettleSearch"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="settleQuery.status" placeholder="全部" clearable style="width: 130px">
                <el-option v-for="o in MEDINS_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSettleSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleSettleReset">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="settleLoading" :data="settles" border stripe size="small">
            <el-table-column prop="settleNo" label="结算单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="billId" label="账单ID" width="80" align="center" />
            <el-table-column prop="admissionId" label="住院ID" width="85" align="center">
              <template #default="{ row }">{{ row.admissionId ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="险种" width="95" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="insuranceTypeTagType(row.insuranceType)">
                  {{ insuranceTypeLabel(row.insuranceType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="总金额" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="账户支付" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.accountPay) }}</template>
            </el-table-column>
            <el-table-column label="统筹支付" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.poolPay) }}</template>
            </el-table-column>
            <el-table-column label="自费金额" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.selfPay) }}</template>
            </el-table-column>
            <el-table-column prop="applyTime" label="申报时间" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.applyTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="95" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="medinsStatusTagType(row.status)">
                  {{ medinsStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 10"
                  v-perm="'medins:settle:reconcile'"
                  link
                  type="primary"
                  @click="handleReconcile(row)"
                >
                  对账
                </el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="settleQuery.pageNum"
              v-model:page-size="settleQuery.pageSize"
              :total="settleTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="handleSettleSizeChange"
              @current-change="fetchSettles"
            />
          </div>
        </el-tab-pane>

        <!-- ② 日对账汇总 -->
        <el-tab-pane label="日对账汇总" name="daily">
          <div class="settle-actions">
            <el-date-picker
              v-model="dailyDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              placeholder="对账日期"
              style="width: 150px"
            />
            <el-button type="primary" :icon="Search" :loading="dailyLoading" @click="fetchDaily">
              查询汇总
            </el-button>
          </div>
          <el-empty v-if="!dailyLoading && dailyRows.length === 0" description="该日期暂无医保申报记录" :image-size="80" />
          <template v-else>
            <el-descriptions :column="4" border size="small" class="daily-summary">
              <el-descriptions-item label="申报笔数">{{ dailyRows.length }}</el-descriptions-item>
              <el-descriptions-item label="总金额">¥{{ fmtMoney(dailyTotals.totalAmount) }}</el-descriptions-item>
              <el-descriptions-item label="统筹支付">¥{{ fmtMoney(dailyTotals.poolPay) }}</el-descriptions-item>
              <el-descriptions-item label="账户支付">¥{{ fmtMoney(dailyTotals.accountPay) }}</el-descriptions-item>
              <el-descriptions-item label="自费金额">¥{{ fmtMoney(dailyTotals.selfPay) }}</el-descriptions-item>
              <el-descriptions-item label="对账通过">{{ dailyPassCount }} 笔</el-descriptions-item>
              <el-descriptions-item label="对账差异">{{ dailyDiffCount }} 笔</el-descriptions-item>
            </el-descriptions>
            <el-table :data="dailyRows" border stripe size="small" class="daily-table">
              <el-table-column prop="settleNo" label="结算单号" min-width="150" show-overflow-tooltip />
              <el-table-column label="险种" width="95" align="center">
                <template #default="{ row }">{{ insuranceTypeLabel(row.insuranceType) }}</template>
              </el-table-column>
              <el-table-column label="总金额" width="100" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column label="统筹支付" width="100" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.poolPay) }}</template>
              </el-table-column>
              <el-table-column label="账户支付" width="100" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.accountPay) }}</template>
              </el-table-column>
              <el-table-column label="自费金额" width="100" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.selfPay) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="95" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="medinsStatusTagType(row.status)">
                    {{ medinsStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="applyTime" label="申报时间" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.applyTime || '-' }}</template>
              </el-table-column>
            </el-table>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 医保申报弹窗 -->
    <el-dialog v-model="applyDialogVisible" title="医保申报" width="460px" destroy-on-close>
      <div v-if="applyRow" class="apply-line">
        收费单号：{{ applyRow.billNo }}｜患者：{{ applyRow.patientName }}｜实收：¥{{ fmtMoney(applyRow.paidAmount) }}
      </div>
      <el-form label-width="90px">
        <el-form-item label="险种" required>
          <el-radio-group v-model="applyInsuranceType">
            <el-radio v-for="o in INSURANCE_TYPE_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon title="同一账单不可重复申报（B6401）。" />
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySubmitting" @click="handleApplySubmit">确认申报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getBillPage, type Bill } from '@/api/billing'
import { fmtMoney, todayStr } from '@/api/registration'
import {
  applyInsuranceSettle,
  getDailyReconcile,
  getInsuranceSettlePage,
  insuranceTypeLabel,
  insuranceTypeTagType,
  INSURANCE_TYPE_OPTIONS,
  medinsStatusLabel,
  medinsStatusTagType,
  MEDINS_STATUS_OPTIONS,
  normalizeSettleNo,
  reconcileSettle,
  type DailyReconcile,
  type InsuranceSettle,
} from '@/api/medins'

// ---------------- 已支付账单（仅住院账单，后端 BillResponse 含 admissionId） ----------------
const activeTab = ref('settles')
const billLoading = ref(false)
const bills = ref<Bill[]>([])

async function fetchBills() {
  billLoading.value = true
  try {
    const res = await getBillPage({ pageNum: 1, pageSize: 50 })
    bills.value = (res.list ?? []).filter((b) => {
      const admissionId = (b as Bill & { admissionId?: number | null }).admissionId
      return admissionId !== null && admissionId !== undefined && b.status === 10
    })
  } catch {
    bills.value = []
  } finally {
    billLoading.value = false
  }
}

// ---------------- 申报单 ----------------
const settleLoading = ref(false)
const settles = ref<InsuranceSettle[]>([])
const settleTotal = ref(0)
const settleQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  settleNo: '',
  status: undefined as number | undefined,
})

/** 已申报账单集合（用于标记申报状态） */
const settledBillIds = computed(() => new Set(settles.value.map((s) => s.billId)))

async function fetchSettles() {
  settleLoading.value = true
  try {
    const res = await getInsuranceSettlePage({
      pageNum: settleQuery.pageNum,
      pageSize: settleQuery.pageSize,
      settleNo: settleQuery.settleNo.trim() || undefined,
      status: settleQuery.status,
    })
    settles.value = res.list ?? []
    settleTotal.value = res.total ?? 0
  } finally {
    settleLoading.value = false
  }
}

function handleSettleSearch() {
  settleQuery.pageNum = 1
  fetchSettles()
}

function handleSettleReset() {
  settleQuery.settleNo = ''
  settleQuery.status = undefined
  handleSettleSearch()
}

function handleSettleSizeChange() {
  settleQuery.pageNum = 1
  fetchSettles()
}

async function fetchAll() {
  await Promise.all([fetchBills(), fetchSettles()])
}

// ---------------- 医保申报 ----------------
const applyDialogVisible = ref(false)
const applySubmitting = ref(false)
const applyRow = ref<Bill | null>(null)
const applyInsuranceType = ref(1)

function openApplyDialog(row: Bill) {
  applyRow.value = row
  applyInsuranceType.value = 1
  applyDialogVisible.value = true
}

async function handleApplySubmit() {
  if (!applyRow.value) return
  applySubmitting.value = true
  try {
    const res = await applyInsuranceSettle(applyRow.value.id, applyInsuranceType.value)
    const settleNo = normalizeSettleNo(res)
    ElMessageBox.alert(
      `医保申报成功，结算单号：${settleNo || '-'}`,
      '申报成功',
      { type: 'success', confirmButtonText: '知道了' }
    ).catch(() => {})
    applyDialogVisible.value = false
    fetchAll()
  } catch {
    // B6401 重复申报已在拦截器统一提示
  } finally {
    applySubmitting.value = false
  }
}

// ---------------- 对账 ----------------
function handleReconcile(row: InsuranceSettle) {
  ElMessageBox.prompt('对账备注（选填）', '申报单对账', {
    type: 'warning',
    confirmButtonText: '确认对账',
    cancelButtonText: '取消',
  })
    .then(async ({ value }) => {
      await reconcileSettle(row.id, value?.trim() || undefined)
      ElMessage.success('对账完成')
      fetchSettles()
    })
    .catch(() => {
      // 取消
    })
}

// ---------------- 日对账汇总（后端返回当日申报单列表） ----------------
const dailyDate = ref(todayStr())
const dailyLoading = ref(false)
const daily = ref<DailyReconcile | null>(null)

const dailyRows = computed<InsuranceSettle[]>(() =>
  Array.isArray(daily.value) ? daily.value : []
)

function sumBy(rows: InsuranceSettle[], key: 'totalAmount' | 'poolPay' | 'accountPay' | 'selfPay') {
  return rows.reduce((acc, r) => acc + Number(r[key] ?? 0), 0)
}

const dailyTotals = computed(() => ({
  totalAmount: sumBy(dailyRows.value, 'totalAmount'),
  poolPay: sumBy(dailyRows.value, 'poolPay'),
  accountPay: sumBy(dailyRows.value, 'accountPay'),
  selfPay: sumBy(dailyRows.value, 'selfPay'),
}))

const dailyPassCount = computed(() => dailyRows.value.filter((r) => r.status === 20).length)
const dailyDiffCount = computed(() => dailyRows.value.filter((r) => r.status === 30).length)

async function fetchDaily() {
  dailyLoading.value = true
  daily.value = null
  try {
    daily.value = await getDailyReconcile(dailyDate.value)
  } catch {
    daily.value = null
  } finally {
    dailyLoading.value = false
  }
}

onMounted(fetchAll)
</script>

<style scoped>
.medins-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  color: #909399;
  font-size: 12px;
}

.settle-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.apply-line {
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.daily-summary {
  margin-bottom: 14px;
  max-width: 980px;
}
</style>
