<template>
  <div class="page-card inventory-page">
    <el-tabs v-model="activeTab">
      <!-- ① 库存批次 -->
      <el-tab-pane label="库存批次" name="batches">
        <el-form class="search-bar" :model="batchQuery" inline>
          <el-form-item label="药品">
            <el-select
              v-model="batchQuery.drugId"
              placeholder="全部药品"
              clearable
              filterable
              style="width: 210px"
            >
              <el-option
                v-for="d in drugOptions"
                :key="d.id"
                :label="`${d.drugName}（${d.spec}）`"
                :value="d.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="批号">
            <el-input
              v-model="batchQuery.batchNo"
              placeholder="批号"
              clearable
              style="width: 150px"
              @keyup.enter="handleBatchSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="batchQuery.status" placeholder="全部" clearable style="width: 110px">
              <el-option
                v-for="opt in BATCH_STATUS_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleBatchSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleBatchReset">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="table-toolbar">
          <span class="toolbar-title">库存批次</span>
          <el-button
            v-perm="'inventory:inbound:create'"
            type="primary"
            size="small"
            :icon="Plus"
            @click="openInboundDialog"
          >
            药品入库
          </el-button>
        </div>

        <el-table v-loading="batchLoading" :data="batchList" border stripe size="small">
          <el-table-column prop="drugName" label="药品名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="drugId" label="药品ID" width="80" align="center" />
          <el-table-column prop="batchNo" label="批号" min-width="120" show-overflow-tooltip />
          <el-table-column label="有效期至" width="110" align="center">
            <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="当前库存" width="95" align="right" />
          <el-table-column prop="initialQuantity" label="初始库存" width="95" align="right" />
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="batchStatusTagType(row.status)">
                {{ batchStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="batchQuery.pageNum"
            v-model:page-size="batchQuery.pageSize"
            :total="batchTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleBatchSizeChange"
            @current-change="fetchBatches"
          />
        </div>
      </el-tab-pane>

      <!-- ② 库存预警 -->
      <el-tab-pane label="库存预警" name="warnings">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="warn-tip"
          title="以下药品当前总量已低于或等于预警阈值（红色行），请及时入库补货。"
        />
        <el-table
          v-loading="warnLoading"
          :data="warnings"
          border
          stripe
          size="small"
          :row-class-name="warnRowClass"
          empty-text="库存充足，暂无预警药品"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-wrap">
                <div class="expand-title">批次明细</div>
                <el-table :data="row.batches" border size="small">
                  <el-table-column prop="batchNo" label="批号" min-width="120" show-overflow-tooltip />
                  <el-table-column label="有效期至" width="110" align="center">
                    <template #default="{ row: b }">{{ b.expiryDate || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="quantity" label="当前库存" width="95" align="right" />
                  <el-table-column prop="initialQuantity" label="初始库存" width="95" align="right" />
                  <el-table-column label="状态" width="85" align="center">
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
          <el-table-column prop="drugName" label="药品名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="drugId" label="药品ID" width="90" align="center" />
          <el-table-column label="当前总量" width="110" align="right">
            <template #default="{ row }">
              <span class="warn-qty">{{ row.totalQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stockWarningQty" label="预警阈值" width="100" align="right" />
          <el-table-column label="批次数" width="80" align="center">
            <template #default="{ row }">{{ (row.batches || []).length }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ③ 出入库流水 -->
      <el-tab-pane label="出入库流水" name="movements">
        <el-form class="search-bar" :model="movementQuery" inline>
          <el-form-item label="药品">
            <el-select
              v-model="movementQuery.drugId"
              placeholder="全部药品"
              clearable
              filterable
              style="width: 210px"
            >
              <el-option
                v-for="d in drugOptions"
                :key="d.id"
                :label="`${d.drugName}（${d.spec}）`"
                :value="d.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="单号">
            <el-input
              v-model="movementQuery.refNo"
              placeholder="关联单号"
              clearable
              style="width: 170px"
              @keyup.enter="handleMovementSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleMovementSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleMovementReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="movementLoading" :data="movementList" border stripe size="small">
          <el-table-column label="时间" width="165" align="center">
            <template #default="{ row }">{{ row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column prop="drugName" label="药品名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="95" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="movementTypeTagType(row.movementType)">
                {{ movementTypeLabel(row.movementType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right">
            <template #default="{ row }">
              <span :class="movementQtyClass(row.movementType)">{{ row.quantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库存变化" width="120" align="center">
            <template #default="{ row }">{{ row.beforeQty }} → {{ row.afterQty }}</template>
          </el-table-column>
          <el-table-column label="关联单号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.refNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="来源类型" width="90" align="center">
            <template #default="{ row }">{{ row.refType ?? '-' }}</template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="movementQuery.pageNum"
            v-model:page-size="movementQuery.pageSize"
            :total="movementTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleMovementSizeChange"
            @current-change="fetchMovements"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 药品入库弹窗 -->
    <el-dialog
      v-model="inboundDialogVisible"
      title="药品入库"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-form
        ref="inboundFormRef"
        :model="inboundForm"
        :rules="inboundRules"
        label-width="90px"
      >
        <el-form-item label="药品" prop="drugId">
          <el-select
            v-model="inboundForm.drugId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchInboundDrugs"
            :loading="inboundDrugLoading"
            placeholder="输入药品名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="d in inboundDrugOptions"
              :key="d.id"
              :label="`${d.drugName}（${d.spec}）`"
              :value="d.id"
            >
              <div class="drug-option">
                <span>{{ d.drugName }}</span>
                <span class="option-sub">{{ d.spec }}｜¥{{ d.retailPrice }}/{{ d.unit }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="批号" prop="batchNo">
          <el-input v-model="inboundForm.batchNo" placeholder="如 B20260901" maxlength="32" />
        </el-form-item>
        <el-form-item label="有效期至" prop="expiryDate">
          <el-date-picker
            v-model="inboundForm.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :clearable="false"
            :disabled-date="disablePastDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number
            v-model="inboundForm.quantity"
            :min="1"
            :precision="0"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="入库单价">
          <el-input-number
            v-model="inboundForm.unitPrice"
            :min="0"
            :precision="2"
            controls-position="right"
            placeholder="选填"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="inboundForm.supplier" placeholder="选填" maxlength="128" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inboundDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="inboundSubmitting" @click="handleInboundSubmit">
          确认入库
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { getDrugPage, type Drug } from '@/api/basedata'
import {
  batchStatusLabel,
  batchStatusTagType,
  BATCH_STATUS_OPTIONS,
  createInbound,
  getBatchPage,
  getMovementPage,
  getWarnings,
  movementQtyClass,
  movementTypeLabel,
  movementTypeTagType,
  type InventoryBatch,
  type InventoryMovement,
  type InventoryWarning,
} from '@/api/inventory'
import { toList } from '@/api/request'

const activeTab = ref('batches')

// ---------------- 药品下拉（批次/流水筛选共用） ----------------
const drugOptions = ref<Drug[]>([])

async function fetchDrugOptions() {
  try {
    const res = await getDrugPage({ pageNum: 1, pageSize: 200, status: 1 })
    drugOptions.value = res.list ?? []
  } catch {
    drugOptions.value = []
  }
}

// ---------------- ① 库存批次 ----------------
const batchLoading = ref(false)
const batchList = ref<InventoryBatch[]>([])
const batchTotal = ref(0)
const batchQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  drugId: undefined as number | undefined,
  batchNo: '',
  status: undefined as number | undefined,
})

async function fetchBatches() {
  batchLoading.value = true
  try {
    const res = await getBatchPage({
      pageNum: batchQuery.pageNum,
      pageSize: batchQuery.pageSize,
      drugId: batchQuery.drugId,
      batchNo: batchQuery.batchNo.trim() || undefined,
      status: batchQuery.status,
    })
    batchList.value = res.list ?? []
    batchTotal.value = res.total ?? 0
  } finally {
    batchLoading.value = false
  }
}

function handleBatchSearch() {
  batchQuery.pageNum = 1
  fetchBatches()
}

function handleBatchReset() {
  batchQuery.drugId = undefined
  batchQuery.batchNo = ''
  batchQuery.status = undefined
  handleBatchSearch()
}

function handleBatchSizeChange() {
  batchQuery.pageNum = 1
  fetchBatches()
}

// ---------------- 药品入库 ----------------
const inboundDialogVisible = ref(false)
const inboundSubmitting = ref(false)
const inboundFormRef = ref<FormInstance>()
const inboundDrugOptions = ref<Drug[]>([])
const inboundDrugLoading = ref(false)
const inboundForm = reactive({
  drugId: undefined as number | undefined,
  batchNo: '',
  expiryDate: '',
  quantity: 1,
  unitPrice: undefined as number | undefined,
  supplier: '',
})

const inboundRules: FormRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  batchNo: [{ required: true, message: '请输入批号', trigger: 'blur' }],
  expiryDate: [{ required: true, message: '请选择有效期至', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入入库数量', trigger: 'blur' }],
}

const todayStart = new Date(new Date().setHours(0, 0, 0, 0)).getTime()
const disablePastDate = (date: Date) => date.getTime() < todayStart

/** 远程搜索启用药品（/basedata/drugs?status=1） */
async function searchInboundDrugs(keyword: string) {
  inboundDrugLoading.value = true
  try {
    const res = await getDrugPage({
      pageNum: 1,
      pageSize: 200,
      status: 1,
      drugName: keyword.trim() || undefined,
    })
    inboundDrugOptions.value = res.list ?? []
  } catch {
    inboundDrugOptions.value = []
  } finally {
    inboundDrugLoading.value = false
  }
}

function openInboundDialog() {
  inboundForm.drugId = undefined
  inboundForm.batchNo = ''
  inboundForm.expiryDate = ''
  inboundForm.quantity = 1
  inboundForm.unitPrice = undefined
  inboundForm.supplier = ''
  inboundDialogVisible.value = true
  searchInboundDrugs('')
}

async function handleInboundSubmit() {
  const valid = await inboundFormRef.value?.validate().catch(() => false)
  if (!valid) return
  inboundSubmitting.value = true
  try {
    const res = await createInbound({
      drugId: inboundForm.drugId as number,
      batchNo: inboundForm.batchNo.trim(),
      expiryDate: inboundForm.expiryDate,
      quantity: inboundForm.quantity,
      unitPrice: inboundForm.unitPrice,
      supplier: inboundForm.supplier.trim() || undefined,
    })
    // 后端 data 直接是入库单号字符串（R<String>）
    ElMessage.success(`入库成功，入库单号：${res}`)
    inboundDialogVisible.value = false
    fetchBatches()
  } catch {
    // 拦截器已统一提示
  } finally {
    inboundSubmitting.value = false
  }
}

// ---------------- ② 库存预警 ----------------
const warnLoading = ref(false)
const warnings = ref<InventoryWarning[]>([])

async function fetchWarnings() {
  warnLoading.value = true
  try {
    warnings.value = toList<InventoryWarning>(await getWarnings())
  } finally {
    warnLoading.value = false
  }
}

/** 总量 ≤ 阈值的行红色标注 */
function warnRowClass({ row }: { row: InventoryWarning }): string {
  return Number(row.totalQuantity) <= Number(row.stockWarningQty) ? 'warning-row' : ''
}

// ---------------- ③ 出入库流水 ----------------
const movementLoading = ref(false)
const movementList = ref<InventoryMovement[]>([])
const movementTotal = ref(0)
const movementQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  drugId: undefined as number | undefined,
  refNo: '',
})

async function fetchMovements() {
  movementLoading.value = true
  try {
    const res = await getMovementPage({
      pageNum: movementQuery.pageNum,
      pageSize: movementQuery.pageSize,
      drugId: movementQuery.drugId,
      refNo: movementQuery.refNo.trim() || undefined,
    })
    movementList.value = res.list ?? []
    movementTotal.value = res.total ?? 0
  } finally {
    movementLoading.value = false
  }
}

function handleMovementSearch() {
  movementQuery.pageNum = 1
  fetchMovements()
}

function handleMovementReset() {
  movementQuery.drugId = undefined
  movementQuery.refNo = ''
  handleMovementSearch()
}

function handleMovementSizeChange() {
  movementQuery.pageNum = 1
  fetchMovements()
}

// 切换页签时刷新对应数据
watch(activeTab, (tab) => {
  if (tab === 'batches') fetchBatches()
  else if (tab === 'warnings') fetchWarnings()
  else if (tab === 'movements') fetchMovements()
})

onMounted(() => {
  fetchDrugOptions()
  fetchBatches()
})
</script>

<style scoped>
.warn-tip {
  margin-bottom: 12px;
}

.warn-qty {
  color: #f56c6c;
  font-weight: 700;
}

.expand-wrap {
  padding: 8px 16px 12px 48px;
}

.expand-title {
  margin-bottom: 6px;
  font-size: 13px;
  color: #606266;
}

.qty-in {
  color: #67c23a;
  font-weight: 600;
}

.qty-out {
  color: #f56c6c;
  font-weight: 600;
}

.drug-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.option-sub {
  color: #909399;
  font-size: 12px;
}

/* 预警行红色标注（表格行由 ElTable 渲染，需用 deep 穿透） */
:deep(.warning-row) {
  color: #f56c6c;
  font-weight: 600;
}
</style>
