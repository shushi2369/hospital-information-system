<template>
  <div class="billing-page">
    <!-- 上：收费区（左未收费就诊 / 右待缴明细） -->
    <div class="charge-layout">
      <!-- 左：未收费就诊列表 -->
      <div class="page-card unpaid-panel">
        <div class="panel-head">
          <span class="toolbar-title">未收费就诊</span>
          <el-button link type="primary" :icon="Refresh" :loading="unpaidLoading" @click="fetchUnpaid">
            刷新
          </el-button>
        </div>
        <el-table
          ref="unpaidTableRef"
          v-loading="unpaidLoading"
          :data="unpaidList"
          size="small"
          border
          stripe
          highlight-current-row
          empty-text="暂无未收费就诊"
          @row-click="handleSelectVisit"
        >
          <el-table-column prop="visitNo" label="就诊号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" min-width="80" show-overflow-tooltip />
          <el-table-column label="就诊日期" width="100" align="center">
            <template #default="{ row }">{{ row.visitDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="待缴金额" width="95" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.unpaidAmount) }}</template>
          </el-table-column>
        </el-table>
        <div class="panel-tip">点击行加载右侧待缴明细，收费成功后自动刷新</div>
      </div>

      <!-- 右：待缴明细与收费 -->
      <div class="page-card payable-panel" v-loading="payableLoading">
        <template v-if="payable">
          <div class="payable-head">
            <span class="toolbar-title">待缴明细</span>
            <span v-if="selectedVisit" class="payable-info">
              {{ selectedVisit.patientName }}｜{{ selectedVisit.visitNo }}
              <template v-if="selectedVisit.doctorName">｜{{ selectedVisit.doctorName }}</template>
            </span>
          </div>
          <el-table :data="payable.items" border stripe size="small" max-height="360">
            <el-table-column type="index" label="#" width="45" align="center" />
            <el-table-column prop="itemName" label="项目名称" min-width="170" show-overflow-tooltip />
            <el-table-column label="费用类别" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="feeTypeTagType(row.feeType)">
                  {{ feeTypeLabel(row.feeType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="85" align="center">
              <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="70" align="center" />
            <el-table-column label="单价" width="95" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div class="payable-footer">
            <span class="total-amount">
              应收合计：<em>¥{{ fmtMoney(payable.totalAmount) }}</em>
            </span>
            <div class="pay-actions">
              <span class="pay-label">支付方式</span>
              <el-radio-group v-model="payMethod">
                <el-radio-button
                  v-for="opt in PAY_METHOD_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </el-radio-button>
              </el-radio-group>
              <el-button
                v-perm="'billing:charge:create'"
                type="primary"
                :icon="Wallet"
                :loading="charging"
                :disabled="payable.items.length === 0"
                @click="handleCharge"
              >
                确认收费
              </el-button>
            </div>
          </div>
        </template>
        <el-empty v-else-if="!payableLoading" description="请从左侧选择未收费就诊" :image-size="90" />
      </div>
    </div>

    <!-- 下：收费记录 / 日结 -->
    <div class="page-card">
      <el-tabs v-model="activeTab">
        <!-- ① 收费记录 -->
        <el-tab-pane label="收费记录" name="bills">
          <el-form class="search-bar" :model="billQuery" inline>
            <el-form-item label="收费单号">
              <el-input
                v-model="billQuery.billNo"
                placeholder="收费单号"
                clearable
                style="width: 170px"
                @keyup.enter="handleBillSearch"
              />
            </el-form-item>
            <el-form-item label="患者ID">
              <el-input
                v-model="billQuery.patientId"
                placeholder="患者ID"
                clearable
                style="width: 110px"
                @keyup.enter="handleBillSearch"
              />
            </el-form-item>
            <el-form-item label="收费日期">
              <el-date-picker
                v-model="billDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 240px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleBillSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleBillReset">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="billLoading" :data="billList" border stripe size="small">
            <el-table-column prop="billNo" label="收费单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="patientName" label="患者" min-width="90" show-overflow-tooltip />
            <el-table-column label="总金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="优惠" width="80" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.discountAmount) }}</template>
            </el-table-column>
            <el-table-column label="实收" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.paidAmount) }}</template>
            </el-table-column>
            <el-table-column label="已退" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.refundAmount) }}</template>
            </el-table-column>
            <el-table-column label="支付方式" width="85" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="payMethodTagType(row.payMethod)">
                  {{ payMethodLabel(row.payMethod) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="收费时间" width="160" align="center">
              <template #default="{ row }">{{ row.payTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="收费员" min-width="85" show-overflow-tooltip>
              <template #default="{ row }">{{ row.cashierName || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="95" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="billStatusTagType(row.status)">
                  {{ billStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="115" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openBillDrawer(row)">详情</el-button>
                <el-button
                  v-if="row.status !== 30"
                  v-perm="'billing:refund:create'"
                  link
                  type="danger"
                  @click="openRefundDialog(row)"
                >
                  退费
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="billQuery.pageNum"
              v-model:page-size="billQuery.pageSize"
              :total="billTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="handleBillSizeChange"
              @current-change="fetchBills"
            />
          </div>
        </el-tab-pane>

        <!-- ② 日结 -->
        <el-tab-pane label="日结" name="settlement">
          <div class="settle-actions">
            <el-date-picker
              v-model="settleDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              placeholder="日结日期"
              style="width: 140px"
            />
            <el-button
              v-perm="'billing:settle:do'"
              type="primary"
              :loading="settling"
              @click="handleSettle"
            >
              执行日结
            </el-button>
            <span class="settle-tip">按当日收费与退费汇总生成日结单，同一天不可重复日结</span>
          </div>

          <el-descriptions v-if="lastSettlement" :column="3" border size="small" class="settle-result">
            <template #title>本次日结结果</template>
            <el-descriptions-item label="日结单号">{{ lastSettlement.settlementNo }}</el-descriptions-item>
            <el-descriptions-item label="收费笔数">{{ lastSettlement.billCount }}</el-descriptions-item>
            <el-descriptions-item label="退费笔数">{{ lastSettlement.refundCount }}</el-descriptions-item>
            <el-descriptions-item label="收费合计">¥{{ fmtMoney(lastSettlement.totalChargeAmount) }}</el-descriptions-item>
            <el-descriptions-item label="退费合计">¥{{ fmtMoney(lastSettlement.totalRefundAmount) }}</el-descriptions-item>
            <el-descriptions-item label="本日净额">
              <span class="net-amount">¥{{ fmtMoney(lastSettlement.netAmount) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-form class="search-bar" :model="settleQuery" inline>
            <el-form-item label="日结日期">
              <el-date-picker
                v-model="settleQuery.settleDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="全部日期"
                clearable
                style="width: 140px"
              />
            </el-form-item>
            <el-form-item label="收费员ID">
              <el-input
                v-model="settleQuery.cashierId"
                placeholder="收费员ID"
                clearable
                style="width: 110px"
                @keyup.enter="handleSettleSearch"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSettleSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleSettleReset">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="settleLoading" :data="settleList" border stripe size="small">
            <el-table-column prop="settlementNo" label="日结单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="settleDate" label="日结日期" width="110" align="center" />
            <el-table-column label="收费员" min-width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ row.cashierName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="billCount" label="收费笔数" width="90" align="center" />
            <el-table-column prop="refundCount" label="退费笔数" width="90" align="center" />
            <el-table-column label="收费合计" width="105" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.totalChargeAmount) }}</template>
            </el-table-column>
            <el-table-column label="退费合计" width="105" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.totalRefundAmount) }}</template>
            </el-table-column>
            <el-table-column label="净额" width="105" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.netAmount) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="settleQuery.pageNum"
              v-model:page-size="settleQuery.pageSize"
              :total="settleTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="handleSettleSizeChange"
              @current-change="fetchSettlements"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 账单详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="收费单详情" size="780px" destroy-on-close>
      <div v-loading="drawerLoading">
        <template v-if="billDetail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="收费单号">{{ billDetail.bill.billNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="billStatusTagType(billDetail.bill.status)">
                {{ billStatusLabel(billDetail.bill.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="患者">
              {{ billDetail.bill.patientName }}（ID：{{ billDetail.bill.patientId }}）
            </el-descriptions-item>
            <el-descriptions-item label="就诊ID">{{ billDetail.bill.visitId }}</el-descriptions-item>
            <el-descriptions-item label="总金额">¥{{ fmtMoney(billDetail.bill.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="优惠金额">¥{{ fmtMoney(billDetail.bill.discountAmount) }}</el-descriptions-item>
            <el-descriptions-item label="应收金额">¥{{ fmtMoney(billDetail.bill.payableAmount) }}</el-descriptions-item>
            <el-descriptions-item label="实收金额">¥{{ fmtMoney(billDetail.bill.paidAmount) }}</el-descriptions-item>
            <el-descriptions-item label="已退金额">¥{{ fmtMoney(billDetail.bill.refundAmount) }}</el-descriptions-item>
            <el-descriptions-item label="支付方式">{{ payMethodLabel(billDetail.bill.payMethod) }}</el-descriptions-item>
            <el-descriptions-item label="收费时间">{{ billDetail.bill.payTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收费员">{{ billDetail.bill.cashierName || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="drawer-section">收费明细</div>
          <el-table :data="billDetail.details" border size="small">
            <el-table-column prop="itemName" label="项目名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="费用类别" width="85" align="center">
              <template #default="{ row }">{{ feeTypeLabel(row.feeType) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="65" align="center" />
            <el-table-column label="单价" width="85" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="退费状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="refundStatusTagType(row.refundStatus)">
                  {{ refundStatusLabel(row.refundStatus) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="drawer-section">支付记录</div>
          <el-table :data="billDetail.payments" border size="small">
            <el-table-column prop="payNo" label="支付单号" min-width="140" show-overflow-tooltip />
            <el-table-column label="支付方式" width="85" align="center">
              <template #default="{ row }">{{ payMethodLabel(row.payMethod) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="第三方流水号" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.transactionId || '-' }}</template>
            </el-table-column>
            <el-table-column label="支付时间" width="160" align="center">
              <template #default="{ row }">{{ row.payTime || '-' }}</template>
            </el-table-column>
          </el-table>

          <div class="drawer-section">退费记录</div>
          <el-table :data="billDetail.refunds" border size="small" empty-text="无退费记录">
            <el-table-column prop="refundNo" label="退费单号" min-width="140" show-overflow-tooltip />
            <el-table-column label="退费金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.refundAmount) }}</template>
            </el-table-column>
            <el-table-column label="退费原因" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reason || '-' }}</template>
            </el-table-column>
            <el-table-column label="退费时间" width="160" align="center">
              <template #default="{ row }">{{ row.refundTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作人" min-width="85" show-overflow-tooltip>
              <template #default="{ row }">{{ row.operatorName || '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <!-- 退费弹窗 -->
    <el-dialog
      v-model="refundDialogVisible"
      title="办理退费"
      width="880px"
      top="6vh"
      destroy-on-close
      append-to-body
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="refund-alert"
        title="已发药药品需先到药房退药，再办理退费；已完成就诊的挂号费不可退。"
      />
      <div v-if="refundBill" class="refund-bill-line">
        收费单号：{{ refundBill.billNo }}｜患者：{{ refundBill.patientName }}｜实收：¥{{ fmtMoney(refundBill.paidAmount) }}｜已退：¥{{ fmtMoney(refundBill.refundAmount) }}
      </div>
      <el-table v-loading="refundLoading" :data="refundRows" border size="small" max-height="330">
        <el-table-column prop="itemName" label="项目名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="费用类别" width="85" align="center">
          <template #default="{ row }">{{ feeTypeLabel(row.feeType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="70" align="center" />
        <el-table-column label="单价" width="85" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="退费状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="refundStatusTagType(row.refundStatus)">
              {{ refundStatusLabel(row.refundStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本次退数量" width="135" align="center">
          <template #default="{ row }">
            <el-input-number
              v-if="row.refundStatus !== 2"
              v-model="row.refundQty"
              :min="1"
              :max="(Number(row.quantity) || 0) - (Number(row.refundedQty) || 0)"
              size="small"
              controls-position="right"
              style="width: 110px"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-form
        ref="refundFormRef"
        :model="refundForm"
        :rules="refundRules"
        label-width="80px"
        class="refund-form"
      >
        <el-form-item label="退费原因" prop="reason">
          <el-input
            v-model="refundForm.reason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请输入退费原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="refundSubmitting" @click="handleRefundSubmit">
          确认退费
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Search, Wallet } from '@element-plus/icons-vue'
import {
  billStatusLabel,
  billStatusTagType,
  createBill,
  createRefund,
  createSettlement,
  feeTypeLabel,
  feeTypeTagType,
  getBillDetail,
  getBillPage,
  getPayable,
  getSettlementPage,
  getUnpaidVisits,
  payMethodLabel,
  payMethodTagType,
  PAY_METHOD_OPTIONS,
  refundStatusLabel,
  refundStatusTagType,
  sourceTypeLabel,
  type Bill,
  type BillDetail,
  type ChargeDetail,
  type PayableResult,
  type Settlement,
  type SettlementResult,
  type UnpaidVisit,
} from '@/api/billing'
import { fmtMoney, todayStr } from '@/api/registration'
import { toList } from '@/api/request'

// ---------------- 左侧未收费就诊 ----------------
const unpaidList = ref<UnpaidVisit[]>([])
const unpaidLoading = ref(false)
const selectedVisit = ref<UnpaidVisit | null>(null)
const unpaidTableRef = ref()

async function fetchUnpaid() {
  unpaidLoading.value = true
  try {
    unpaidList.value = toList<UnpaidVisit>(await getUnpaidVisits())
  } finally {
    unpaidLoading.value = false
  }
}

// ---------------- 右侧待缴明细 ----------------
const payable = ref<PayableResult | null>(null)
const payableLoading = ref(false)
const payMethod = ref(1)
const charging = ref(false)

async function handleSelectVisit(row: UnpaidVisit) {
  if (!row) return
  selectedVisit.value = row
  payable.value = null
  payableLoading.value = true
  try {
    const result = await getPayable(row.visitId)
    // 乱序守卫：期间用户已选中另一行则丢弃本次明细
    if (selectedVisit.value === row) {
      payable.value = result
    }
  } catch {
    if (selectedVisit.value === row) {
      payable.value = null
    }
  } finally {
    payableLoading.value = false
  }
}

function clearSelection() {
  selectedVisit.value = null
  payable.value = null
  unpaidTableRef.value?.setCurrentRow?.()
}

function handleCharge() {
  const visit = selectedVisit.value
  const payableData = payable.value
  if (!visit || !payableData) return
  if (!payableData.items.length) {
    ElMessage.warning('该就诊没有待缴费用')
    return
  }
  ElMessageBox.confirm(
    `确认为患者「${visit.patientName}」收费 ¥${fmtMoney(payableData.totalAmount)}（${payMethodLabel(payMethod.value)}）？`,
    '收费确认',
    { type: 'warning', confirmButtonText: '确认收费', cancelButtonText: '取消' }
  )
    .then(async () => {
      charging.value = true
      try {
        const res = await createBill({ visitId: visit.visitId, payMethod: payMethod.value })
        ElMessageBox.alert(
          h('div', null, [
            h('p', null, `收费单号：${res.billNo}`),
            h('p', null, `支付方式：${payMethodLabel(payMethod.value)}`),
            h('p', null, `收费金额：¥${fmtMoney(res.totalAmount)}`),
          ]),
          '收费成功',
          { type: 'success', confirmButtonText: '知道了' }
        ).catch(() => {})
        clearSelection()
        fetchUnpaid()
        fetchBills()
      } catch {
        // B3001 已收费 / B3002 无待缴等已在拦截器统一提示
      } finally {
        charging.value = false
      }
    })
    .catch(() => {
      // 取消
    })
}

// ---------------- ① 收费记录 ----------------
const activeTab = ref('bills')
const billLoading = ref(false)
const billList = ref<Bill[]>([])
const billTotal = ref(0)
const billDateRange = ref<[string, string] | null>(null)
const billQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  billNo: '',
  patientId: '',
})

async function fetchBills() {
  billLoading.value = true
  try {
    const pid = Number(billQuery.patientId)
    const res = await getBillPage({
      pageNum: billQuery.pageNum,
      pageSize: billQuery.pageSize,
      billNo: billQuery.billNo.trim() || undefined,
      patientId: Number.isFinite(pid) && pid > 0 ? pid : undefined,
      startDate: billDateRange.value?.[0] || undefined,
      endDate: billDateRange.value?.[1] || undefined,
    })
    billList.value = res.list ?? []
    billTotal.value = res.total ?? 0
  } finally {
    billLoading.value = false
  }
}

function handleBillSearch() {
  billQuery.pageNum = 1
  fetchBills()
}

function handleBillReset() {
  billQuery.billNo = ''
  billQuery.patientId = ''
  billDateRange.value = null
  handleBillSearch()
}

function handleBillSizeChange() {
  billQuery.pageNum = 1
  fetchBills()
}

// ---------------- 账单详情抽屉 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const billDetail = ref<BillDetail | null>(null)

async function openBillDrawer(row: Bill) {
  drawerVisible.value = true
  drawerLoading.value = true
  billDetail.value = null
  try {
    billDetail.value = await getBillDetail(row.id)
  } catch {
    billDetail.value = null
  } finally {
    drawerLoading.value = false
  }
}

// ---------------- 退费 ----------------
interface RefundRow extends ChargeDetail {
  refundQty: number
}

const refundDialogVisible = ref(false)
const refundLoading = ref(false)
const refundSubmitting = ref(false)
const refundBill = ref<Bill | null>(null)
const refundRows = ref<RefundRow[]>([])
const refundFormRef = ref<FormInstance>()
const refundForm = reactive({ reason: '' })

const refundRules: FormRules = {
  reason: [{ required: true, message: '请输入退费原因', trigger: 'blur' }],
}

async function openRefundDialog(row: Bill) {
  refundBill.value = row
  refundRows.value = []
  refundForm.reason = ''
  refundDialogVisible.value = true
  refundLoading.value = true
  try {
    const detail = await getBillDetail(row.id)
    // 按剩余可退量预填（quantity - refundedQty）；已全退行不可退（八十七轮契约审计）
    refundRows.value = (detail.details ?? []).map((d) => ({
      ...d,
      refundQty:
        d.refundStatus === 2
          ? 0
          : Math.max(0, (Number(d.quantity) || 0) - (Number(d.refundedQty) || 0)),
    }))
  } catch {
    refundRows.value = []
  } finally {
    refundLoading.value = false
  }
}

async function handleRefundSubmit() {
  if (!refundBill.value) return
  const valid = await refundFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const details = refundRows.value
    .filter((r) => r.refundStatus !== 2 && r.refundQty > 0)
    .map((r) => ({ chargeDetailId: r.id, refundQuantity: r.refundQty }))
  if (details.length === 0) {
    ElMessage.warning('请至少填写一行本次退数量（已全退明细不可退）')
    return
  }
  const over = details.find((d) => {
    const row = refundRows.value.find((r) => r.id === d.chargeDetailId)
    return row
      ? d.refundQuantity > (Number(row.quantity) || 0) - (Number(row.refundedQty) || 0)
      : false
  })
  if (over) {
    ElMessage.warning('本次退数量不能超过明细数量')
    return
  }
  refundSubmitting.value = true
  try {
    const res = await createRefund({
      billId: refundBill.value.id,
      reason: refundForm.reason.trim(),
      details,
    })
    ElMessage.success(`退费成功，退费单号：${res}`)
    refundDialogVisible.value = false
    fetchBills()
  } catch {
    // B3003 退费超限 / B3004 已发药先退药 / B3005 挂号费不可退已在拦截器统一提示
  } finally {
    refundSubmitting.value = false
  }
}

// ---------------- ② 日结 ----------------
const settleDate = ref(todayStr())
const settling = ref(false)
const lastSettlement = ref<SettlementResult | null>(null)
const settleLoading = ref(false)
const settleList = ref<Settlement[]>([])
const settleTotal = ref(0)
const settleQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  settleDate: '',
  cashierId: '',
})

async function fetchSettlements() {
  settleLoading.value = true
  try {
    const cid = Number(settleQuery.cashierId)
    const res = await getSettlementPage({
      pageNum: settleQuery.pageNum,
      pageSize: settleQuery.pageSize,
      settleDate: settleQuery.settleDate || undefined,
      cashierId: Number.isFinite(cid) && cid > 0 ? cid : undefined,
    })
    settleList.value = res.list ?? []
    settleTotal.value = res.total ?? 0
  } finally {
    settleLoading.value = false
  }
}

function handleSettleSearch() {
  settleQuery.pageNum = 1
  fetchSettlements()
}

function handleSettleReset() {
  settleQuery.settleDate = ''
  settleQuery.cashierId = ''
  handleSettleSearch()
}

function handleSettleSizeChange() {
  settleQuery.pageNum = 1
  fetchSettlements()
}

function handleSettle() {
  ElMessageBox.confirm(
    `确认执行 ${settleDate.value} 的收费日结？日结按当前登录收费员汇总当日收费与退费。`,
    '执行日结',
    { type: 'warning', confirmButtonText: '确认日结', cancelButtonText: '取消' }
  )
    .then(async () => {
      settling.value = true
      try {
        const res = await createSettlement(settleDate.value)
        lastSettlement.value = res
        ElMessageBox.alert(
          h('div', null, [
            h('p', null, `日结单号：${res.settlementNo}`),
            h('p', null, `收费 ${res.billCount} 笔，合计 ¥${fmtMoney(res.totalChargeAmount)}`),
            h('p', null, `退费 ${res.refundCount} 笔，合计 ¥${fmtMoney(res.totalRefundAmount)}`),
            h('p', null, `本日净额：¥${fmtMoney(res.netAmount)}`),
          ]),
          '日结成功',
          { type: 'success', confirmButtonText: '知道了' }
        ).catch(() => {})
        fetchSettlements()
      } catch {
        // B3006 已日结等已在拦截器统一提示
      } finally {
        settling.value = false
      }
    })
    .catch(() => {
      // 取消
    })
}

watch(activeTab, (tab) => {
  // 首次/每次切到日结页签时刷新记录
  if (tab === 'settlement') fetchSettlements()
})

onMounted(() => {
  fetchUnpaid()
  fetchBills()
})
</script>

<style scoped>
.billing-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.charge-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.unpaid-panel {
  width: 470px;
  flex-shrink: 0;
}

.payable-panel {
  flex: 1;
  min-width: 0;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.panel-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.payable-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.payable-info {
  color: #606266;
  font-size: 13px;
}

.payable-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.total-amount {
  font-size: 15px;
  color: #303133;
}

.total-amount em {
  font-style: normal;
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}

.pay-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pay-label {
  color: #606266;
}

.settle-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.settle-tip {
  color: #909399;
  font-size: 12px;
}

.settle-result {
  max-width: 780px;
  margin-bottom: 16px;
}

.net-amount {
  color: #f56c6c;
  font-weight: 700;
}

.drawer-section {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}

.refund-alert {
  margin-bottom: 12px;
}

.refund-bill-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}

.refund-form {
  max-width: 640px;
  margin-top: 12px;
}
</style>
