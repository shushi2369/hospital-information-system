<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="物资与库存" name="stock">
        <el-table v-loading="loading" :data="materials" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="materialCode" label="编码" min-width="120" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column label="类别" width="100" align="center">
            <template #default="{ row }">{{ matCategoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="price" label="单价" width="90" align="right" />
          <el-table-column label="库存" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.lowStock ? 'danger' : 'success'">{{ row.quantity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="safeStock" label="安全库存" width="90" align="center" />
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'mat:requisition:create'" link type="primary" @click="openReq(row)">领用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="采购单" name="purchase">
        <div class="table-toolbar">
          <span class="toolbar-title">采购单</span>
          <el-button v-perm="'mat:purchase:create'" type="primary" @click="resetPoForm(); poVisible = true">新建采购</el-button>
        </div>
        <el-table v-loading="poLoading" :data="purchases" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="poNo" label="采购单号" min-width="140" />
          <el-table-column prop="materialId" label="物资ID" width="90" align="center" />
          <el-table-column prop="quantity" label="数量" width="90" align="center" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="matPoStatusTagType(row.status)">{{ matPoStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 10" v-perm="'mat:purchase:approve'" link type="warning" @click="handlePo(row, 'approve')">审批</el-button>
              <el-button v-if="row.status === 20" v-perm="'mat:purchase:approve'" link type="success" @click="handlePo(row, 'receive')">入库</el-button>
              <el-button v-if="[10, 20].includes(row.status)" v-perm="'mat:purchase:create'" link type="danger" @click="handlePo(row, 'cancel')">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="批次效期" name="batch">
        <el-table v-loading="batchLoading" :data="batches" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="materialId" label="物资ID" width="90" align="center" />
          <el-table-column prop="batchNo" label="批次号" min-width="140" />
          <el-table-column prop="expireDate" label="效期" width="110" align="center" />
          <el-table-column prop="quantity" label="数量" width="90" align="center" />
          <el-table-column label="效期预警" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.expireSoon" size="small" type="danger">≤30天</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="领用流水" name="req">
        <el-table v-loading="reqLoading" :data="requisitions" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="reqNo" label="领用单号" min-width="140" />
          <el-table-column prop="materialId" label="物资ID" width="90" align="center" />
          <el-table-column prop="deptId" label="科室ID" width="90" align="center" />
          <el-table-column prop="quantity" label="数量" width="90" align="center" />
          <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.purpose || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="poVisible" title="新建采购单" width="460px" destroy-on-close>
      <el-form :model="poForm" label-width="90px">
        <el-form-item label="供应商ID" required><el-input-number v-model="poForm.supplierId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="物资" required>
          <el-select v-model="poForm.materialId" style="width: 100%">
            <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required><el-input-number v-model="poForm.quantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="单价" required><el-input-number v-model="poForm.unitPrice" :min="0.01" :precision="2" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreatePo">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reqVisible" title="科室领用" width="440px" destroy-on-close>
      <el-form :model="reqForm" label-width="90px">
        <el-form-item label="物资">
          <el-input :model-value="`${reqRow?.materialCode ?? ''} ${reqRow?.name ?? ''}`" disabled />
        </el-form-item>
        <el-form-item label="领用科室ID" required><el-input-number v-model="reqForm.deptId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="数量" required><el-input-number v-model="reqForm.quantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="reqForm.purpose" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reqVisible = false">取消</el-button>
        <el-button type="primary" @click="handleReq">领用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approvePurchase, cancelPurchase, createPurchase, createRequisition, getBatches,
  getMaterialPage, getPurchasePage, getRequisitionPage, matCategoryLabel,
  matPoStatusLabel, matPoStatusTagType, receivePurchase,
} from '@/api/mat'

const tab = ref('stock')
const loading = ref(false)
const poLoading = ref(false)
const reqLoading = ref(false)
const materials = ref<Array<Record<string, unknown>>>([])
const purchases = ref<Array<Record<string, unknown>>>([])
const requisitions = ref<Array<Record<string, unknown>>>([])
const batches = ref<Array<Record<string, unknown>>>([])
const batchLoading = ref(false)

async function fetchMaterials() {
  loading.value = true
  try {
    const res = await getMaterialPage({ pageNum: 1, pageSize: 50 })
    materials.value = res.list ?? []
  } finally { loading.value = false }
}
async function fetchPo() {
  poLoading.value = true
  try {
    const res = await getPurchasePage({ pageNum: 1, pageSize: 20 })
    purchases.value = res.list ?? []
  } finally { poLoading.value = false }
}
async function fetchReq() {
  reqLoading.value = true
  try {
    const res = await getRequisitionPage({ pageNum: 1, pageSize: 20 })
    requisitions.value = res.list ?? []
  } finally { reqLoading.value = false }
}

const poVisible = ref(false)
function resetPoForm() {
  Object.assign(poForm, { supplierId: undefined, materialId: undefined, quantity: 100, unitPrice: 1 })
}
const poForm = reactive<{ supplierId?: number; materialId?: number; quantity: number; unitPrice: number }>({ supplierId: undefined, materialId: undefined, quantity: 100, unitPrice: 1 })

async function handleCreatePo() {
  if (!poForm.supplierId || !poForm.materialId) {
    ElMessage.warning('请选择供应商与物资')
    return
  }
  await createPurchase({
    supplierId: poForm.supplierId, materialId: poForm.materialId,
    quantity: poForm.quantity, unitPrice: poForm.unitPrice,
  })
  ElMessage.success('采购单已创建')
  poVisible.value = false
  fetchPo()
}

async function handlePo(row: Record<string, unknown>, action: 'approve' | 'receive' | 'cancel') {
  const id = row.id as number
  const labels = { approve: '审批', receive: '确认入库', cancel: '取消' }
  try {
    await ElMessageBox.confirm(`确认${labels[action]}采购单 ${row.poNo}？`, labels[action])
    if (action === 'approve') await approvePurchase(id)
    else if (action === 'receive') await receivePurchase(id)
    else await cancelPurchase(id)
    ElMessage.success('操作成功')
    fetchPo()
    fetchMaterials()
  } catch { /* 取消 */ }
}

const reqVisible = ref(false)
const reqRow = ref<Record<string, unknown> | null>(null)
const reqForm = reactive<{ deptId?: number; quantity: number; purpose: string }>({ deptId: undefined, quantity: 10, purpose: '' })

function openReq(row: Record<string, unknown>) {
  reqRow.value = row
  reqForm.deptId = undefined
  reqForm.quantity = 10
  reqForm.purpose = ''
  reqVisible.value = true
}

async function handleReq() {
  if (!reqForm.deptId || !reqRow.value) {
    ElMessage.warning('请填写领用科室')
    return
  }
  await createRequisition({ materialId: reqRow.value.id as number, deptId: reqForm.deptId, quantity: reqForm.quantity, purpose: reqForm.purpose || undefined })
  ElMessage.success('领用成功')
  reqVisible.value = false
  fetchMaterials()
  fetchReq()
}

async function fetchBatches() {
  batchLoading.value = true
  try { batches.value = await getBatches() } finally { batchLoading.value = false }
}

onMounted(() => { fetchMaterials(); fetchPo(); fetchReq(); fetchBatches() })
</script>
