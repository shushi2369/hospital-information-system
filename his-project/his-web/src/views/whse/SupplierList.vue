<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="toolbar-title">供应商列表</span>
      <div class="toolbar-right">
        <el-button link type="primary" :icon="Refresh" :loading="loading" @click="fetchList">
          刷新
        </el-button>
        <el-button v-perm="'whse:supplier:manage'" type="primary" :icon="Plus" @click="openCreate">
          新增供应商
        </el-button>
      </div>
    </div>

    <!-- 供应商表格（接口非分页，客户端分页展示） -->
    <el-table v-loading="loading" :data="pagedList" border stripe size="small" empty-text="暂无供应商">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="supplierCode" label="供应商编码" min-width="120" show-overflow-tooltip />
      <el-table-column prop="supplierName" label="供应商名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="contact" label="联系人" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.contact || '-' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.phone || '-' }}</template>
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

    <!-- 新增供应商弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增供应商" width="500px" destroy-on-close append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="供应商编码" prop="supplierCode">
          <el-input v-model="form.supplierCode" placeholder="字母/数字，如 SUP001" maxlength="32" />
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName">
          <el-input v-model="form.supplierName" placeholder="请输入供应商名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系人" prop="contact">
          <el-input v-model="form.contact" placeholder="选填" maxlength="32" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="选填，如 010-12345678" maxlength="20" />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="供应商编码需唯一；新增后可在采购单创建时选用。"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createSupplier, getSupplierList, type Supplier } from '@/api/whse'

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<Supplier[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = computed(() => list.value.length)
const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

async function fetchList() {
  loading.value = true
  try {
    list.value = (await getSupplierList()) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 新增供应商 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  supplierCode: '',
  supplierName: '',
  contact: '',
  phone: '',
})

const rules: FormRules = {
  supplierCode: [
    { required: true, message: '请输入供应商编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线、中划线', trigger: 'blur' },
  ],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  phone: [{ pattern: /^[0-9+\-\s]{5,20}$/, message: '联系电话格式不正确', trigger: 'blur' }],
}

function openCreate() {
  form.supplierCode = ''
  form.supplierName = ''
  form.contact = ''
  form.phone = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const id = await createSupplier({
      supplierCode: form.supplierCode.trim(),
      supplierName: form.supplierName.trim(),
      contact: form.contact.trim() || undefined,
      phone: form.phone.trim() || undefined,
    })
    ElMessage.success(`供应商新增成功，ID：${id ?? '-'}`)
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示（编码重复等）
  } finally {
    submitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
