<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="opt in PO_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商">
        <el-select
          v-model="query.supplierId"
          placeholder="全部供应商"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <span class="toolbar-title">采购单</span>
      <el-button v-perm="'whse:po:create'" type="primary" size="small" :icon="Plus" @click="openCreateDialog">
        创建采购单
      </el-button>
    </div>

    <!-- 采购单表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="poNo" label="采购单号" min-width="130" show-overflow-tooltip />
      <el-table-column label="供应商" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ supplierName(row.supplierId) }}</template>
      </el-table-column>
      <el-table-column label="药品" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ drugName(row.drugId) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="80" align="right" />
      <el-table-column label="单价" width="95" align="right">
        <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
      </el-table-column>
      <el-table-column label="预计到货" width="105" align="center">
        <template #default="{ row }">{{ row.expectedDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="85" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="poStatusTagType(row.status)">
            {{ poStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="入库单号" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.inboundNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="审批时间" width="150" align="center">
        <template #default="{ row }">{{ row.approvedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            v-perm="'whse:po:approve'"
            link
            type="primary"
            @click="handleApprove(row)"
          >
            审批
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'whse:po:approve'"
            link
            type="success"
            @click="openReceiveDialog(row)"
          >
            到货入库
          </el-button>
          <el-button
            v-if="row.status === 10 || row.status === 20"
            v-perm="'whse:po:create'"
            link
            type="danger"
            @click="handleCancel(row)"
          >
            取消
          </el-button>
          <span v-if="row.status === 30 || row.status === 40">-</span>
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

    <!-- 创建采购单弹窗 -->
    <el-dialog v-model="createVisible" title="创建采购单" width="520px" destroy-on-close append-to-body>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="药品" prop="drugId">
          <el-select
            v-model="createForm.drugId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchDrugs"
            :loading="drugLoading"
            placeholder="输入药品名称搜索"
            style="width: 100%"
          >
            <el-option v-for="d in drugOptions" :key="d.id" :label="`${d.drugName}（${d.spec}）`" :value="d.id">
              <div class="drug-option">
                <span>{{ d.drugName }}</span>
                <span class="option-sub">{{ d.spec }}｜¥{{ d.retailPrice }}/{{ d.unit }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="createForm.supplierId" filterable placeholder="选择供应商" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number
            v-model="createForm.quantity"
            :min="1"
            :precision="0"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number
            v-model="createForm.unitPrice"
            :min="0"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker
            v-model="createForm.expectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选填"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreateSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 到货入库弹窗 -->
    <el-dialog v-model="receiveVisible" title="到货入库" width="440px" destroy-on-close append-to-body>
      <div v-if="receiveRow" class="receive-line">
        {{ receiveRow.poNo }}｜{{ drugName(receiveRow.drugId) }} × {{ receiveRow.quantity }}
      </div>
      <el-form ref="receiveFormRef" :model="receiveForm" :rules="receiveRules" label-width="80px">
        <el-form-item label="有效期至" prop="expiryDate">
          <el-date-picker
            v-model="receiveForm.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :clearable="false"
            :disabled-date="disablePastDate"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="入库批号取采购单号，入库后库存批次可追溯采购来源。"
      />
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiveSubmitting" @click="handleReceiveSubmit">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { getDrugPage, type Drug } from '@/api/basedata'
import { fmtMoney } from '@/api/registration'
import {
  approvePurchaseOrder,
  cancelPurchaseOrder,
  createPurchaseOrder,
  getPurchaseOrderPage,
  getSupplierList,
  PO_STATUS_OPTIONS,
  poStatusLabel,
  poStatusTagType,
  receivePurchaseOrder,
  type PurchaseOrder,
  type Supplier,
} from '@/api/whse'

// ---------------- 基础数据 ----------------
const suppliers = ref<Supplier[]>([])
const supplierMap = reactive<Record<number, string>>({})

async function fetchSuppliers() {
  try {
    suppliers.value = (await getSupplierList()) ?? []
    suppliers.value.forEach((s) => {
      supplierMap[s.id] = s.supplierName
    })
  } catch {
    suppliers.value = []
  }
}

function supplierName(id?: number | null): string {
  if (id === null || id === undefined) return '-'
  return supplierMap[id] || `供应商ID ${id}`
}

const drugMap = reactive<Record<number, string>>({})

function drugName(id?: number | null): string {
  if (id === null || id === undefined) return '-'
  return drugMap[id] || `药品ID ${id}`
}

// ---------------- 列表查询 ----------------
const loading = ref(false)
const list = ref<PurchaseOrder[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: undefined as number | undefined,
  supplierId: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getPurchaseOrderPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status,
      supplierId: query.supplierId,
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
  query.status = undefined
  query.supplierId = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 创建采购单 ----------------
const createVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref<FormInstance>()
const drugOptions = ref<Drug[]>([])
const drugLoading = ref(false)
const createForm = reactive({
  drugId: undefined as number | undefined,
  supplierId: undefined as number | undefined,
  quantity: 1,
  unitPrice: undefined as number | undefined,
  expectedDate: '',
})

const createRules: FormRules = {
  drugId: [{ required: true, message: '请选择药品', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入采购数量', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
}

/** 远程搜索药品（/basedata/drugs?status=1），结果同时用于表格药品名映射 */
async function searchDrugs(keyword: string) {
  drugLoading.value = true
  try {
    const res = await getDrugPage({
      pageNum: 1,
      pageSize: 200,
      status: 1,
      drugName: keyword.trim() || undefined,
    })
    drugOptions.value = res.list ?? []
    drugOptions.value.forEach((d) => {
      drugMap[d.id] = `${d.drugName}（${d.spec}）`
    })
  } catch {
    drugOptions.value = []
  } finally {
    drugLoading.value = false
  }
}

function openCreateDialog() {
  createForm.drugId = undefined
  createForm.supplierId = undefined
  createForm.quantity = 1
  createForm.unitPrice = undefined
  createForm.expectedDate = ''
  createVisible.value = true
  searchDrugs('')
}

async function handleCreateSubmit() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createSubmitting.value = true
  try {
    const poNo = await createPurchaseOrder({
      supplierId: createForm.supplierId as number,
      drugId: createForm.drugId as number,
      quantity: createForm.quantity,
      unitPrice: createForm.unitPrice as number,
      expectedDate: createForm.expectedDate || undefined,
    })
    ElMessage.success(`采购单创建成功，单号：${poNo}`)
    createVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    createSubmitting.value = false
  }
}

// ---------------- 审批 / 取消 ----------------
async function handleApprove(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认审批通过采购单 ${row.poNo}？审批通过后进入已下单。`, '采购审批', {
      type: 'warning',
      confirmButtonText: '确认审批',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await approvePurchaseOrder(row.id)
    ElMessage.success('审批成功')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

async function handleCancel(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认取消采购单 ${row.poNo}？取消后不可恢复。`, '采购取消', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await cancelPurchaseOrder(row.id)
    ElMessage.success('采购单已取消')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 到货入库 ----------------
const receiveVisible = ref(false)
const receiveSubmitting = ref(false)
const receiveRow = ref<PurchaseOrder | null>(null)
const receiveFormRef = ref<FormInstance>()
const receiveForm = reactive({ expiryDate: '' })

const receiveRules: FormRules = {
  expiryDate: [{ required: true, message: '请选择有效期至', trigger: 'change' }],
}

const todayStart = new Date(new Date().setHours(0, 0, 0, 0)).getTime()
const disablePastDate = (date: Date) => date.getTime() < todayStart

function openReceiveDialog(row: PurchaseOrder) {
  receiveRow.value = row
  receiveForm.expiryDate = ''
  receiveVisible.value = true
}

async function handleReceiveSubmit() {
  const valid = await receiveFormRef.value?.validate().catch(() => false)
  if (!valid || !receiveRow.value) return
  receiveSubmitting.value = true
  try {
    const inboundNo = await receivePurchaseOrder(receiveRow.value.id, receiveForm.expiryDate)
    ElMessage.success(`入库成功，入库单号：${inboundNo}`)
    receiveVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    receiveSubmitting.value = false
  }
}

onMounted(() => {
  fetchSuppliers()
  fetchList()
  searchDrugs('')
})
</script>

<style scoped>
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

.receive-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}
</style>
