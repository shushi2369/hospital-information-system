<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="药品名称">
        <el-input
          v-model="query.drugName"
          placeholder="请输入药品名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="药品编码">
        <el-input
          v-model="query.drugCode"
          placeholder="请输入药品编码"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 140px">
          <el-option v-for="opt in DRUG_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">药品列表</span>
      <el-button v-perm="'basedata:drug:manage'" type="primary" :icon="Plus" @click="openCreate">
        新增药品
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="drugCode" label="药品编码" min-width="110" />
      <el-table-column prop="drugName" label="药品名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="genericName" label="通用名" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.genericName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="spec" label="规格" min-width="120" show-overflow-tooltip />
      <el-table-column prop="dosageForm" label="剂型" min-width="100">
        <template #default="{ row }">{{ row.dosageForm || '-' }}</template>
      </el-table-column>
      <el-table-column label="分类" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.category === 1 ? 'primary' : 'success'">
            {{ drugCategoryLabel(row.category) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="manufacturer" label="生产厂家" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.manufacturer || '-' }}</template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="70" align="center" />
      <el-table-column prop="retailPrice" label="零售价(元)" min-width="105" align="right" />
      <el-table-column prop="stockWarningQty" label="库存预警下限" min-width="110" align="right" />
      <el-table-column label="抗菌药物" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isAntibiotic === 1 ? 'danger' : 'info'" size="small">
            {{ row.isAntibiotic === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-perm="'basedata:drug:manage'"
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="() => handleToggleStatus(row)"
          />
          <el-tag v-if="!canManage" :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'basedata:drug:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑药品' : '新增药品'"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="药品编码" prop="drugCode">
          <el-input v-model="form.drugCode" placeholder="字母/数字，如 DRUG006" maxlength="32" />
        </el-form-item>
        <el-form-item label="药品名称" prop="drugName">
          <el-input v-model="form.drugName" maxlength="64" placeholder="请输入药品名称" />
        </el-form-item>
        <el-form-item label="通用名" prop="genericName">
          <el-input v-model="form.genericName" maxlength="64" placeholder="选填" />
        </el-form-item>
        <el-form-item label="规格" prop="spec">
          <el-input v-model="form.spec" maxlength="64" placeholder="如 0.25g×24粒" />
        </el-form-item>
        <el-form-item label="剂型" prop="dosageForm">
          <el-input v-model="form.dosageForm" maxlength="32" placeholder="如 胶囊剂，选填" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="opt in DRUG_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产厂家" prop="manufacturer">
          <el-input v-model="form.manufacturer" maxlength="64" placeholder="选填" />
        </el-form-item>
        <el-form-item label="最小发药单位" prop="unit">
          <el-input v-model="form.unit" maxlength="16" placeholder="如 盒" style="width: 220px" />
        </el-form-item>
        <el-form-item label="零售价" prop="retailPrice">
          <el-input-number
            v-model="form.retailPrice"
            :min="0.01"
            :max="999999"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-unit">元</span>
        </el-form-item>
        <el-form-item label="库存预警下限" prop="stockWarningQty">
          <el-input-number
            v-model="form.stockWarningQty"
            :min="0"
            :max="999999"
            :precision="0"
            :step="10"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="抗菌药物" prop="isAntibiotic">
          <el-switch v-model="form.isAntibiotic" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item v-if="editingId" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createDrug,
  DRUG_CATEGORY_OPTIONS,
  drugCategoryLabel,
  getDrugPage,
  updateDrug,
  type Drug,
} from '@/api/basedata'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPerm('basedata:drug:manage'))

// ---------------- 列表（服务端分页） ----------------
const loading = ref(false)
const list = ref<Drug[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  drugName: '',
  drugCode: '',
  category: undefined as number | undefined,
  status: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getDrugPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      drugName: query.drugName || undefined,
      drugCode: query.drugCode || undefined,
      category: query.category,
      status: query.status,
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
  query.drugName = ''
  query.drugCode = ''
  query.category = undefined
  query.status = undefined
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  drugCode: '',
  drugName: '',
  genericName: '',
  spec: '',
  dosageForm: '',
  category: undefined as number | undefined,
  manufacturer: '',
  unit: '',
  retailPrice: undefined as number | undefined,
  stockWarningQty: 0,
  isAntibiotic: 0,
  status: 1,
})

const formRules: FormRules = {
  drugCode: [
    { required: true, message: '请输入药品编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{1,32}$/, message: '编码仅允许字母与数字', trigger: 'blur' },
  ],
  drugName: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  spec: [{ required: true, message: '请输入规格', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  unit: [{ required: true, message: '请输入最小发药单位', trigger: 'blur' }],
  retailPrice: [{ required: true, message: '请输入零售价', trigger: 'blur' }],
  stockWarningQty: [{ required: true, message: '请输入库存预警下限', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  form.drugCode = ''
  form.drugName = ''
  form.genericName = ''
  form.spec = ''
  form.dosageForm = ''
  form.category = undefined
  form.manufacturer = ''
  form.unit = ''
  form.retailPrice = undefined
  form.stockWarningQty = 0
  form.isAntibiotic = 0
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: Drug) {
  editingId.value = row.id
  form.drugCode = row.drugCode
  form.drugName = row.drugName
  form.genericName = row.genericName ?? ''
  form.spec = row.spec
  form.dosageForm = row.dosageForm ?? ''
  form.category = row.category
  form.manufacturer = row.manufacturer ?? ''
  form.unit = row.unit
  // 金额/数量后端以字符串返回，编辑时转数值
  form.retailPrice = Number(row.retailPrice)
  form.stockWarningQty = Number(row.stockWarningQty)
  form.isAntibiotic = row.isAntibiotic
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateDrug(editingId.value, {
        drugCode: form.drugCode,
        drugName: form.drugName,
        genericName: form.genericName || undefined,
        spec: form.spec,
        dosageForm: form.dosageForm || undefined,
        category: form.category as number,
        manufacturer: form.manufacturer || undefined,
        unit: form.unit,
        retailPrice: Number(form.retailPrice),
        stockWarningQty: Number(form.stockWarningQty),
        isAntibiotic: form.isAntibiotic,
        status: form.status,
      })
      ElMessage.success('修改成功')
    } else {
      await createDrug({
        drugCode: form.drugCode,
        drugName: form.drugName,
        genericName: form.genericName || undefined,
        spec: form.spec,
        dosageForm: form.dosageForm || undefined,
        category: form.category as number,
        manufacturer: form.manufacturer || undefined,
        unit: form.unit,
        retailPrice: Number(form.retailPrice),
        stockWarningQty: Number(form.stockWarningQty),
        isAntibiotic: form.isAntibiotic,
      })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // 错误提示已在拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 启用 / 停用 ----------------
async function handleToggleStatus(row: Drug) {
  const target = row.status === 1 ? 0 : 1
  try {
    await updateDrug(row.id, {
      drugCode: row.drugCode,
      drugName: row.drugName,
      genericName: row.genericName ?? undefined,
      spec: row.spec,
      dosageForm: row.dosageForm ?? undefined,
      category: row.category,
      manufacturer: row.manufacturer ?? undefined,
      unit: row.unit,
      retailPrice: Number(row.retailPrice),
      stockWarningQty: Number(row.stockWarningQty),
      isAntibiotic: row.isAntibiotic,
      status: target,
    })
    row.status = target
    ElMessage.success(target === 1 ? '已启用' : '已停用')
  } catch {
    // 失败保持原状态
  }
}

onMounted(fetchList)
</script>

<style scoped>
.form-unit {
  margin-left: 8px;
  color: #909399;
}
</style>
