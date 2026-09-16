<template>
  <div class="page-card">
    <!-- 病区选择工具条 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-title">床位一览</span>
        <el-select
          v-model="wardId"
          placeholder="选择病区"
          style="width: 200px; margin-left: 12px"
          @change="fetchBeds"
        >
          <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="w.id" />
        </el-select>
        <el-radio-group v-model="bedStatusFilter" style="margin-left: 12px" @change="fetchBeds">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="o in BED_STATUS_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <el-button v-perm="'inp:bed:manage'" type="primary" :icon="Plus" @click="openCreateDialog">
        新增床位
      </el-button>
    </div>

    <!-- 床位卡片 -->
    <div v-loading="loading" class="bed-grid">
      <el-empty v-if="!loading && beds.length === 0" description="该病区暂无床位" />
      <div v-for="b in beds" :key="b.id" class="bed-card" :class="`bed-card-${b.bedStatus}`">
        <div class="bed-head">
          <span class="bed-no">{{ b.bedNo }}</span>
          <el-tag size="small" :type="bedStatusTagType(b.bedStatus)">
            {{ bedStatusLabel(b.bedStatus) }}
          </el-tag>
        </div>
        <div class="bed-body">
          <template v-if="b.bedStatus === 2">
            <div class="bed-patient">{{ b.patientName || '住院患者' }}</div>
            <div class="bed-sub">住院 ID：{{ b.currentAdmissionId ?? '-' }}</div>
          </template>
          <template v-else>
            <div class="bed-sub">床位费：¥{{ fmtMoney(b.bedFee) }}/日</div>
          </template>
        </div>
        <div v-if="canToggleStatus(b)" class="bed-foot">
          <el-button
            v-perm="'inp:bed:manage'"
            link
            :type="b.bedStatus === 4 ? 'success' : 'warning'"
            size="small"
            @click="handleToggleStatus(b)"
          >
            {{ b.bedStatus === 4 ? '启用' : '停用' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 新增床位弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增床位" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="所属病区" prop="wardId">
          <el-select v-model="form.wardId" placeholder="选择病区" style="width: 100%">
            <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="床位号" prop="bedNo">
          <el-input v-model="form.bedNo" placeholder="如 0201" maxlength="20" />
        </el-form-item>
        <el-form-item label="床位费项目" prop="chargeItemId">
          <el-select
            v-model="form.chargeItemId"
            filterable
            :loading="itemLoading"
            placeholder="选择床位费对应的收费项目"
            style="width: 100%"
          >
            <el-option
              v-for="i in chargeItems"
              :key="i.id"
              :label="`${i.itemName}（¥${i.price}/${i.unit}）`"
              :value="i.id"
            />
          </el-select>
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getChargeItemList, type ChargeItem } from '@/api/basedata'
import { fmtMoney } from '@/api/registration'
import {
  bedStatusLabel,
  bedStatusTagType,
  BED_STATUS_OPTIONS,
  createBed,
  getBeds,
  getWards,
  updateBedStatus,
  type Bed,
  type Ward,
} from '@/api/inp'

// ---------------- 病区与床位 ----------------
const wards = ref<Ward[]>([])
const wardId = ref<number | undefined>(undefined)
const bedStatusFilter = ref<number | undefined>(undefined)
const beds = ref<Bed[]>([])
const loading = ref(false)

async function fetchWards() {
  try {
    wards.value = (await getWards()) ?? []
    if (wards.value.length > 0 && wardId.value === undefined) {
      wardId.value = wards.value[0].id
      await fetchBeds()
    }
  } catch {
    wards.value = []
  }
}

async function fetchBeds() {
  if (!wardId.value) {
    beds.value = []
    return
  }
  loading.value = true
  try {
    beds.value = (await getBeds({ wardId: wardId.value, bedStatus: bedStatusFilter.value })) ?? []
  } catch {
    beds.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 床位状态切换 ----------------
/** 仅空闲/停用床位可切换状态（占用/预约由住院流程控制） */
function canToggleStatus(b: Bed): boolean {
  return b.bedStatus === 1 || b.bedStatus === 4
}

function handleToggleStatus(b: Bed) {
  const target = b.bedStatus === 4 ? 1 : 4
  const action = target === 4 ? '停用' : '启用'
  ElMessageBox.confirm(`确认${action}床位「${b.bedNo}」？`, `${action}床位`, {
    type: 'warning',
    confirmButtonText: `确认${action}`,
    cancelButtonText: '取消',
  })
    .then(async () => {
      await updateBedStatus(b.id, target)
      ElMessage.success(`床位已${action}`)
      fetchBeds()
    })
    .catch(() => {
      // 取消
    })
}

// ---------------- 新增床位 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const chargeItems = ref<ChargeItem[]>([])
const itemLoading = ref(false)
const form = reactive({
  wardId: undefined as number | undefined,
  bedNo: '',
  chargeItemId: undefined as number | undefined,
})

const formRules: FormRules = {
  wardId: [{ required: true, message: '请选择所属病区', trigger: 'change' }],
  bedNo: [{ required: true, message: '请输入床位号', trigger: 'blur' }],
  chargeItemId: [{ required: true, message: '请选择床位费收费项目', trigger: 'change' }],
}

async function openCreateDialog() {
  form.wardId = wardId.value
  form.bedNo = ''
  form.chargeItemId = undefined
  dialogVisible.value = true
  if (chargeItems.value.length === 0) {
    itemLoading.value = true
    try {
      chargeItems.value = (await getChargeItemList({ status: 1 })) ?? []
    } catch {
      chargeItems.value = []
    } finally {
      itemLoading.value = false
    }
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || form.wardId === undefined) return
  submitting.value = true
  try {
    await createBed({
      wardId: form.wardId,
      bedNo: form.bedNo.trim(),
      chargeItemId: form.chargeItemId as number,
    })
    ElMessage.success('床位创建成功')
    dialogVisible.value = false
    fetchBeds()
  } catch {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(fetchWards)
</script>

<style scoped>
.toolbar-left {
  display: flex;
  align-items: center;
}

.bed-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  min-height: 120px;
}

.bed-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.bed-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.bed-card-1 {
  border-top: 3px solid #67c23a;
}

.bed-card-2 {
  border-top: 3px solid #f56c6c;
}

.bed-card-3 {
  border-top: 3px solid #e6a23c;
}

.bed-card-4 {
  border-top: 3px solid #c0c4cc;
  background: #fafafa;
}

.bed-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bed-no {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.bed-body {
  margin-top: 8px;
  min-height: 40px;
}

.bed-patient {
  font-weight: 600;
  color: #303133;
}

.bed-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.bed-foot {
  margin-top: 6px;
  text-align: right;
}
</style>
