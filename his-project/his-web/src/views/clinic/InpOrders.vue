<template>
  <div class="page-card">
    <!-- 顶部：在院患者选择 -->
    <el-form class="search-bar" inline>
      <el-form-item label="在院患者">
        <el-select
          v-model="admissionId"
          filterable
          remote
          :remote-method="searchAdmissions"
          :loading="admSearching"
          placeholder="输入住院号搜索在院患者"
          style="width: 330px"
          @change="fetchOrders"
          @visible-change="(v: boolean) => v && !admissionId && searchAdmissions('')"
        >
          <el-option
            v-for="a in admissionOptions"
            :key="a.id"
            :label="`${a.admissionNo}｜${a.patientName}｜${a.wardName || '-'} ${a.bedNo || ''}`"
            :value="a.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          v-perm="'doc:order:create'"
          type="primary"
          :icon="Plus"
          :disabled="!admissionId"
          @click="openOrderDialog"
        >
          开医嘱
        </el-button>
      </el-form-item>
    </el-form>

    <el-tabs v-model="activeTab">
      <!-- ① 医嘱列表 -->
      <el-tab-pane label="医嘱列表" name="orders">
        <el-form class="search-bar" inline>
          <el-form-item label="类别">
            <el-select v-model="filterCategory" placeholder="全部类别" clearable style="width: 110px">
              <el-option v-for="o in ORDER_CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 120px">
              <el-option v-for="o in ORDER_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" :disabled="!admissionId" @click="fetchOrders">查询</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="orderLoading" :data="orders" border stripe size="small">
          <el-table-column prop="orderNo" label="医嘱号" min-width="140" show-overflow-tooltip />
          <el-table-column label="长短" width="65" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.orderClass === 1 ? 'primary' : 'info'">
                {{ orderClassLabel(row.orderClass) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类别" width="75" align="center">
            <template #default="{ row }">{{ orderCategoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column prop="frequency" label="频次" width="80" align="center">
            <template #default="{ row }">{{ row.frequency || '-' }}</template>
          </el-table-column>
          <el-table-column prop="doctorName" label="医生" min-width="85" show-overflow-tooltip>
            <template #default="{ row }">{{ row.doctorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="orderStatusTagType(row.status)">
                {{ orderStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="开立时间" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 10"
                v-perm="'pharmacy:review:do'"
                link
                type="success"
                @click="openReviewDialog(row)"
              >
                审核
              </el-button>
              <el-button
                v-if="row.status === 20 && row.category === 1"
                v-perm="'pharmacy:dispense:do'"
                link
                type="primary"
                @click="handleDispense(row)"
              >
                摆药
              </el-button>
              <el-button
                v-if="row.status === 20 || row.status === 30"
                v-perm="'doc:order:stop'"
                link
                type="warning"
                @click="handleStop(row)"
              >
                停嘱
              </el-button>
              <el-button
                v-if="row.status === 50"
                v-perm="'doc:order:stop'"
                link
                type="success"
                @click="handleResume(row)"
              >
                恢复
              </el-button>
              <el-button
                v-if="row.status === 10 || row.status === 20"
                v-perm="'doc:order:create'"
                link
                type="danger"
                @click="handleVoid(row)"
              >
                作废
              </el-button>
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="orderQuery.pageNum"
            v-model:page-size="orderQuery.pageSize"
            :total="orderTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @size-change="handleOrderSizeChange"
            @current-change="fetchOrders"
          />
        </div>
      </el-tab-pane>

      <!-- ② 待审核队列（药师） -->
      <el-tab-pane label="审核队列" name="review">
        <div class="table-toolbar">
          <span class="toolbar-title">待审核医嘱</span>
          <el-button link type="primary" :icon="Refresh" :loading="reviewLoading" @click="fetchReviewQueue">
            刷新
          </el-button>
        </div>
        <el-table v-loading="reviewLoading" :data="reviewQueue" border stripe size="small">
          <el-table-column prop="orderNo" label="医嘱号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" min-width="85" show-overflow-tooltip>
            <template #default="{ row }">{{ row.patientName || '-' }}</template>
          </el-table-column>
          <el-table-column label="类别" width="75" align="center">
            <template #default="{ row }">{{ orderCategoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column label="皮试" width="65" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.skinTestFlag" size="small" type="danger">需皮试</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="doctorName" label="医生" min-width="85" show-overflow-tooltip>
            <template #default="{ row }">{{ row.doctorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center">
            <template #default="{ row }">
              <el-button
                v-perm="'pharmacy:review:do'"
                link
                type="success"
                @click="openReviewDialog(row)"
              >
                审核
              </el-button>
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 开医嘱弹窗 -->
    <el-dialog
      v-model="orderDialogVisible"
      title="开医嘱"
      width="1080px"
      top="5vh"
      destroy-on-close
      append-to-body
    >
      <el-form :model="orderForm" label-width="90px" class="order-form-head">
        <el-form-item label="医嘱类别">
          <el-radio-group v-model="orderForm.category" @change="handleCategoryChange">
            <el-radio-button v-for="o in ORDER_CATEGORY_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="长短嘱">
          <el-radio-group v-model="orderForm.orderClass">
            <el-radio v-for="o in ORDER_CLASS_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="orderForm.category === 1" label="用药频次">
          <el-select v-model="orderForm.frequency" placeholder="选择频次" style="width: 200px" clearable>
            <el-option v-for="f in ORDER_FREQUENCY_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
          <el-switch
            v-model="needSkinTest"
            active-text="需皮试"
            inactive-text=""
            class="skin-switch"
          />
        </el-form-item>
      </el-form>

      <div class="rx-dialog-toolbar">
        <el-button type="primary" plain size="small" :icon="Plus" @click="addRow">添加明细</el-button>
        <span class="rx-dialog-tip">
          {{ orderForm.category === 1 ? '药品需选择药品并填写数量' : '项目需选择收费项目并填写数量' }}
        </span>
      </div>
      <el-table :data="rows" border size="small" max-height="380">
        <!-- 药品列 -->
        <el-table-column v-if="orderForm.category === 1" label="药品" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.drugId"
              filterable
              :loading="drugLoading"
              placeholder="选择药品"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="d in drugOptions"
                :key="d.id"
                :label="`${d.drugName}（${d.spec}）`"
                :value="d.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <!-- 非药品列 -->
        <el-table-column v-else label="项目" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.chargeItemId"
              filterable
              :loading="itemLoading"
              placeholder="选择收费项目"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="i in itemOptions"
                :key="i.id"
                :label="`${i.itemName}（¥${i.price}/${i.unit}）`"
                :value="i.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="剂量" width="110">
          <template #default="{ row }">
            <el-input
              v-if="orderForm.category === 1"
              v-model="row.dosage"
              placeholder="如 0.5g/次"
              size="small"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="用法" width="95">
          <template #default="{ row }">
            <el-select
              v-if="orderForm.category === 1"
              v-model="row.usageRoute"
              placeholder="用法"
              size="small"
              style="width: 100%"
              clearable
            >
              <el-option v-for="u in USAGE_ROUTE_OPTIONS" :key="u.value" :label="u.label" :value="u.value" />
            </el-select>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="天数" width="115">
          <template #default="{ row }">
            <el-input-number
              v-if="orderForm.category === 1"
              v-model="row.days"
              :min="1"
              :max="90"
              :precision="0"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="999"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="嘱托" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.usageNote" placeholder="选填" size="small" maxlength="128" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orderSubmitting" @click="handleOrderSubmit">
          确认开立
        </el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="医嘱审核" width="460px" destroy-on-close append-to-body>
      <div v-if="reviewRow" class="review-line">医嘱号：{{ reviewRow.orderNo }}</div>
      <el-form label-width="80px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="reviewPass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="2"
            maxlength="200"
            placeholder="选填；驳回时建议填写原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="handleReviewSubmit">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 医嘱详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="医嘱详情" size="720px" destroy-on-close append-to-body>
      <div v-loading="drawerLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="医嘱号">{{ detail.order.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="orderStatusTagType(detail.order.status)">
                {{ orderStatusLabel(detail.order.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="长短嘱">{{ orderClassLabel(detail.order.orderClass) }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ orderCategoryLabel(detail.order.category) }}</el-descriptions-item>
            <el-descriptions-item label="频次">{{ detail.order.frequency || '-' }}</el-descriptions-item>
            <el-descriptions-item label="皮试">{{ detail.order.skinTestFlag ? '需皮试' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="医生">{{ detail.order.doctorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="金额">¥{{ fmtMoney(detail.order.totalAmount) }}</el-descriptions-item>
          </el-descriptions>

          <div class="drawer-section">医嘱明细</div>
          <el-table :data="detail.items" border size="small">
            <el-table-column label="名称" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.drugName || row.itemName || '-' }}</template>
            </el-table-column>
            <el-table-column label="剂量" width="85" align="center">
              <template #default="{ row }">{{ row.dosage || '-' }}</template>
            </el-table-column>
            <el-table-column label="用法" width="75" align="center">
              <template #default="{ row }">{{ row.usageRoute || '-' }}</template>
            </el-table-column>
            <el-table-column label="天数" width="60" align="center">
              <template #default="{ row }">{{ row.days ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="65" align="center" />
            <el-table-column label="单价" width="85" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="嘱托" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.usageNote || '-' }}</template>
            </el-table-column>
          </el-table>

          <div class="drawer-section">执行记录</div>
          <el-table :data="detail.executions" border size="small" empty-text="暂无执行记录">
            <el-table-column prop="execDate" label="执行日期" width="110" align="center">
              <template #default="{ row }">{{ row.execDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="execSlot" label="时段" width="90" align="center">
              <template #default="{ row }">{{ row.execSlot || '-' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.execType" size="small" :type="execTypeTagType(row.execType)">
                  {{ execTypeLabel(row.execType) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.status" size="small" :type="execStatusTagType(row.status)">
                  {{ execStatusLabel(row.status) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="护士" min-width="85" show-overflow-tooltip>
              <template #default="{ row }">{{ row.nurseName || '-' }}</template>
            </el-table-column>
            <el-table-column label="执行时间" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.execTime || '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { getChargeItemList, getDrugPage, type ChargeItem, type Drug } from '@/api/basedata'
import { USAGE_ROUTE_OPTIONS } from '@/api/clinic'
import { fmtMoney } from '@/api/registration'
import {
  createOrder,
  dispenseOrder,
  execStatusLabel,
  execStatusTagType,
  execTypeLabel,
  execTypeTagType,
  getOrderDetail,
  getOrderPage,
  getOrderReviewQueue,
  orderCategoryLabel,
  orderClassLabel,
  ORDER_CATEGORY_OPTIONS,
  ORDER_CLASS_OPTIONS,
  ORDER_STATUS_OPTIONS,
  orderStatusLabel,
  orderStatusTagType,
  resumeOrder,
  reviewOrder,
  stopOrder,
  voidOrder,
  type InpOrder,
  type OrderDetail,
} from '@/api/doc'
import { getAdmissionPage, type Admission } from '@/api/inp'
import { ORDER_FREQUENCY_OPTIONS } from '@/api/doc'

// 医嘱类别 → 收费项目类别映射（检查→3 检验→4 治疗/护理→5 材料→6）
function chargeCategoryOf(docCategory: number): number {
  switch (docCategory) {
    case 2:
      return 3
    case 3:
      return 4
    case 6:
      return 6
    default:
      return 5
  }
}

// ---------------- 在院患者远程选择 ----------------
const admissionId = ref<number | undefined>(undefined)
const admissionOptions = ref<Admission[]>([])
const admSearching = ref(false)

async function searchAdmissions(kw: string) {
  admSearching.value = true
  try {
    const res = await getAdmissionPage({
      pageNum: 1,
      pageSize: 50,
      status: 10,
      admissionNo: kw.trim() || undefined,
    })
    admissionOptions.value = res.list ?? []
  } catch {
    admissionOptions.value = []
  } finally {
    admSearching.value = false
  }
}

// ---------------- 医嘱列表 ----------------
const activeTab = ref('orders')
const orderLoading = ref(false)
const orders = ref<InpOrder[]>([])
const orderTotal = ref(0)
const filterCategory = ref<number | undefined>(undefined)
const filterStatus = ref<number | undefined>(undefined)
const orderQuery = reactive({ pageNum: 1, pageSize: 10 })

async function fetchOrders() {
  if (!admissionId.value) {
    orders.value = []
    orderTotal.value = 0
    return
  }
  orderLoading.value = true
  try {
    const res = await getOrderPage({
      pageNum: orderQuery.pageNum,
      pageSize: orderQuery.pageSize,
      admissionId: admissionId.value,
      category: filterCategory.value,
      status: filterStatus.value,
    })
    orders.value = res.list ?? []
    orderTotal.value = res.total ?? 0
  } finally {
    orderLoading.value = false
  }
}

function handleOrderSizeChange() {
  orderQuery.pageNum = 1
  fetchOrders()
}

// ---------------- 审核队列 ----------------
const reviewLoading = ref(false)
const reviewQueue = ref<InpOrder[]>([])

async function fetchReviewQueue() {
  reviewLoading.value = true
  try {
    reviewQueue.value = (await getOrderReviewQueue()) ?? []
  } catch {
    reviewQueue.value = []
  } finally {
    reviewLoading.value = false
  }
}

// ---------------- 开医嘱 ----------------
interface OrderRow {
  drugId: number | undefined
  chargeItemId: number | undefined
  dosage: string
  days: number | undefined
  quantity: number | undefined
  usageRoute: string
  usageNote: string
}

const orderDialogVisible = ref(false)
const orderSubmitting = ref(false)
const orderForm = reactive({
  orderClass: 2,
  category: 1,
  frequency: '',
})
const needSkinTest = ref(false)
const rows = ref<OrderRow[]>([])
const drugOptions = ref<Drug[]>([])
const drugLoading = ref(false)
const itemOptions = ref<ChargeItem[]>([])
const itemLoading = ref(false)

function emptyRow(): OrderRow {
  return {
    drugId: undefined,
    chargeItemId: undefined,
    dosage: '',
    days: undefined,
    quantity: undefined,
    usageRoute: '',
    usageNote: '',
  }
}

function addRow() {
  rows.value.push(emptyRow())
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
}

async function fetchDrugs() {
  if (drugOptions.value.length > 0) return
  drugLoading.value = true
  try {
    const res = await getDrugPage({ pageNum: 1, pageSize: 200, status: 1 })
    drugOptions.value = res.list ?? []
  } catch {
    drugOptions.value = []
  } finally {
    drugLoading.value = false
  }
}

async function fetchItems(docCategory: number) {
  itemLoading.value = true
  try {
    itemOptions.value =
      (await getChargeItemList({ category: chargeCategoryOf(docCategory), status: 1 })) ?? []
  } catch {
    itemOptions.value = []
  } finally {
    itemLoading.value = false
  }
}

function handleCategoryChange() {
  rows.value = [emptyRow()]
  needSkinTest.value = false
  if (orderForm.category !== 1) {
    orderForm.frequency = ''
    fetchItems(orderForm.category)
  }
}

async function openOrderDialog() {
  if (!admissionId.value) return
  orderForm.orderClass = 2
  orderForm.category = 1
  orderForm.frequency = ''
  needSkinTest.value = false
  rows.value = [emptyRow()]
  orderDialogVisible.value = true
  await fetchDrugs()
}

function isRowTouched(r: OrderRow): boolean {
  return !!(r.drugId || r.chargeItemId || r.dosage.trim() || r.usageNote.trim() || r.quantity)
}

async function handleOrderSubmit() {
  if (!admissionId.value) return
  const touched = rows.value.filter(isRowTouched)
  if (touched.length === 0) {
    ElMessage.warning('请至少填写一行医嘱明细')
    return
  }
  const isDrug = orderForm.category === 1
  for (const r of touched) {
    if (isDrug && !r.drugId) {
      ElMessage.warning('请为药品明细选择药品')
      return
    }
    if (!isDrug && !r.chargeItemId) {
      ElMessage.warning('请为项目明细选择收费项目')
      return
    }
    if (!r.quantity || r.quantity <= 0) {
      ElMessage.warning('请填写明细数量')
      return
    }
  }
  if (isDrug && !orderForm.frequency) {
    ElMessage.warning('药品医嘱请选择用药频次')
    return
  }
  orderSubmitting.value = true
  try {
    const orderNo = await createOrder({
      admissionId: admissionId.value,
      orderClass: orderForm.orderClass,
      category: orderForm.category,
      frequency: isDrug ? orderForm.frequency : undefined,
      skinTestFlag: isDrug ? needSkinTest.value : undefined,
      items: touched.map((r) => ({
        drugId: isDrug ? r.drugId : undefined,
        chargeItemId: isDrug ? undefined : r.chargeItemId,
        dosage: isDrug && r.dosage.trim() ? r.dosage.trim() : undefined,
        days: isDrug && r.days ? Number(r.days) : undefined,
        quantity: Number(r.quantity),
        usageRoute: isDrug && r.usageRoute ? r.usageRoute : undefined,
        usageNote: r.usageNote.trim() || undefined,
      })),
    })
    ElMessage.success(`医嘱开立成功，医嘱号：${orderNo}`)
    orderDialogVisible.value = false
    fetchOrders()
    fetchReviewQueue()
  } catch {
    // 拦截器已统一提示
  } finally {
    orderSubmitting.value = false
  }
}

// ---------------- 审核 ----------------
const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewRow = ref<InpOrder | null>(null)
const reviewPass = ref(true)
const reviewComment = ref('')

function openReviewDialog(row: InpOrder) {
  reviewRow.value = row
  reviewPass.value = true
  reviewComment.value = ''
  reviewDialogVisible.value = true
}

async function handleReviewSubmit() {
  if (!reviewRow.value) return
  if (!reviewPass.value && !reviewComment.value.trim()) {
    ElMessage.warning('驳回时请填写审核意见')
    return
  }
  reviewSubmitting.value = true
  try {
    await reviewOrder(reviewRow.value.id, reviewPass.value, reviewComment.value.trim() || undefined)
    ElMessage.success(reviewPass.value ? '审核通过' : '已驳回')
    reviewDialogVisible.value = false
    if (activeTab.value === 'review') fetchReviewQueue()
    fetchOrders()
  } catch {
    // 拦截器已统一提示
  } finally {
    reviewSubmitting.value = false
  }
}

// ---------------- 摆药 ----------------
function handleDispense(row: InpOrder) {
  ElMessageBox.confirm(`确认为医嘱 ${row.orderNo} 摆药出库？皮试未通过的医嘱将被拦截。`, '摆药确认', {
    type: 'warning',
    confirmButtonText: '确认摆药',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await dispenseOrder(row.id)
      ElMessage.success('摆药出库成功')
      fetchOrders()
    })
    .catch(() => {
      // 取消或 B6104 拦截（拦截器已提示）
    })
}

// ---------------- 停嘱 / 恢复 / 作废 ----------------
function handleStop(row: InpOrder) {
  ElMessageBox.prompt('请输入停嘱原因（必填）', '停止医嘱', {
    type: 'warning',
    confirmButtonText: '确认停嘱',
    cancelButtonText: '取消',
    inputValidator: (v: string) => (v && v.trim() ? true : '请输入停嘱原因'),
  })
    .then(async ({ value }) => {
      await stopOrder(row.id, value.trim())
      ElMessage.success('医嘱已停止')
      fetchOrders()
    })
    .catch(() => {
      // 取消
    })
}

function handleResume(row: InpOrder) {
  ElMessageBox.confirm(`确认恢复执行医嘱 ${row.orderNo}？`, '恢复医嘱', {
    type: 'warning',
    confirmButtonText: '确认恢复',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await resumeOrder(row.id)
      ElMessage.success('医嘱已恢复')
      fetchOrders()
    })
    .catch(() => {
      // 取消
    })
}

function handleVoid(row: InpOrder) {
  ElMessageBox.prompt('请输入作废原因（选填）', '作废医嘱', {
    type: 'warning',
    confirmButtonText: '确认作废',
    cancelButtonText: '取消',
  })
    .then(async ({ value }) => {
      await voidOrder(row.id, value?.trim() || undefined)
      ElMessage.success('医嘱已作废')
      fetchOrders()
      fetchReviewQueue()
    })
    .catch(() => {
      // 取消
    })
}

// ---------------- 详情抽屉 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const detail = ref<OrderDetail | null>(null)

async function openDetail(row: InpOrder) {
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetail(row.id)
  } catch {
    detail.value = null
  } finally {
    drawerLoading.value = false
  }
}

onMounted(() => {
  searchAdmissions('')
  fetchReviewQueue()
})
</script>

<style scoped>
.order-form-head :deep(.el-form-item) {
  margin-bottom: 12px;
}

.skin-switch {
  margin-left: 16px;
}

.rx-dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.rx-dialog-tip {
  color: #909399;
  font-size: 12px;
}

.review-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}

.drawer-section {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}
</style>
