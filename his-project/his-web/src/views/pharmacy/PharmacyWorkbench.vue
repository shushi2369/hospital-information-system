<template>
  <div class="page-card pharmacy-page">
    <el-tabs v-model="activeTab">
      <!-- ① 处方审核 -->
      <el-tab-pane label="处方审核" name="review">
        <div class="table-toolbar">
          <el-radio-group v-model="rxQueueStatus" size="small" @change="fetchPrescriptions">
            <el-radio-button :value="10">待审核</el-radio-button>
            <el-radio-button :value="20">已通过</el-radio-button>
          </el-radio-group>
          <el-button link type="primary" :icon="Refresh" :loading="rxLoading" @click="fetchPrescriptions">
            刷新
          </el-button>
        </div>

        <el-table v-loading="rxLoading" :data="rxList" border stripe size="small" row-key="id">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-wrap">
                <div class="expand-title">处方明细（{{ (row.items || []).length }} 项）</div>
                <el-table :data="row.items" border size="small">
                  <el-table-column prop="drugName" label="药品名称" min-width="130" show-overflow-tooltip />
                  <el-table-column label="规格" min-width="100" show-overflow-tooltip>
                    <template #default="{ row: item }">{{ item.spec || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="dosage" label="剂量" min-width="80" />
                  <el-table-column prop="frequency" label="频次" width="70" align="center" />
                  <el-table-column prop="usageRoute" label="用法" width="70" align="center" />
                  <el-table-column prop="days" label="天数" width="60" align="center" />
                  <el-table-column label="数量" width="85" align="center">
                    <template #default="{ row: item }">{{ item.quantity }}{{ item.unit || '' }}</template>
                  </el-table-column>
                  <el-table-column label="单价" width="85" align="right">
                    <template #default="{ row: item }">¥{{ fmtMoney(item.unitPrice) }}</template>
                  </el-table-column>
                  <el-table-column label="金额" width="90" align="right">
                    <template #default="{ row: item }">¥{{ fmtMoney(item.amount) }}</template>
                  </el-table-column>
                  <el-table-column label="嘱托" min-width="110" show-overflow-tooltip>
                    <template #default="{ row: item }">{{ item.usageNote || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="rxNo" label="处方号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" min-width="85" show-overflow-tooltip />
          <el-table-column prop="patientNo" label="建档号" min-width="110" show-overflow-tooltip />
          <el-table-column prop="visitNo" label="就诊号" min-width="130" show-overflow-tooltip />
          <el-table-column label="科室" min-width="95" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deptName || '-' }}</template>
          </el-table-column>
          <el-table-column label="医生" min-width="85" show-overflow-tooltip>
            <template #default="{ row }">{{ row.doctorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="95" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="收费" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="chargeStatusTagType(row.chargeStatus)">
                {{ chargeStatusLabel(row.chargeStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="rxStatusTagType(row.status)">
                {{ rxStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开单时间" width="165" align="center">
            <template #default="{ row }">{{ row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="rxQueueStatus === 10" label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'pharmacy:review:do'" link type="success" @click="openReview(row, true)">
                通过
              </el-button>
              <el-button v-perm="'pharmacy:review:do'" link type="danger" @click="openReview(row, false)">
                驳回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ② 可发药 -->
      <el-tab-pane label="可发药" name="dispensable">
        <div class="table-toolbar">
          <span class="toolbar-title">审核通过且已收费的处方</span>
          <el-button link type="primary" :icon="Refresh" :loading="dispensableLoading" @click="fetchDispensable">
            刷新
          </el-button>
        </div>

        <el-table v-loading="dispensableLoading" :data="dispensableList" border stripe size="small" row-key="id">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-wrap">
                <div class="expand-title">处方明细（{{ (row.items || []).length }} 项）</div>
                <el-table :data="row.items" border size="small">
                  <el-table-column prop="drugName" label="药品名称" min-width="130" show-overflow-tooltip />
                  <el-table-column label="规格" min-width="100" show-overflow-tooltip>
                    <template #default="{ row: item }">{{ item.spec || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="数量" width="85" align="center">
                    <template #default="{ row: item }">{{ item.quantity }}{{ item.unit || '' }}</template>
                  </el-table-column>
                  <el-table-column label="单价" width="85" align="right">
                    <template #default="{ row: item }">¥{{ fmtMoney(item.unitPrice) }}</template>
                  </el-table-column>
                  <el-table-column label="金额" width="90" align="right">
                    <template #default="{ row: item }">¥{{ fmtMoney(item.amount) }}</template>
                  </el-table-column>
                  <el-table-column label="嘱托" min-width="110" show-overflow-tooltip>
                    <template #default="{ row: item }">{{ item.usageNote || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="rxNo" label="处方号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" min-width="90" show-overflow-tooltip />
          <el-table-column prop="patientNo" label="建档号" min-width="110" show-overflow-tooltip />
          <el-table-column prop="visitNo" label="就诊号" min-width="130" show-overflow-tooltip />
          <el-table-column label="科室" min-width="95" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deptName || '-' }}</template>
          </el-table-column>
          <el-table-column label="医生" min-width="85" show-overflow-tooltip>
            <template #default="{ row }">{{ row.doctorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="95" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="项目数" width="80" align="center">
            <template #default="{ row }">{{ (row.items || []).length }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'pharmacy:dispense:do'" link type="primary" @click="handleDispense(row)">
                发药
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ③ 发药记录 -->
      <el-tab-pane label="发药记录" name="dispense">
        <el-form class="search-bar" :model="dispenseQuery" inline>
          <el-form-item label="发药单号">
            <el-input
              v-model="dispenseQuery.dispenseNo"
              placeholder="发药单号"
              clearable
              style="width: 170px"
              @keyup.enter="handleDispenseSearch"
            />
          </el-form-item>
          <el-form-item label="发药日期">
            <el-date-picker
              v-model="dispenseDateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleDispenseSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleDispenseReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="dispenseLoading" :data="dispenseList" border stripe size="small">
          <el-table-column prop="dispenseNo" label="发药单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="rxNo" label="处方号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" min-width="90" show-overflow-tooltip />
          <el-table-column label="发药人" min-width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ row.dispenserName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="totalQuantity" label="发药总量" width="95" align="center" />
          <el-table-column label="发药时间" width="165" align="center">
            <template #default="{ row }">{{ row.dispenseTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'pharmacy:return:do'" link type="danger" @click="openReturnDialog(row)">
                退药
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="dispenseQuery.pageNum"
            v-model:page-size="dispenseQuery.pageSize"
            :total="dispenseTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleDispenseSizeChange"
            @current-change="fetchDispenseOrders"
          />
        </div>
      </el-tab-pane>

      <!-- ④ 退药记录 -->
      <el-tab-pane label="退药记录" name="returns">
        <el-form class="search-bar" :model="returnQuery" inline>
          <el-form-item label="退药单号">
            <el-input
              v-model="returnQuery.returnNo"
              placeholder="退药单号"
              clearable
              style="width: 170px"
              @keyup.enter="handleReturnSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleReturnSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReturnReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="returnLoading" :data="returnList" border stripe size="small">
          <el-table-column prop="returnNo" label="退药单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="dispenseNo" label="发药单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="rxNo" label="处方号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" min-width="90" show-overflow-tooltip />
          <el-table-column label="退药原因" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reason || '-' }}</template>
          </el-table-column>
          <el-table-column label="退药时间" width="165" align="center">
            <template #default="{ row }">{{ row.returnTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作人" min-width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ row.operatorName || '-' }}</template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="returnQuery.pageNum"
            v-model:page-size="returnQuery.pageSize"
            :total="returnTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleReturnSizeChange"
            @current-change="fetchReturns"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 审核弹窗（通过/驳回共用，意见必填） -->
    <el-dialog
      v-model="reviewDialogVisible"
      :title="reviewPass ? '审核通过' : '审核驳回'"
      width="480px"
      destroy-on-close
      append-to-body
    >
      <div v-if="reviewRx" class="review-rx-line">
        处方号：{{ reviewRx.rxNo }}｜患者：{{ reviewRx.patientName }}｜金额：¥{{ fmtMoney(reviewRx.totalAmount) }}
      </div>
      <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="80px">
        <el-form-item label="审核意见" prop="comment">
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            :placeholder="reviewPass ? '请输入审核意见（必填）' : '请输入驳回原因（必填）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button :type="reviewPass ? 'primary' : 'danger'" :loading="reviewSubmitting" @click="handleReviewSubmit">
          {{ reviewPass ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 整方退药弹窗 -->
    <el-dialog
      v-model="returnDialogVisible"
      title="处方退药"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="return-alert"
        title="整方退药：将退回该发药单全部药品；退药完成后，退费请到收费处办理。"
      />
      <div v-if="returnRow" class="return-rx-line">
        发药单号：{{ returnRow.dispenseNo }}｜处方号：{{ returnRow.rxNo }}｜患者：{{ returnRow.patientName }}
      </div>
      <el-form ref="returnFormRef" :model="returnForm" :rules="returnRules" label-width="80px">
        <el-form-item label="退药原因" prop="reason">
          <el-input
            v-model="returnForm.reason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请输入退药原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="returnSubmitting" @click="handleReturnSubmit">
          确认退药
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  chargeStatusLabel,
  chargeStatusTagType,
  createReturn,
  dispensePrescription,
  getDispensable,
  getDispenseOrderPage,
  getPrescriptions,
  getReturnPage,
  reviewPrescription,
  rxStatusLabel,
  rxStatusTagType,
  type DispenseOrder,
  type ReturnOrder,
  type RxQueueItem,
} from '@/api/pharmacy'
import { fmtMoney } from '@/api/registration'
import { toList } from '@/api/request'

const activeTab = ref('review')

// ---------------- ① 处方审核 ----------------
const rxQueueStatus = ref(10)
const rxList = ref<RxQueueItem[]>([])
const rxLoading = ref(false)

async function fetchPrescriptions() {
  rxLoading.value = true
  try {
    rxList.value = toList<RxQueueItem>(await getPrescriptions(rxQueueStatus.value))
  } finally {
    rxLoading.value = false
  }
}

const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewRx = ref<RxQueueItem | null>(null)
const reviewPass = ref(true)
const reviewFormRef = ref<FormInstance>()
const reviewForm = reactive({ comment: '' })

const reviewRules: FormRules = {
  comment: [{ required: true, message: '请输入审核意见', trigger: 'blur' }],
}

function openReview(row: RxQueueItem, pass: boolean) {
  reviewRx.value = row
  reviewPass.value = pass
  reviewForm.comment = ''
  reviewDialogVisible.value = true
}

async function handleReviewSubmit() {
  if (!reviewRx.value) return
  const valid = await reviewFormRef.value?.validate().catch(() => false)
  if (!valid) return
  reviewSubmitting.value = true
  try {
    await reviewPrescription(reviewRx.value.id, reviewPass.value, reviewForm.comment.trim())
    ElMessage.success(
      reviewPass.value ? `处方 ${reviewRx.value.rxNo} 审核通过` : `处方 ${reviewRx.value.rxNo} 已驳回`
    )
    reviewDialogVisible.value = false
    fetchPrescriptions()
  } catch {
    // B4008 重复审核等已在拦截器统一提示
  } finally {
    reviewSubmitting.value = false
  }
}

// ---------------- ② 可发药 ----------------
const dispensableList = ref<RxQueueItem[]>([])
const dispensableLoading = ref(false)

async function fetchDispensable() {
  dispensableLoading.value = true
  try {
    dispensableList.value = toList<RxQueueItem>(await getDispensable())
  } finally {
    dispensableLoading.value = false
  }
}

function handleDispense(row: RxQueueItem) {
  ElMessageBox.confirm(
    `确认为患者「${row.patientName}」发药？处方 ${row.rxNo}，共 ${(row.items || []).length} 项，金额 ¥${fmtMoney(row.totalAmount)}。`,
    '发药确认',
    { type: 'warning', confirmButtonText: '确认发药', cancelButtonText: '取消' }
  )
    .then(async () => {
      const res = await dispensePrescription(row.id)
      ElMessageBox.alert(`发药单号：${res.dispenseNo}`, '发药成功', {
        type: 'success',
        confirmButtonText: '知道了',
      }).catch(() => {})
      fetchDispensable()
      fetchDispenseOrders()
    })
    .catch(() => {
      // 取消或失败（B4001-B4005 已在拦截器统一提示）
    })
}

// ---------------- ③ 发药记录 ----------------
const dispenseLoading = ref(false)
const dispenseList = ref<DispenseOrder[]>([])
const dispenseTotal = ref(0)
const dispenseDateRange = ref<[string, string] | null>(null)
const dispenseQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  dispenseNo: '',
})

