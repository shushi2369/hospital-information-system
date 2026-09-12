<template>
  <div class="page-card">
    <el-card shadow="never" class="org-card">
      <template #header>
        <span class="toolbar-title">机构信息</span>
      </template>
      <el-form
        ref="formRef"
        v-loading="loading"
        :model="form"
        :rules="rules"
        label-width="90px"
        style="max-width: 560px"
      >
        <el-form-item label="机构编码">
          <el-input v-model="form.orgCode" disabled />
        </el-form-item>
        <el-form-item label="机构名称" prop="orgName">
          <el-input v-model="form.orgName" maxlength="64" placeholder="请输入机构名称" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" maxlength="128" placeholder="请输入机构地址" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" maxlength="20" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item>
          <el-button v-perm="'basedata:org:manage'" type="primary" :loading="submitting" @click="handleSave">
            保存修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getOrg, updateOrg } from '@/api/basedata'

const loading = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  orgCode: '',
  orgName: '',
  address: '',
  phone: '',
})

const rules: FormRules = {
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  phone: [
    {
      pattern: /^[0-9\-+() ]{0,20}$/,
      message: '电话仅支持数字、-、+ 等字符',
      trigger: 'blur',
    },
  ],
}

async function fetchOrg() {
  loading.value = true
  try {
    const org = await getOrg()
    form.orgCode = org?.orgCode ?? ''
    form.orgName = org?.orgName ?? ''
    form.address = org?.address ?? ''
    form.phone = org?.phone ?? ''
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // orgCode 不可修改，仅提交可维护字段
    await updateOrg({
      orgName: form.orgName,
      address: form.address || undefined,
      phone: form.phone || undefined,
    })
    ElMessage.success('保存成功')
    fetchOrg()
  } catch {
    // 错误提示已在拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(fetchOrg)
</script>

<style scoped>
.org-card {
  max-width: 720px;
}
</style>
