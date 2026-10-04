<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="住院号">
        <el-input
          v-model="query.admissionNo"
          placeholder="请输入住院号"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
      <el-form-item>
        <el-radio-group v-model="viewMode" @change="handleSearch">
          <el-radio-button value="unsettled">出院未结</el-radio-button>
          <el-radio-button value="deposit">押金退还</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <span class="toolbar-title">{{ viewMode === 'unsettled' ? '出院未结住院' : '已结算押金退还' }}</span>
      <span class="toolbar-tip">{{ viewMode === 'unsettled' ? '患者已出院但尚未结算费用，请在此办理出院结算' : '已结算住院的押金退还登记：应退 = 押金累计 - 结算账单额' }}</span>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="admissionNo" label="住院号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="patientName" label="患者" min-width="85" show-overflow-tooltip />
      <el-table-column prop="deptName" label="科室" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deptName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="wardName" label="病区" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.wardName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="bedNo" label="床位" width="70" align="center">
        <template #default="{ row }">{{ row.bedNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="admissionTime" label="入院时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.admissionTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="dischargeTime" label="出院时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.dischargeTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="出院诊断" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.dischargeDiagnosis || '-' }}</template>
      </el-table-column>
      <el-table-column label="累计押金" width="100" align="right">
        <template #default="{ row }">¥{{ fmtMoney(row.depositTotal) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="viewMode === 'unsettled'" v-perm="'billing:charge:create'" link type="primary" @click="openSettle(row)">
            出院结算
          </el-button>
          <el-button v-else v-perm="'inp:deposit:refund'" link type="warning" @click="openRefund(row)">
            退押金
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

    <!-- 出院结算弹窗 -->
    <el-dialog v-model="dialogVisible" title="出院结算" width="500px" destroy-on-close>
      <template v-if="currentRow">
        <el-descriptions :column="1" border size="small" class="settle-desc">
          <el-descriptions-item label="住院号">{{ currentRow.admissionNo }}</el-descriptions-item>
          <el-descriptions-item label="患者">{{ currentRow.patientName }}</el-descriptions-item>
          <el-descriptions-item label="累计押金">
            <span class="dep-amount">¥{{ fmtMoney(currentRow.depositTotal) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="支付方式">
            <el-radio-group v-model="payMethod">
              <el-radio-button v-for="o in PAY_METHOD_OPTIONS" :key="o.value" :value="o.value">
                {{ o.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="结算将生成住院账单（费用全额）；系统给出押金应退（补）金额，差额由收银台线下多退少补。"
        />
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSettle">确认结算</el-button>
      </template>
    </el-dialog>

    <!-- 退押金登记弹窗（七十二轮）：应退 = 押金累计 - 结算账单额 -->
    <el-dialog v-model="refundVisible" title="押金退还登记" width="480px" destroy-on-close>
      <template v-if="refundRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="住院号">{{ refundRow.admissionNo }}</el-descriptions-item>
          <el-descriptions-item label="押金累计">¥{{ fmtMoney(refundRow.depositTotal) }}</el-descriptions-item>
          <el-descriptions-item label="结算账单额">¥{{ refundBillTotal === null ? '-' : fmtMoney(refundBillTotal) }}</el-descriptions-item>
          <el-descriptions-item label="应退金额">¥{{ fmtMoney(refundable) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px" style="margin-top: 10px">
          <el-form-item label="退还金额">
            <el-input-number v-model="refundAmount" :min="0.01" :max="refundable || undefined" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="支付方式">
            <el-radio-group v-model="refundPayMethod">
              <el-radio-button v-for="o in PAY_METHOD_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="refundReason" maxlength="128" placeholder="选填" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" :loading="refundSubmitting" :disabled="!refundable" @click="handleRefundSubmit">确认退还</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getBillPage, payMethodLabel, PAY_METHOD_OPTIONS } from '@/api/billing'
import { refundDeposit } from '@/api/inp'
import { fmtMoney } from '@/api/registration'
import {
  getAdmissionPage,
  settleInpAdmission,
  type Admission,
  type InpSettleResult,
} from '@/api/inp'

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<Admission[]>([])
const total = ref(0)
// 七十二轮：视图切换——出院未结（20）/ 已结算押金退还（30）
const viewMode = ref<'unsettled' | 'deposit'>('unsettled')

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  admissionNo: '',
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getAdmissionPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: viewMode.value === 'unsettled' ? 20 : 30,
      admissionNo: query.admissionNo.trim() || undefined,
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
  query.admissionNo = ''
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 出院结算 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const currentRow = ref<Admission | null>(null)
const payMethod = ref(1)

function openSettle(row: Admission) {
  currentRow.value = row
  payMethod.value = 1
  dialogVisible.value = true
}

// 七十二轮：押金退还登记（应退 = 押金累计 - 结算账单额）
const refundVisible = ref(false)
const refundSubmitting = ref(false)
const refundRow = ref<Admission | null>(null)
const refundBillTotal = ref<number | null>(null)
const refundAmount = ref<number | null>(null)
const refundPayMethod = ref(1)
const refundReason = ref('')

const refundable = computed(() => {
  const dep = Number(refundRow.value?.depositTotal ?? 0)
  const bill = Number(refundBillTotal.value ?? 0)
  return Math.max(dep - bill, 0)
})

async function openRefund(row: Admission) {
  refundRow.value = row
  refundBillTotal.value = null
  refundAmount.value = null
  refundPayMethod.value = 1
  refundReason.value = ''
  refundVisible.value = true
  try {
    const res = await getBillPage({ pageNum: 1, pageSize: 1, admissionId: row.id })
    const bill = res.list?.[0]
    refundBillTotal.value = bill ? Number(bill.totalAmount) : 0
    refundAmount.value = refundable.value > 0 ? refundable.value : null
  } catch {
    // 账单额展示 '-'，金额留空由收银台手填
  }
}

async function handleRefundSubmit() {
  if (!refundRow.value) return
  if (!refundAmount.value || refundAmount.value <= 0) {
    ElMessage.warning('请输入退还金额')
    return
  }
  refundSubmitting.value = true
  try {
    await refundDeposit(refundRow.value.id, {
      amount: refundAmount.value,
      payMethod: refundPayMethod.value,
      reason: refundReason.value.trim() || undefined,
    })
    ElMessage.success('押金退还已登记')
    refundVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    refundSubmitting.value = false
  }
}

function resultLines(row: Admission, res: InpSettleResult): string[] {
  const lines: string[] = []
  if (res.billNo) lines.push(`账单号：${res.billNo}`)
  if (res.totalAmount !== undefined && res.totalAmount !== null) {
    lines.push(`结算金额：¥${fmtMoney(res.totalAmount)}`)
  }
  lines.push(`押金累计：¥${fmtMoney(res.depositTotal ?? row.depositTotal)}`)
  if (res.refundAmount !== undefined && res.refundAmount !== null) {
    lines.push(`应退（补）金额：¥${fmtMoney(res.refundAmount)}`)
  }
  lines.push('请核对押金抵扣与补退金额，差额按支付方式多退少补。')
  return lines
}

function handleSettle() {
  const row = currentRow.value
  if (!row) return
  ElMessageBox.confirm(
    `确认为患者「${row.patientName}」办理出院结算（${payMethodLabel(payMethod.value)}）？`,
    '出院结算确认',
    { type: 'warning', confirmButtonText: '确认结算', cancelButtonText: '取消' }
  )
    .then(async () => {
      submitting.value = true
      try {
        const res = await settleInpAdmission(row.id, payMethod.value)
        ElMessageBox.alert(
          h('div', null, resultLines(row, res).map((t) => h('p', null, t))),
          '出院结算成功',
          { type: 'success', confirmButtonText: '知道了' }
        ).catch(() => {})
        dialogVisible.value = false
        fetchList()
      } catch {
        // 拦截器已统一提示
      } finally {
        submitting.value = false
      }
    })
    .catch(() => {
      // 取消
    })
}

onMounted(fetchList)
</script>

<style scoped>
.toolbar-tip {
  color: #909399;
  font-size: 12px;
}

.settle-desc {
  margin-bottom: 14px;
}

.dep-amount {
  color: #f56c6c;
  font-weight: 600;
}
</style>