async function fetchDispenseOrders() {
  dispenseLoading.value = true
  try {
    const res = await getDispenseOrderPage({
      pageNum: dispenseQuery.pageNum,
      pageSize: dispenseQuery.pageSize,
      dispenseNo: dispenseQuery.dispenseNo.trim() || undefined,
      startDate: dispenseDateRange.value?.[0] || undefined,
      endDate: dispenseDateRange.value?.[1] || undefined,
    })
    dispenseList.value = res.list ?? []
    dispenseTotal.value = res.total ?? 0
  } finally {
    dispenseLoading.value = false
  }
}

function handleDispenseSearch() {
  dispenseQuery.pageNum = 1
  fetchDispenseOrders()
}

function handleDispenseReset() {
  dispenseQuery.dispenseNo = ''
  dispenseDateRange.value = null
  handleDispenseSearch()
}

function handleDispenseSizeChange() {
  dispenseQuery.pageNum = 1
  fetchDispenseOrders()
}

// ---------------- 整方退药 ----------------
const returnDialogVisible = ref(false)
const returnSubmitting = ref(false)
const returnRow = ref<DispenseOrder | null>(null)
const returnFormRef = ref<FormInstance>()
const returnForm = reactive({ reason: '' })

const returnRules: FormRules = {
  reason: [{ required: true, message: '请输入退药原因', trigger: 'blur' }],
}

