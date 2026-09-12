<template>
  <div class="page-card">
    <!-- 搜索栏 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="项目类别">
        <el-select v-model="query.category" placeholder="全部类别" clearable style="width: 150px">
          <el-option
            v-for="opt in CHARGE_ITEM_CATEGORY_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="fetchList">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">收费项目列表</span>
      <el-button v-perm="'basedata:item:manage'" type="primary" :icon="Plus" @click="openCreate">
        新增项目
      </el-button>
    </div>

    <!-- 列表（接口非分页，客户端分页展示） -->
    <el-table v-loading="loading" :data="pagedList" border stripe>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="itemCode" label="项目编码" min-width="110" />
      <el-table-column prop="itemName" label="项目名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="类别" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="categoryTag(row.category)">{{ chargeCategoryLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="price" label="单价(元)" min-width="100" align="right" />
      <el-table-column prop="unit" label="计价单位" width="95" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-perm="'basedata:item:manage'"
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
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">{{ row.updatedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'basedata:item:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="() => (pageNum = 1)"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑收费项目' : '新增收费项目'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="项目编码" prop="itemCode">
          <el-input v-model="form.itemCode" placeholder="字母/数字，如 ITEM006" maxlength="32" />
        </el-form-item>
        <el-form-item label="项目名称" prop="itemName">
          <el-input v-model="form.itemName" maxlength="64" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="form.category" placeholder="请选择类别" style="width: 100%">
            <el-option
              v-for="opt in CHARGE_ITEM_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单价" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0.01"
            :max="999999"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-unit">元</span>
        </el-form-item>
        <el-form-item label="计价单位" prop="unit">
          <el-input v-model="form.unit" maxlength="16" placeholder="默认：次" style="width: 200px" />
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
  CHARGE_ITEM_CATEGORY_OPTIONS,
  chargeCategoryLabel,
  createChargeItem,
  getChargeItemList,
  updateChargeItem,
  type ChargeItem,
} from '@/api/basedata'
import { toList } from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPerm('basedata:item:manage'))

// ---------------- 列表（非分页接口 + 客户端分页） ----------------
const loading = ref(false)
const allList = ref<ChargeItem[]>([])
const pageNum = ref(1)
const pageSize = ref(10)

const query = reactive({
  category: undefined as number | undefined,
  status: undefined as number | undefined,
})

const total = computed(() => allList.value.length)
const pagedList = computed(() =>
  allList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value)
)

async function fetchList() {
  loading.value = true
  try {
    const res = await getChargeItemList({
      category: query.category,
      status: query.status,
    })
    allList.value = toList<ChargeItem>(res)
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.category = undefined
  query.status = undefined
  fetchList()
}

function categoryTag(category: number) {
  const map: Record<number, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    1: 'warning',
    2: 'warning',
    3: 'primary',
    4: 'success',
    5: 'info',
    6: 'info',
    7: 'danger',
  }
  return map[category] ?? 'info'
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  itemCode: '',
  itemName: '',
  category: undefined as number | undefined,
  price: undefined as number | undefined,
  unit: '次',
  status: 1,
})

const formRules: FormRules = {
  itemCode: [
    { required: true, message: '请输入项目编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{1,32}$/, message: '编码仅允许字母与数字', trigger: 'blur' },
  ],
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }],
  price: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入计价单位', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  form.itemCode = ''
  form.itemName = ''
  form.category = undefined
  form.price = undefined
  form.unit = '次'
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: ChargeItem) {
  editingId.value = row.id
  form.itemCode = row.itemCode
  form.itemName = row.itemName
  form.category = row.category
  // 金额后端以字符串返回，编辑时转数值
  form.price = Number(row.price)
  form.unit = row.unit
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateChargeItem(editingId.value, {
        itemCode: form.itemCode,
        itemName: form.itemName,
        category: form.category as number,
        price: Number(form.price),
        unit: form.unit,
        status: form.status,
      })
      ElMessage.success('修改成功')
    } else {
      await createChargeItem({
        itemCode: form.itemCode,
        itemName: form.itemName,
        category: form.category as number,
        price: Number(form.price),
        unit: form.unit,
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
async function handleToggleStatus(row: ChargeItem) {
  const target = row.status === 1 ? 0 : 1
  try {
    await updateChargeItem(row.id, {
      itemCode: row.itemCode,
      itemName: row.itemName,
      category: row.category,
      price: Number(row.price),
      unit: row.unit,
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
