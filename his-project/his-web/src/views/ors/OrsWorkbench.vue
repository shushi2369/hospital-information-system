<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="就诊ID">
        <el-input-number v-model="query.admissionId" :min="1" :precision="0" controls-position="right" style="width: 130px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in ORS_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button v-perm="'or:request:create'" type="success" @click="createVisible = true">
          新建手术申请
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="requestNo" label="申请号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="admissionId" label="就诊ID" width="90" align="center" />
      <el-table-column prop="surgeryName" label="手术名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="plannedDate" label="拟手术日期" width="110" align="center" />
      <el-table-column label="麻醉方式" width="100" align="center">
        <template #default="{ row }">{{ anesthesiaMethodLabel(row.anesthesiaMethod) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="orsStatusTagType(row.status)">{{ orsStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 10" v-perm="'or:request:review'" link type="warning" @click="handleReview(row, true)">通过</el-button>
          <el-button v-if="row.status === 10" v-perm="'or:request:review'" link type="danger" @click="handleReview(row, false)">驳回</el-button>
          <el-button v-if="row.status === 20" v-perm="'or:schedule:manage'" link type="primary" @click="openSchedule(row)">排台</el-button>
          <el-button v-if="row.status === 30 || row.status === 50" v-perm="'or:check:submit'" link type="warning" @click="openCheck(row)">核查</el-button>
          <el-button v-if="row.status === 30" v-perm="'or:stage:operate'" link type="primary" @click="handleStart(row)">开始</el-button>
          <el-button v-if="row.status === 40" v-perm="'or:anesthesia:write'" link type="warning" @click="openAnesthesia(row)">麻醉</el-button>
          <el-button v-if="row.status === 40" v-perm="'or:stage:operate'" link type="danger" @click="handleFinish(row)">结束</el-button>
          <el-button v-if="row.status === 50" v-perm="'or:stage:operate'" link type="success" @click="openLeave(row)">离室</el-button>
          <el-button v-if="row.status === 60 && row.chargeStatus === 0" v-perm="'or:request:complete'" link type="success" @click="handleComplete(row)">关档记账</el-button>
          <el-button v-if="[10, 20, 30].includes(row.status)" v-perm="'or:request:create'" link type="danger" @click="handleCancel(row)">取消</el-button>
          <el-button v-perm="'or:request:query'" link type="info" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" background
        layout="total, sizes, prev, pager, next" @size-change="handleSizeChange" @current-change="fetchList" />
    </div>

    <!-- 新建申请 -->
    <el-dialog v-model="createVisible" title="新建手术申请" width="520px" destroy-on-close @open="loadChargeItems">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="就诊ID" required><el-input-number v-model="createForm.admissionId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="患者ID" required><el-input-number v-model="createForm.patientId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="手术名称" required><el-input v-model="createForm.surgeryName" placeholder="如 阑尾切除术" /></el-form-item>
        <el-form-item label="术前诊断" required><el-input v-model="createForm.diagnosis" /></el-form-item>
        <el-form-item label="手术费项目" required>
          <el-select v-model="createForm.surgeryItemId" filterable style="width: 100%" placeholder="选择手术费收费项目">
            <el-option v-for="i in surgeryItems" :key="i.id" :value="i.id" :label="i.itemName + '（¥' + i.price + '）'" />
          </el-select>
        </el-form-item>
        <el-form-item label="麻醉费项目" required>
          <el-select v-model="createForm.anesthesiaItemId" filterable style="width: 100%" placeholder="选择麻醉费收费项目">
            <el-option v-for="i in anesthesiaItems" :key="i.id" :value="i.id" :label="i.itemName + '（¥' + i.price + '）'" />
          </el-select>
        </el-form-item>
        <el-form-item label="拟手术日期" required><el-date-picker v-model="createForm.plannedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="麻醉方式" required>
          <el-select v-model="createForm.anesthesiaMethod" style="width: 100%">
            <el-option v-for="(l, v) in [1, 2, 3, 4, 5]" :key="v" :value="Number(v) + 1" :label="anesthesiaMethodLabel(Number(v) + 1)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 排台 -->
    <el-dialog v-model="scheduleVisible" title="手术排台" width="480px" destroy-on-close>
      <el-form :model="scheduleForm" label-width="90px">
        <el-form-item label="手术间" required>
          <el-select v-model="scheduleForm.roomId" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :value="r.id" :label="`${r.roomNo} ${r.roomName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="手术日期" required><el-date-picker v-model="scheduleForm.surgeryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="台次" required><el-input-number v-model="scheduleForm.seqNo" :min="1" :max="10" style="width: 100%" /></el-form-item>
        <el-form-item label="主刀医生ID" required><el-input-number v-model="scheduleForm.surgeonId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSchedule">提交</el-button>
      </template>
    </el-dialog>

    <!-- 三方核查 -->
    <el-dialog v-model="checkVisible" title="手术安全核查（双人签名）" width="560px" destroy-on-close>
      <el-descriptions :column="1" border size="small" class="mb8">
        <el-descriptions-item label="当前签名人">{{ currentUserName }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="核查类型">
          <el-radio-group v-model="checkForm.checkType">
            <el-radio-button v-if="[30, 40].includes(currentRow?.status ?? 0)" :value="1">麻醉前</el-radio-button>
            <el-radio-button v-if="[30, 40].includes(currentRow?.status ?? 0)" :value="2">切皮前</el-radio-button>
            <el-radio-button v-if="currentRow?.status === 50" :value="3">离室前</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="第二签名"><el-input-number v-model="checkForm.checker2Id" :min="1" :precision="0" placeholder="第二签名人用户ID" style="width: 100%" /></el-form-item>
      </el-form>
      <el-table :data="checkForm.items" border size="small" max-height="320">
        <el-table-column label="核查项">
          <template #default="{ row }">{{ row.item }}</template>
        </el-table-column>
        <el-table-column label="通过" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.result" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="checkVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitCheck">登记</el-button>
      </template>
    </el-dialog>

    <!-- 麻醉记录 -->
    <el-dialog v-model="anesthesiaVisible" title="麻醉记录" width="520px" destroy-on-close>
      <el-form :model="anesthesiaForm" label-width="110px">
        <el-form-item label="ASA 分级" required>
          <el-select v-model="anesthesiaForm.asaGrade" style="width: 100%">
            <el-option v-for="g in [1, 2, 3, 4, 5]" :key="g" :value="g" :label="`ASA ${g}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="术中用药"><el-input v-model="anesthesiaForm.drugNote" placeholder="如 丙泊酚 100mg、七氟烷维持" /></el-form-item>
        <el-form-item label="术中事件"><el-input v-model="anesthesiaForm.eventNote" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="anesthesiaVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAnesthesia">保存</el-button>
      </template>
    </el-dialog>

    <!-- 离室登记 -->
    <el-dialog v-model="leaveVisible" title="离室登记（含术后记录）" width="480px" destroy-on-close>
      <el-form :model="leaveForm" label-width="110px">
        <el-form-item label="苏醒评分" required>
          <el-input-number v-model="leaveForm.recoveryScore" :min="0" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="离室去向" required>
          <el-select v-model="leaveForm.destination" style="width: 100%">
            <el-option :value="1" label="回病房" />
            <el-option :value="2" label="转 ICU" />
            <el-option :value="3" label="离院" />
          </el-select>
        </el-form-item>
        <el-form-item label="随访备注"><el-input v-model="leaveForm.followupNote" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leaveVisible = false">取消</el-button>
        <el-button type="primary" @click="handleLeave">登记离室</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="手术详情" size="560px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请号">{{ detail.request.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orsStatusLabel(detail.request.status) }}</el-descriptions-item>
          <el-descriptions-item label="手术名称" :span="2">{{ detail.request.surgeryName }}</el-descriptions-item>
          <el-descriptions-item label="术前诊断" :span="2">{{ detail.request.diagnosis }}</el-descriptions-item>
          <el-descriptions-item label="拟手术日期">{{ detail.request.plannedDate }}</el-descriptions-item>
          <el-descriptions-item label="麻醉方式">{{ anesthesiaMethodLabel(detail.request.anesthesiaMethod) }}</el-descriptions-item>
          <el-descriptions-item label="切皮时间">{{ detail.request.incisionTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detail.request.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="排台" :span="2">
            <template v-if="detail.schedule">
              {{ detail.schedule.scheduleNo }}（{{ detail.schedule.surgeryDate }} 第 {{ detail.schedule.seqNo }} 台）
            </template>
            <template v-else>-</template>
          </el-descriptions-item>
          <el-descriptions-item label="核查单" :span="2">
            <template v-if="detail.checks.length">
              <el-tag v-for="c in detail.checks" :key="c.id" size="small" class="mr8" type="success">
                {{ checkTypeLabel(c.checkType) }} 已核查
              </el-tag>
            </template>
            <template v-else>-</template>
          </el-descriptions-item>
          <el-descriptions-item label="记账状态" :span="2">
            {{ detail.request.chargeStatus === 1 ? '已关档记账' : '未记账' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getChargeItemList, type ChargeItem } from '@/api/basedata'
import { toList } from '@/api/request'
import {
  anesthesiaMethodLabel,
  cancelSurgery,
  checkTypeLabel,
  completeSurgery,
  createSurgery,
  defaultChecklist,
  finishSurgery,
  getOrsDetail,
  getRequestPage,
  getRooms,
  leaveRoom,
  ORS_STATUS_OPTIONS,
  orsStatusLabel,
  orsStatusTagType,
  reviewSurgery,
  saveAnesthesia,
  scheduleSurgery,
  startSurgery,
  submitCheck,
  type CheckItem,
  type OrsDetail,
  type OrsRequest,
  type OrsRoom,
} from '@/api/ors'

const auth = useUserStore()
const currentUserName = auth.userInfo?.realName ?? String(auth.userInfo?.id ?? '')

const loading = ref(false)
const list = ref<OrsRequest[]>([])
const total = ref(0)
const rooms = ref<OrsRoom[]>([])
const query = reactive({ pageNum: 1, pageSize: 10, admissionId: undefined as number | undefined, status: undefined as number | undefined })

async function fetchList() {
  loading.value = true
  try {
    const res = await getRequestPage({ pageNum: query.pageNum, pageSize: query.pageSize, admissionId: query.admissionId, status: query.status })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { query.pageNum = 1; fetchList() }
function handleReset() { query.admissionId = undefined; query.status = undefined; handleSearch() }
function handleSizeChange() { query.pageNum = 1; fetchList() }

async function confirmAction(message: string, title: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title)
    return true
  } catch {
    return false
  }
}

const createVisible = ref(false)
const createForm = reactive({
  admissionId: undefined as number | undefined,
  patientId: undefined as number | undefined,
  surgeryName: '',
  diagnosis: '',
  surgeryItemId: undefined as number | undefined,
  anesthesiaItemId: undefined as number | undefined,
  plannedDate: '',
  anesthesiaMethod: 1,
})
// 手术/麻醉收费项目（category 9/10）：缺项目则手术无法计费（四十轮 UI 走查实锤）
const surgeryItems = ref<ChargeItem[]>([])
const anesthesiaItems = ref<ChargeItem[]>([])

async function loadChargeItems() {
  try {
    const [op, an] = await Promise.all([
      getChargeItemList({ category: 9, status: 1 }),
      getChargeItemList({ category: 10, status: 1 }),
    ])
    surgeryItems.value = toList(op)
    anesthesiaItems.value = toList(an)
  } catch {
    // 拦截器已统一提示
  }
}

async function handleCreate() {
  if (!createForm.admissionId || !createForm.patientId || !createForm.surgeryName || !createForm.diagnosis
    || !createForm.surgeryItemId || !createForm.anesthesiaItemId || !createForm.plannedDate) {
    ElMessage.warning('请完整填写申请信息（含手术/麻醉费项目）')
    return
  }
  await createSurgery({
    admissionId: createForm.admissionId,
    patientId: createForm.patientId,
    surgeryName: createForm.surgeryName,
    diagnosis: createForm.diagnosis,
    surgeryItemId: createForm.surgeryItemId,
    anesthesiaItemId: createForm.anesthesiaItemId,
    plannedDate: createForm.plannedDate,
    anesthesiaMethod: createForm.anesthesiaMethod,
  })
  ElMessage.success('手术申请已创建')
  createVisible.value = false
  fetchList()
}

async function handleReview(row: OrsRequest, approved: boolean) {
  const action = approved ? '通过' : '驳回'
  if (!(await confirmAction(`确认${action}手术申请 ${row.requestNo}？`, '手术审核'))) return
  await reviewSurgery(row.id, { approved })
  ElMessage.success(`已${action}`)
  fetchList()
}

const scheduleVisible = ref(false)
const scheduleForm = reactive({ roomId: undefined as number | undefined, surgeryDate: '', seqNo: 1, surgeonId: undefined as number | undefined })
let currentRow: OrsRequest | null = null

async function openSchedule(row: OrsRequest) {
  currentRow = row
  if (!rooms.value.length) {
    rooms.value = await getRooms()
  }
  scheduleForm.roomId = undefined
  scheduleForm.surgeryDate = row.plannedDate
  scheduleForm.seqNo = 1
  scheduleForm.surgeonId = undefined
  scheduleVisible.value = true
}

async function handleSchedule() {
  if (!currentRow || !scheduleForm.roomId || !scheduleForm.surgeryDate || !scheduleForm.surgeonId) {
    ElMessage.warning('请完整填写排台信息')
    return
  }
  await scheduleSurgery(currentRow.id, {
    roomId: scheduleForm.roomId,
    surgeryDate: scheduleForm.surgeryDate,
    seqNo: scheduleForm.seqNo,
    surgeonId: scheduleForm.surgeonId,
  })
  ElMessage.success('排台成功')
  scheduleVisible.value = false
  fetchList()
}

const checkVisible = ref(false)
const checkForm = reactive<{ checkType: number; checker2Id: undefined | number; items: CheckItem[] }>({ checkType: 1, checker2Id: undefined, items: [] })

async function openCheck(row: OrsRequest) {
  currentRow = row
  checkForm.checkType = row.status === 50 ? 3 : 1
  checkForm.checker2Id = undefined
  checkForm.items = defaultChecklist(checkForm.checkType)
  checkVisible.value = true
}

async function handleSubmitCheck() {
  if (!currentRow || !checkForm.checker2Id) {
    ElMessage.warning('请填写第二签名人')
    return
  }
  await submitCheck(currentRow.id, { checkType: checkForm.checkType, items: checkForm.items, checker2Id: checkForm.checker2Id })
  ElMessage.success('核查单已登记')
  checkVisible.value = false
  fetchList()
}

async function handleStart(row: OrsRequest) {
  if (!(await confirmAction('开始手术需麻醉前与切皮前核查齐备，确认开始？', '开始手术'))) return
  await startSurgery(row.id)
  ElMessage.success('手术已开始')
  fetchList()
}

const anesthesiaVisible = ref(false)
const anesthesiaForm = reactive({ asaGrade: 1, drugNote: '', eventNote: '' })

async function openAnesthesia(row: OrsRequest) {
  currentRow = row
  anesthesiaForm.asaGrade = 1
  anesthesiaForm.drugNote = ''
  anesthesiaForm.eventNote = ''
  anesthesiaVisible.value = true
}

async function handleAnesthesia() {
  if (!currentRow) return
  await saveAnesthesia(currentRow.id, { asaGrade: anesthesiaForm.asaGrade, drugNote: anesthesiaForm.drugNote, eventNote: anesthesiaForm.eventNote })
  ElMessage.success('麻醉记录已保存')
  anesthesiaVisible.value = false
  fetchList()
}

async function handleFinish(row: OrsRequest) {
  if (!(await confirmAction('确认手术结束、转入复苏？', '手术结束'))) return
  await finishSurgery(row.id)
  ElMessage.success('手术已结束，复苏中')
  fetchList()
}

const leaveVisible = ref(false)
const leaveForm = reactive({ recoveryScore: 10, destination: 1, followupNote: '' })

async function openLeave(row: OrsRequest) {
  currentRow = row
  leaveForm.recoveryScore = 10
  leaveForm.destination = 1
  leaveForm.followupNote = ''
  leaveVisible.value = true
}

async function handleLeave() {
  if (!currentRow) return
  await leaveRoom(currentRow.id, { recoveryScore: leaveForm.recoveryScore, destination: leaveForm.destination, followupNote: leaveForm.followupNote })
  ElMessage.success('离室登记完成')
  leaveVisible.value = false
  fetchList()
}

async function handleComplete(row: OrsRequest) {
  if (!(await confirmAction('关档将自动生成手术费/麻醉费（已配置项目时），确认关档？', '完成关档'))) return
  await completeSurgery(row.id)
  ElMessage.success('已关档记账')
  fetchList()
}

async function handleCancel(row: OrsRequest) {
  if (!(await confirmAction(`确认取消手术申请 ${row.requestNo}？`, '取消申请'))) return
  await cancelSurgery(row.id)
  ElMessage.success('已取消')
  fetchList()
}

const detailVisible = ref(false)
const detail = ref<OrsDetail | null>(null)

async function openDetail(row: OrsRequest) {
  detail.value = await getOrsDetail(row.id)
  detailVisible.value = true
}

onMounted(fetchList)
</script>

<style scoped>
.mr8 { margin-right: 8px; }
.mb8 { margin-bottom: 8px; }
</style>