function openReturnDialog(row: DispenseOrder) {
  returnRow.value = row
  returnForm.reason = ''
  returnDialogVisible.value = true
}

async function handleReturnSubmit() {
  if (!returnRow.value) return
  const valid = await returnFormRef.value?.validate().catch(() => false)
  if (!valid) return
  returnSubmitting.value = true
  try {
    const res = await createReturn(returnRow.value.id, returnForm.reason.trim())
    ElMessage.success(`整方退药成功，退药单号：${res.returnNo}；退费请到收费处办理`)
    returnDialogVisible.value = false
    fetchDispenseOrders()
    fetchReturns()
  } catch {
    // 拦截器已统一提示
  } finally {
    returnSubmitting.value = false
  }
}

// ---------------- ④ 退药记录 ----------------
const returnLoading = ref(false)
const returnList = ref<ReturnOrder[]>([])
const returnTotal = ref(0)
const returnQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  returnNo: '',
})

async function fetchReturns() {
  returnLoading.value = true
  try {
    const res = await getReturnPage({
      pageNum: returnQuery.pageNum,
      pageSize: returnQuery.pageSize,
      returnNo: returnQuery.returnNo.trim() || undefined,
    })
    returnList.value = res.list ?? []
    returnTotal.value = res.total ?? 0
  } finally {
    returnLoading.value = false
  }
}

function handleReturnSearch() {
  returnQuery.pageNum = 1
  fetchReturns()
}

function handleReturnReset() {
  returnQuery.returnNo = ''
  handleReturnSearch()
}

function handleReturnSizeChange() {
  returnQuery.pageNum = 1
  fetchReturns()
}

// 切换页签时刷新对应数据
watch(activeTab, (tab) => {
  if (tab === 'review') fetchPrescriptions()
  else if (tab === 'dispensable') fetchDispensable()
  else if (tab === 'dispense') fetchDispenseOrders()
  else if (tab === 'returns') fetchReturns()
})

onMounted(() => {
  fetchPrescriptions()
})
</script>

<style scoped>
.expand-wrap {
  padding: 8px 16px 12px 48px;
}

.expand-title {
  margin-bottom: 6px;
  font-size: 13px;
  color: #606266;
}

.review-rx-line,
.return-rx-line {
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.return-alert {
  margin-bottom: 12px;
}
</style>
