<template>
  <div class="page-card">
    <!-- 查询条件 -->
    <el-form class="search-bar" inline>
      <el-form-item label="病区">
        <el-select
          v-model="wardId"
          placeholder="选择病区"
          style="width: 180px"
          @change="fetchSchedules"
        >
          <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="排班日期">
        <el-date-picker
          v-model="shiftDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 150px"
          @change="fetchSchedules"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="fetchSchedules">查询</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <div class="table-toolbar">
      <span class="toolbar-title">护士排班</span>
      <el-button
        v-perm="'nur:schedule:manage'"
        type="primary"
        :icon="Plus"
        :disabled="!wardId"
        @click="openCreateDialog"
      >
        新增排班
      </el-button>
    </div>

    <!-- 排班表 -->
    <el-table v-loading="loading" :data="schedules" border stripe>
      <el-table-column prop="shiftDate" label="日期" width="120" align="center" />
      <el-table-column label="班次" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="shiftTypeTagType(row.shiftType)">
            {{ shiftTypeLabel(row.shiftType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="护士" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ nurseNameOf(row.nurseId) || row.nurseName || `ID:${row.nurseId}` }}</template>
      </el-table-column>
      <el-table-column label="病区" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.wardName || wardName() }}</template>
      </el-table-column>
    </el-table>

    <!-- 新增排班弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增排班" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="护士" prop="nurseId">
          <el-select
            v-model="form.nurseId"
            filterable
            :loading="userLoading"
            placeholder="选择护士（系统用户）"
            style="width: 100%"
          >
            <el-option
              v-for="u in nurseOptions"
              :key="u.id"
              :label="`${u.realName || u.username}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="病区">
          <el-select v-model="form.wardId" disabled style="width: 180px">
            <el-option v-for="w in wards" :key="w.id" :label="w.wardName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排班日期" prop="shiftDate">
          <el-date-picker
            v-model="form.shiftDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="班次" prop="shiftType">
          <el-radio-group v-model="form.shiftType">
            <el-radio v-for="o in SHIFT_TYPE_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="同一护士同一天同班次不可重复排班（重复提交将返回错误）。"
      />
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
import { Plus, Search } from '@element-plus/icons-vue'
import { SHIFT_TYPE_OPTIONS, shiftTypeLabel, shiftTypeTagType, createSchedule, getSchedules, type Schedule } from '@/api/nur'
import { getWards, type Ward } from '@/api/inp'
import { getUserPage, type SystemUser } from '@/api/system'
import { todayStr } from '@/api/registration'

// ---------------- 病区 ----------------
const wards = ref<Ward[]>([])
const wardId = ref<number | undefined>(undefined)

async function fetchWards() {
  try {
    wards.value = (await getWards()) ?? []
    if (wards.value.length > 0 && wardId.value === undefined) {
      wardId.value = wards.value[0].id
    }
  } catch {
    wards.value = []
  }
}

function wardName(): string {
  return wards.value.find((w) => w.id === wardId.value)?.wardName || '-'
}

// ---------------- 护士下拉（/system/users） ----------------
const nurseOptions = ref<SystemUser[]>([])
const userLoading = ref(false)

async function fetchNurses() {
  if (nurseOptions.value.length > 0) return
  userLoading.value = true
  try {
    const res = await getUserPage({ pageNum: 1, pageSize: 200 })
    nurseOptions.value = res.list ?? []
  } catch {
    nurseOptions.value = []
  } finally {
    userLoading.value = false
  }
}

function nurseNameOf(nurseId?: number | null): string {
  if (!nurseId) return ''
  const u = nurseOptions.value.find((x) => x.id === nurseId)
  return u ? u.realName || u.username : ''
}

// ---------------- 排班列表 ----------------
const loading = ref(false)
const shiftDate = ref(todayStr())
const schedules = ref<Schedule[]>([])

async function fetchSchedules() {
  if (!wardId.value) {
    schedules.value = []
    return
  }
  loading.value = true
  try {
    schedules.value =
      (await getSchedules({ wardId: wardId.value, shiftDate: shiftDate.value })) ?? []
  } catch {
    schedules.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 新增排班 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  nurseId: undefined as number | undefined,
  wardId: undefined as number | undefined,
  shiftDate: todayStr(),
  shiftType: 1,
})

const formRules: FormRules = {
  nurseId: [{ required: true, message: '请输入护士ID', trigger: 'change' }],
  shiftDate: [{ required: true, message: '请选择排班日期', trigger: 'change' }],
  shiftType: [{ required: true, message: '请选择班次', trigger: 'change' }],
}

function openCreateDialog() {
  form.nurseId = undefined
  form.wardId = wardId.value
  form.shiftDate = todayStr()
  form.shiftType = 1
  dialogVisible.value = true
  fetchNurses()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !wardId.value || !form.nurseId) return
  submitting.value = true
  try {
    await createSchedule({
      nurseId: Number(form.nurseId),
      wardId: wardId.value,
      shiftDate: form.shiftDate,
      shiftType: form.shiftType,
    })
    ElMessage.success('排班新增成功')
    dialogVisible.value = false
    fetchSchedules()
  } catch {
    // A0001 重复排班已在拦截器统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await fetchWards()
  fetchSchedules()
  fetchNurses()
})
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
