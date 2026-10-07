<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in CNT_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button link type="primary" :icon="Refresh" :loading="loading" @click="fetchList">
          刷新
        </el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <span class="toolbar-title">会诊申请列表</span>
      <span class="status-legend">流程：申请 → 会诊医师接受 → 完成并填写会诊意见</span>
      <el-button
        v-perm="'cnt:request'"
        type="warning"
        plain
        size="small"
        class="toolbar-action"
        @click="createDialogVisible = true"
      >
        新建会诊申请
      </el-button>
    </div>

    <!-- 会诊申请表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="reqNo" label="会诊单号" min-width="120" show-overflow-tooltip />
      <el-table-column prop="patientId" label="患者ID" width="80" align="center" />
      <el-table-column label="就诊关联" width="110" align="center">
        <template #default="{ row }">
          <span v-if="row.admissionId">住院 {{ row.admissionId }}</span>
          <span v-else-if="row.visitId">门诊 {{ row.visitId }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="申请科室" width="110" align="center" show-overflow-tooltip />
      <el-table-column prop="consultDoctorName" label="会诊医师" width="100" align="center" show-overflow-tooltip />
      <el-table-column label="缓急" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.urgent === 1 ? 'danger' : 'info'">
            {{ row.urgent === 1 ? '紧急' : '常规' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="会诊理由" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="cntStatusTagType(row.status)">
            {{ cntStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="acceptTime" label="接受时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.acceptTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="opinion" label="会诊意见" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.opinion || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            v-perm="'cnt:execute'"
            link
            type="primary"
            @click="handleAccept(row)"
          >
            接受
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'cnt:execute'"
            link
            type="success"
            @click="openCompleteDialog(row)"
          >
            完成
          </el-button>
          <span v-if="row.status === 30">-</span>
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

    <!-- 新建会诊申请弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建会诊申请" width="560px" destroy-on-close append-to-body>
      <el-form label-width="100px">
        <el-form-item label="就诊类型" required>
          <el-radio-group v-model="createForm.target">
            <el-radio-button value="inpatient">住院</el-radio-button>
            <el-radio-button value="outpatient">门诊</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="createForm.target === 'inpatient' ? '住院ID' : '门诊就诊ID'" required>
          <el-input-number v-model="createForm.targetId" :min="1" :controls="false" class="num-input" />
        </el-form-item>
        <el-form-item label="患者ID" required>
          <el-input-number v-model="createForm.patientId" :min="1" :controls="false" class="num-input" />
        </el-form-item>
        <el-form-item label="申请科室ID" required>
          <el-input-number v-model="createForm.deptId" :min="1" :controls="false" class="num-input" />
        </el-form-item>
        <el-form-item label="会诊医师ID" required>
          <el-input-number v-model="createForm.consultDoctorId" :min="1" :controls="false" class="num-input" />
        </el-form-item>
        <el-form-item label="缓急">
          <el-radio-group v-model="createForm.urgent">
            <el-radio-button :value="0">常规</el-radio-button>
            <el-radio-button :value="1">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="会诊理由" required>
          <el-input
            v-model="createForm.reason"
            type="textarea"
            :rows="3"
            maxlength="300"
            placeholder="病情摘要与会诊目的"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreateSubmit">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 完成会诊弹窗（20 → 30） -->
    <el-dialog v-model="completeDialogVisible" title="完成会诊" width="520px" destroy-on-close append-to-body>
      <div v-if="completeRow" class="dialog-line">{{ completeRow.reqNo }}｜患者 {{ completeRow.patientId }}</div>
      <el-form label-width="90px">
        <el-form-item label="会诊意见" required>
          <el-input
            v-model="opinion"
            type="textarea"
            :rows="4"
            maxlength="500"
            placeholder="会诊意见与诊疗建议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="completeSubmitting" @click="handleCompleteSubmit">
          确认完成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  CNT_STATUS_OPTIONS,
  acceptCntRequest,
  cntStatusLabel,
  cntStatusTagType,
  completeCntRequest,
  createCntRequest,
  getCntPage,
  type CntRequest,
} from '@/api/cnt'

// ---------------- 列表查询 ----------------
const loading = ref(false)
const list = ref<CntRequest[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: undefined as number | undefined,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getCntPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
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

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 新建会诊申请 ----------------
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive({
  target: 'inpatient' as 'inpatient' | 'outpatient',
  targetId: undefined as number | undefined,
  patientId: undefined as number | undefined,
  deptId: undefined as number | undefined,
  consultDoctorId: undefined as number | undefined,
  urgent: 0,
  reason: '',
})

function resetCreateForm() {
  createForm.target = 'inpatient'
  createForm.targetId = undefined
  createForm.patientId = undefined
  createForm.deptId = undefined
  createForm.consultDoctorId = undefined
  createForm.urgent = 0
  createForm.reason = ''
}

async function handleCreateSubmit() {
  if (!createForm.targetId || !createForm.patientId || !createForm.deptId || !createForm.consultDoctorId || !createForm.reason.trim()) {
    ElMessage.warning('请完整填写会诊申请')
    return
  }
  createSubmitting.value = true
  try {
    const payload =
      createForm.target === 'inpatient'
        ? { admissionId: createForm.targetId }
        : { visitId: createForm.targetId }
    const reqNo = await createCntRequest({
      ...payload,
      patientId: createForm.patientId,
      deptId: createForm.deptId,
      consultDoctorId: createForm.consultDoctorId,
      urgent: createForm.urgent,
      reason: createForm.reason.trim(),
    })
    ElMessage.success(`会诊申请已提交：${reqNo}`)
    createDialogVisible.value = false
    resetCreateForm()
    handleSearch()
  } catch {
    // 拦截器已统一提示
  } finally {
    createSubmitting.value = false
  }
}

// ---------------- 接受（10 → 20） ----------------
async function handleAccept(row: CntRequest) {
  try {
    await ElMessageBox.confirm(
      `确认接受会诊 ${row.reqNo}（患者 ${row.patientId}）？接受后请按时完成会诊。`,
      '接受会诊',
      { type: 'warning', confirmButtonText: '确认接受', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await acceptCntRequest(row.id)
    ElMessage.success('已接受，会诊进行中')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 完成（20 → 30） ----------------
const completeDialogVisible = ref(false)
const completeSubmitting = ref(false)
const completeRow = ref<CntRequest | null>(null)
const opinion = ref('')

function openCompleteDialog(row: CntRequest) {
  completeRow.value = row
  opinion.value = ''
  completeDialogVisible.value = true
}

async function handleCompleteSubmit() {
  if (!completeRow.value) return
  if (!opinion.value.trim()) {
    ElMessage.warning('请填写会诊意见')
    return
  }
  completeSubmitting.value = true
  try {
    await completeCntRequest(completeRow.value.id, opinion.value.trim())
    ElMessage.success('会诊已完成')
    completeDialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    completeSubmitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.status-legend {
  font-size: 12px;
  color: #909399;
}

.toolbar-action {
  margin-left: auto;
}

.dialog-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}

.num-input {
  width: 100%;
}
</style>
