<template>
  <div class="page-card">
    <!-- 分诊登记 -->
    <div class="table-toolbar">
      <span class="toolbar-title">急诊分诊</span>
      <div>
        <el-button v-perm="'emc:triage:create'" type="danger" @click="triageVisible = true">分诊登记</el-button>
        <el-button v-perm="'emc:stats:query'" link type="primary" @click="loadStats">达标统计</el-button>
      </div>
    </div>

    <el-table v-loading="triageLoading" :data="triages" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="triageNo" label="分诊号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="patientId" label="患者ID" width="90" align="center" />
      <el-table-column prop="chiefComplaint" label="主诉" min-width="140" show-overflow-tooltip />
      <el-table-column label="级别" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="triageLevelTagType(row.triageLevel)">{{ triageLevelLabel(row.triageLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="预判中心" width="120" align="center">
        <template #default="{ row }">{{ centerTypeLabel(row.centerType) }}</template>
      </el-table-column>
      <el-table-column label="绿色通道" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.greenChannel === 1" size="small" type="success">绿通</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="triageTime" label="分诊时间" width="150" align="center" show-overflow-tooltip />
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1 && row.registered !== 1" v-perm="'emc:visit:create'" link type="danger" @click="openRegister(row)">中心登记</el-button>
          <el-tag v-else-if="row.registered === 1" size="small" type="info">已登记</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-toolbar mt16">
      <span class="toolbar-title">五大中心病例</span>
      <el-button link type="primary" :icon="Refresh" :loading="loading" @click="fetchVisits">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="visits" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="visitNo" label="登记号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="patientId" label="患者ID" width="90" align="center" />
      <el-table-column label="中心" width="120" align="center">
        <template #default="{ row }">{{ centerTypeLabel(row.centerType) }}</template>
      </el-table-column>
      <el-table-column prop="startTime" label="登记时刻" width="150" align="center" show-overflow-tooltip />
      <el-table-column label="关联住院" width="90" align="center">
        <template #default="{ row }">{{ row.admissionId ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="转归" width="90" align="center">
        <template #default="{ row }">{{ row.outcome ? outcomeLabel(row.outcome) : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 20 ? 'success' : 'warning'">{{ emcVisitStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 10" v-perm="'emc:timepoint:record'" link type="warning" @click="openTimepoint(row)">节点录入</el-button>
          <el-button v-if="row.status === 10" v-perm="'emc:visit:create'" link type="primary" @click="openLink(row)">关联住院</el-button>
          <el-button v-if="row.status === 10" v-perm="'emc:visit:close'" link type="success" @click="openClose(row)">关档</el-button>
          <el-button v-perm="'emc:visit:query'" link type="info" @click="openDetail(row)">时间轴</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="visitQuery.pageNum" v-model:page-size="visitQuery.pageSize"
        :total="visitTotal" :page-sizes="[10, 20, 50]" background
        layout="total, sizes, prev, pager, next" @size-change="handleSizeChange" @current-change="fetchVisits" />
    </div>

    <!-- 分诊登记 -->
    <el-dialog v-model="triageVisible" title="急诊分诊登记" width="560px" destroy-on-close>
      <el-form :model="triageForm" label-width="90px">
        <el-form-item label="患者ID" required><el-input-number v-model="triageForm.patientId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="主诉" required><el-input v-model="triageForm.chiefComplaint" placeholder="如 胸痛 30 分钟" /></el-form-item>
        <el-form-item label="生命体征">
          <div class="vital-row">
            <el-input-number v-model="triageForm.bodyTemp" :min="30" :max="45" :precision="1" placeholder="体温" controls-position="right" style="width: 90px" />
            <el-input-number v-model="triageForm.pulse" :min="0" :max="300" :precision="0" placeholder="脉搏" controls-position="right" style="width: 90px" />
            <el-input-number v-model="triageForm.respiration" :min="0" :max="60" :precision="0" placeholder="呼吸" controls-position="right" style="width: 90px" />
            <el-input v-model="triageForm.bloodPressure" placeholder="血压 120/80" style="width: 120px" />
            <el-input-number v-model="triageForm.spo2" :min="0" :max="100" :precision="0" placeholder="血氧" controls-position="right" style="width: 90px" />
          </div>
        </el-form-item>
        <el-form-item label="分诊级别" required>
          <el-radio-group v-model="triageForm.triageLevel">
            <el-radio-button v-for="opt in TRIAGE_LEVEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预判中心">
          <el-select v-model="triageForm.centerType" clearable style="width: 100%">
            <el-option v-for="opt in CENTER_TYPE_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="绿色通道">
          <el-switch v-model="triageForm.greenChannel" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triageVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTriage">登记</el-button>
      </template>
    </el-dialog>

    <!-- 中心登记 -->
    <el-dialog v-model="registerVisible" title="五大中心登记（时限基准=登记时刻）" width="440px" destroy-on-close>
      <el-form :model="registerForm" label-width="90px">
        <el-form-item label="中心" required>
          <el-select v-model="registerForm.centerType" style="width: 100%">
            <el-option v-for="opt in CENTER_TYPE_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRegister">登记</el-button>
      </template>
    </el-dialog>

    <!-- 节点录入 -->
    <el-dialog v-model="tpVisible" title="时间节点录入" width="460px" destroy-on-close>
      <el-form :model="tpForm" label-width="90px">
        <el-form-item label="节点" required>
          <el-select v-model="tpForm.nodeCode" style="width: 100%">
            <el-option v-for="n in centerNodes" :key="n.nodeCode" :value="n.nodeCode" :label="`${n.nodeName}${n.targetMinutes ? `（目标 ≤${n.targetMinutes}min）` : ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点时间" required>
          <el-date-picker v-model="tpForm.nodeTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tpVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTimepoint">录入</el-button>
      </template>
    </el-dialog>

    <!-- 时间轴抽屉 -->
    <el-drawer v-model="detailVisible" title="救治时间轴与达标预警" size="560px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="mb8">
          <el-descriptions-item label="登记号">{{ detail.visit.visitNo }}</el-descriptions-item>
          <el-descriptions-item label="中心">{{ centerTypeLabel(detail.visit.centerType) }}</el-descriptions-item>
          <el-descriptions-item label="登记时刻">{{ detail.visit.startTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ emcVisitStatusLabel(detail.visit.status) }}</el-descriptions-item>
          <el-descriptions-item label="主诉" :span="2">{{ detail.triage?.chiefComplaint || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.timeline" border size="small">
          <el-table-column prop="nodeName" label="节点" min-width="130" />
          <el-table-column label="目标" width="90" align="center">
            <template #default="{ row }">{{ row.targetMinutes ? `≤${row.targetMinutes}min` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="nodeTime" label="时刻" width="150" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ row.nodeTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="达标" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.onTarget === true" size="small" type="success">达标{{ row.elapsedMinutes !== null ? ` ${row.elapsedMinutes}min` : '' }}</el-tag>
              <el-tag v-else-if="row.onTarget === false" size="small" type="danger">超时 {{ row.elapsedMinutes }}min</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 达标统计 -->
    <el-drawer v-model="statsVisible" title="五大中心达标率统计" size="640px" destroy-on-close>
      <el-table v-loading="statsLoading" :data="stats" border size="small">
        <el-table-column prop="centerName" label="中心" width="140" />
        <el-table-column prop="caseCount" label="病例数" width="80" align="center" />
        <el-table-column prop="closedCount" label="已关档" width="80" align="center" />
        <el-table-column label="节点达标" min-width="220">
          <template #default="{ row }">
            <div v-for="n in row.nodes" :key="n.nodeCode" class="stat-node">
              {{ n.nodeName }}：
              <el-tag size="small" :type="n.totalCount === 0 ? 'info' : (n.metCount === n.totalCount ? 'success' : 'danger')">
                {{ n.metCount }}/{{ n.totalCount }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  addTimepoint,
  centerTypeLabel,
  closeVisit,
  emcCreateTriage,
  emcCreateVisit,
  emcVisitStatusLabel,
  CENTER_TYPE_OPTIONS,
  getEmcStats,
  getEmcVisitDetail,
  getEmcNodes,
  getEmcVisitPage,
  getTriagePage,
  linkVisit,
  OUTCOME_OPTIONS,
  outcomeLabel,
  TRIAGE_LEVEL_OPTIONS,
  triageLevelLabel,
  triageLevelTagType,
  type EmcNodeDict,
  type EmcTriage,
  type EmcVisit,
  type EmcVisitDetail,
  type TimelineNode,
} from '@/api/emc'

const triageLoading = ref(false)
const triages = ref<EmcTriage[]>([])
const loading = ref(false)
const visits = ref<EmcVisit[]>([])
const visitTotal = ref(0)
const visitQuery = reactive({ pageNum: 1, pageSize: 10 })
const nodeDict = ref<EmcNodeDict[]>([])

async function fetchTriages() {
  triageLoading.value = true
  try {
    const res = await getTriagePage({ pageNum: 1, pageSize: 20 })
    triages.value = res.list ?? []
  } finally {
    triageLoading.value = false
  }
}

async function fetchVisits() {
  loading.value = true
  try {
    const res = await getEmcVisitPage({ pageNum: visitQuery.pageNum, pageSize: visitQuery.pageSize })
    visits.value = res.list ?? []
    visitTotal.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  visitQuery.pageNum = 1
  fetchVisits()
}

async function fetchNodeDict() {
  try {
    nodeDict.value = await getEmcNodes()
  } catch {
    /* 无权限时节点下拉留空 */
  }
}

const centerNodes = computed(() =>
  nodeDict.value.filter((n) => !tpRow || n.centerType === tpRow.centerType),
)

const triageVisible = ref(false)
const triageForm = reactive({
  patientId: undefined as number | undefined,
  chiefComplaint: '',
  bodyTemp: undefined as number | undefined,
  pulse: undefined as number | undefined,
  respiration: undefined as number | undefined,
  bloodPressure: '',
  spo2: undefined as number | undefined,
  triageLevel: 3,
  centerType: undefined as number | undefined,
  greenChannel: false,
})

async function handleTriage() {
  if (!triageForm.patientId || !triageForm.chiefComplaint) {
    ElMessage.warning('请填写患者与主诉')
    return
  }
  await emcCreateTriage({
    patientId: triageForm.patientId,
    chiefComplaint: triageForm.chiefComplaint,
    bodyTemp: triageForm.bodyTemp,
    pulse: triageForm.pulse,
    respiration: triageForm.respiration,
    bloodPressure: triageForm.bloodPressure || undefined,
    spo2: triageForm.spo2,
    triageLevel: triageForm.triageLevel,
    centerType: triageForm.centerType,
    greenChannel: triageForm.greenChannel ? 1 : 0,
  })
  ElMessage.success('分诊登记完成')
  triageVisible.value = false
  fetchTriages()
}

const registerVisible = ref(false)
const registerForm = reactive<{ triageId: number; centerType: number }>({ triageId: 0, centerType: 1 })
let registerRow: EmcTriage | null = null

async function openRegister(row: EmcTriage) {
  registerRow = row
  registerForm.triageId = row.id
  registerForm.centerType = row.centerType && row.centerType > 0 ? row.centerType : 1
  registerVisible.value = true
}

async function handleRegister() {
  await emcCreateVisit({ triageId: registerForm.triageId, centerType: registerForm.centerType })
  ElMessage.success('已登记五大中心病例')
  registerVisible.value = false
  fetchVisits()
}

const tpVisible = ref(false)
const tpForm = reactive<{ nodeCode: string; nodeTime: string }>({ nodeCode: '', nodeTime: '' })
let tpRow: EmcVisit | null = null

async function openTimepoint(row: EmcVisit) {
  tpRow = row
  tpForm.nodeCode = ''
  tpForm.nodeTime = ''
  tpVisible.value = true
}

async function handleTimepoint() {
  if (!tpRow || !tpForm.nodeCode || !tpForm.nodeTime) {
    ElMessage.warning('请选择节点与时间')
    return
  }
  await addTimepoint(tpRow.id, { nodeCode: tpForm.nodeCode, nodeTime: tpForm.nodeTime })
  ElMessage.success('节点已录入')
  tpVisible.value = false
}

const linkVisible = ref(false)
const linkForm = reactive<{ admissionId: undefined | number }>({ admissionId: undefined })
let linkRow: EmcVisit | null = null

async function openLink(row: EmcVisit) {
  linkRow = row
  linkForm.admissionId = undefined
  linkVisible.value = true
}

async function handleLink() {
  if (!linkRow || !linkForm.admissionId) {
    ElMessage.warning('请填写住院 ID')
    return
  }
  await linkVisit(linkRow.id, { admissionId: linkForm.admissionId })
  ElMessage.success('已关联住院')
  linkVisible.value = false
  fetchVisits()
}

async function openClose(row: EmcVisit) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请选择 ${row.visitNo} 的转归：输入 1 收住院 / 2 急诊手术 / 3 转院 / 4 离院 / 5 死亡`,
      '病例关档',
      { inputValue: '1' },
    )
    const outcome = Number(value)
    if (!OUTCOME_OPTIONS.some((o) => o.value === outcome)) {
      ElMessage.warning('转归取值 1~5')
      return
    }
    await closeVisit(row.id, { outcome })
    ElMessage.success('已关档')
    fetchVisits()
  } catch {
    /* 用户取消 */
  }
}

const detailVisible = ref(false)
const detail = ref<EmcVisitDetail | null>(null)

async function openDetail(row: EmcVisit) {
  detail.value = await getEmcVisitDetail(row.id)
  detailVisible.value = true
}

const statsVisible = ref(false)
const statsLoading = ref(false)
const stats = ref<Array<Record<string, unknown>>>([])

async function loadStats() {
  statsVisible.value = true
  statsLoading.value = true
  try {
    stats.value = await getEmcStats({})
  } finally {
    statsLoading.value = false
  }
}

onMounted(() => {
  fetchTriages()
  fetchVisits()
  fetchNodeDict()
})
</script>

<style scoped>
.mt16 { margin-top: 16px; }
.vital-row { display: flex; gap: 6px; }
.stat-node { margin: 2px 0; }
</style>
