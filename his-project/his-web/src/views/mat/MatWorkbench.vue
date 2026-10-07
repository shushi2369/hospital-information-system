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
          <el-table-column prop="deptName" label="领用科室" width="120" align="center" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="90" align="center" />
          <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.purpose || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="poVisible" title="新建采购单" width="460px" destroy-on-close>
      <el-form :model="poForm" label-width="90px">
        <el-form-item label="供应商" required>
          <!-- 一百零七轮：裸供应商ID改下拉（后端同步校验存在性） -->
          <el-select v-model="poForm.supplierId" filterable style="width: 100%" placeholder="选择供应商">
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="`${s.supplierCode} ${s.supplierName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="物资" required>
          <el-select v-model="poForm.materialId" filterable style="width: 100%">
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

    <!-- 到货入库弹窗（一百零七轮：批次效期必录，杜绝静默 DEFAULT 批次） -->
    <el-dialog v-model="receiveVisible" title="到货入库" width="440px" destroy-on-close>
      <div v-if="receiveRow" class="dialog-line">采购单号：{{ receiveRow.poNo }}｜数量：{{ receiveRow.quantity }}</div>
      <el-form :model="receiveForm" label-width="90px">
        <el-form-item label="批次号">
          <el-input v-model="receiveForm.batchNo" maxlength="50" placeholder="选填，默认按采购单号派生" />
        </el-form-item>
        <el-form-item label="效期" required>
          <el-date-picker
            v-model="receiveForm.expireDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="(d: Date) => d.getTime() <= Date.now()"
            placeholder="必填，须晚于今天"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiveSubmitting" @click="handleReceiveSubmit">确认入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reqVisible" title="科室领用" width="440px" destroy-on-close>
      <el-form :model="reqForm" label-width="90px">
        <el-form-item label="物资">
          <el-input :model-value="`${reqRow?.materialCode ?? ''} ${reqRow?.name ?? ''}`" disabled />
        </el-form-item>
        <el-form-item label="领用科室" required>
          <!-- 一百零七轮：裸科室ID改下拉 -->
          <el-select v-model="reqForm.deptId" filterable style="width: 100%" placeholder="选择领用科室">
            <el-option v-for="d in deptOptions" :key="d.id" :value="d.id" :label="d.deptName" />
          </el-select>
        </el-form-item>
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
  getMaterialPage, getPurchasePage, getRequisitionPage,
  getSupplierList, matCategoryLabel, matPoStatusLabel, matPoStatusTagType, receivePurchase,
  type BasSupplier,
} from '@/api/mat'
import { getDepartmentListCached, type Department } from '@/api/basedata'

const tab = ref('stock')
const loading = ref(false)
const poLoading = ref(false)
const reqLoading = ref(false)
const materials = ref<Array<Record<string, unknown>>>([])
const purchases = ref<Array<Record<string, unknown>>>([])
const requisitions = ref<Array<Record<string, unknown>>>([])
const batches = ref<Array<Record<string, unknown>>>([])
const batchLoading = ref(false)
// 一百零七轮：供应商/科室下拉数据源
const suppliers = ref<BasSupplier[]>([])
const deptOptions = ref<Department[]>([])

async function fetchMaterials() {
  loading.value = true
  try {
    // 一百零七轮：50 → 200，物资超 50 条时下拉/列表不再截断
    const res = await getMaterialPage({ pageNum: 1, pageSize: 200 })
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
  if (action === 'receive') {
    // 一百零七轮：入库改弹窗录批次效期
    receiveRow.value = row
    receiveForm.batchNo = ''
    receiveForm.expireDate = ''
    receiveVisible.value = true
    return
  }
  const labels = { approve: '审批', receive: '确认入库', cancel: '取消' }
  try {
    await ElMessageBox.confirm(`确认${labels[action]}采购单 ${row.poNo}？`, labels[action])
    if (action === 'approve') await approvePurchase(id)
    else await cancelPurchase(id)
    ElMessage.success('操作成功')
    fetchPo()
    fetchMaterials()
  } catch { /* 取消 */ }
}

// ---------------- 到货入库弹窗（一百零七轮） ----------------
const receiveVisible = ref(false)
const receiveSubmitting = ref(false)
const receiveRow = ref<Record<string, unknown> | null>(null)
const receiveForm = reactive({ batchNo: '', expireDate: '' })

async function handleReceiveSubmit() {
  if (!receiveRow.value) return
  if (!receiveForm.expireDate) {
    ElMessage.warning('请录入批次效期（须晚于今天）')
    return
  }
  receiveSubmitting.value = true
  try {
    await receivePurchase(
      receiveRow.value.id as number,
      receiveForm.batchNo.trim() || undefined,
      receiveForm.expireDate
    )
    ElMessage.success('入库成功，批次已登记')
    receiveVisible.value = false
    fetchPo()
    fetchMaterials()
    fetchBatches()
  } catch {
    // 拦截器已统一提示
  } finally {
    receiveSubmitting.value = false
  }
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

onMounted(() => {
  fetchMaterials(); fetchPo(); fetchReq(); fetchBatches()
  getSupplierList().then((s) => { suppliers.value = s ?? [] }).catch(() => { suppliers.value = [] })
  getDepartmentListCached({ status: 1 }).then((d) => { deptOptions.value = d ?? [] }).catch(() => { deptOptions.value = [] })
})
</script>

<style scoped>
.dialog-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}
</style>
