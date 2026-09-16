<template>
  <div class="page-card">
    <!-- 住院选择 -->
    <el-form class="search-bar" inline>
      <el-form-item label="在院患者">
        <el-select
          v-model="admissionId"
          filterable
          remote
          :remote-method="searchAdmissions"
          :loading="admSearching"
          placeholder="输入住院号搜索在院患者"
          style="width: 320px"
          @change="fetchFees"
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
        <el-button :icon="Refresh" :disabled="!admissionId" @click="fetchFees">刷新</el-button>
      </el-form-item>
      <el-form-item>
        <el-button
          v-perm="'inp:fee:create'"
          type="primary"
          :icon="Plus"
          :disabled="!admissionId"
          @click="openManualDialog"
        >
          手工记账
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 一日清分组表 -->
    <div v-loading="loading" class="fee-area">
      <el-empty
        v-if="!loading && groups.length === 0"
        :description="admissionId ? '该住院暂无费用记录' : '请先选择在院患者'"
      />
      <div v-for="g in groups" :key="g.feeDate" class="fee-group">
        <div class="fee-group-head">
          <span class="fee-date">{{ g.feeDate }}</span>
          <span class="fee-total">日合计：<em>¥{{ fmtMoney(g.totalAmount) }}</em></span>
        </div>
        <el-table :data="g.items" border stripe size="small">
          <el-table-column type="index" label="#" width="45" align="center" />
          <el-table-column prop="itemName" label="项目名称" min-width="170" show-overflow-tooltip />
          <el-table-column label="费用类别" width="95" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="feeTypeTagType(row.feeType)">
                {{ inpFeeTypeLabel(row.feeType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="95" align="center">
            <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="75" align="center" />
          <el-table-column label="单价" width="95" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 手工记账弹窗 -->
    <el-dialog v-model="dialogVisible" title="手工记账" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="费用类别" prop="feeType">
          <el-select v-model="form.feeType" placeholder="选择费用类别" style="width: 100%">
            <el-option v-for="o in MANUAL_FEE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称" prop="itemName">
          <el-input v-model="form.itemName" placeholder="请输入项目名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number
            v-model="form.quantity"
            :min="1"
            :precision="0"
            controls-position="right"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number
            v-model="form.unitPrice"
            :min="0.01"
            :precision="2"
            controls-position="right"
            style="width: 160px"
          />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { feeTypeTagType, sourceTypeLabel } from '@/api/billing'
import { fmtMoney } from '@/api/registration'
import {
  createManualFee,
  getAdmissionPage,
  getDailyFees,
  INP_FEE_TYPE_OPTIONS,
  inpFeeTypeLabel,
  type Admission,
  type DailyFeeGroup,
} from '@/api/inp'

// 手工记账可选费用类别（与后端 ManualFeeRequest 1~8 一致）
const MANUAL_FEE_TYPES = INP_FEE_TYPE_OPTIONS

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

// ---------------- 一日清 ----------------
const loading = ref(false)
const groups = ref<DailyFeeGroup[]>([])

async function fetchFees() {
  if (!admissionId.value) {
    groups.value = []
    return
  }
  loading.value = true
  try {
    groups.value = (await getDailyFees(admissionId.value)) ?? []
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 手工记账 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  feeType: undefined as number | undefined,
  itemName: '',
  quantity: 1,
  unitPrice: 0,
})

const formRules: FormRules = {
  feeType: [{ required: true, message: '请选择费用类别', trigger: 'change' }],
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'change' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'change' }],
}

function openManualDialog() {
  form.feeType = undefined
  form.itemName = ''
  form.quantity = 1
  form.unitPrice = 0
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !admissionId.value || form.feeType === undefined) return
  submitting.value = true
  try {
    await createManualFee(admissionId.value, {
      feeType: form.feeType,
      itemName: form.itemName.trim(),
      quantity: Number(form.quantity),
      unitPrice: Number(form.unitPrice),
    })
    ElMessage.success('手工记账成功')
    dialogVisible.value = false
    fetchFees()
  } catch {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => searchAdmissions(''))
</script>

<style scoped>
.fee-area {
  min-height: 160px;
}

.fee-group {
  margin-bottom: 16px;
}

.fee-group-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.fee-date {
  font-weight: 600;
  color: #303133;
}

.fee-total {
  color: #606266;
}

.fee-total em {
  font-style: normal;
  font-weight: 700;
  color: #f56c6c;
}
</style>
