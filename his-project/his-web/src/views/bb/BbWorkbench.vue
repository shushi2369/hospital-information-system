<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="用血申请" name="req">
        <div class="table-toolbar">
          <span class="toolbar-title">用血申请单</span>
          <!-- 一百四十九轮 D-29：加状态筛选——217 条 22 页混排待办无从一眼确认 -->
          <el-radio-group v-model="query.status" size="small" @change="handleSearch">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="10">待审核</el-radio-button>
            <el-radio-button :value="20">待配血</el-radio-button>
            <el-radio-button :value="30">待发血</el-radio-button>
            <el-radio-button :value="40">待输注</el-radio-button>
            <el-radio-button :value="50">输注中</el-radio-button>
          </el-radio-group>
          <el-button v-perm="'bb:request:create'" type="danger" @click="resetReqForm(); reqVisible = true">用血申请</el-button>
          <el-button v-perm="'bb:bags:manage'" link type="primary" @click="resetBagForm(); bagVisible = true">血袋入库</el-button>
        </div>
        <el-table v-loading="loading" :data="list" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="reqNo" label="申请号" min-width="140" />
          <el-table-column prop="admissionId" label="就诊ID" width="90" align="center" />
          <el-table-column label="血型" width="110" align="center">
            <template #default="{ row }">{{ bloodTypeLabel(row.bloodType) }} / {{ rhLabel(row.rh) }}</template>
          </el-table-column>
          <el-table-column label="成分" width="90" align="center">
            <template #default="{ row }">{{ componentLabel(row.component) }}</template>
          </el-table-column>
          <el-table-column prop="volumeMl" label="申请量ml" width="100" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="bbStatusTagType(row.status)">{{ bbStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 10" v-perm="'bb:request:review'" link type="warning" @click="handleReview(row, true)">通过</el-button>
              <el-button v-if="row.status === 10" v-perm="'bb:request:review'" link type="danger" @click="handleReview(row, false)">驳回</el-button>
              <el-button v-if="row.status === 20" v-perm="'bb:cross:match'" link type="warning" @click="openCross(row)">配血</el-button>
              <el-button v-if="row.status === 30" v-perm="'bb:issue:manage'" link type="success" @click="openIssue(row)">发血</el-button>
              <el-button v-if="row.status === 40" v-perm="'bb:transfusion:execute'" link type="primary" @click="openTransfusion(row)">开始输注</el-button>
              <el-button v-if="row.status === 50" v-perm="'bb:transfusion:execute'" link type="success" @click="openFinish(row)">结束</el-button>
              <el-button v-if="[50, 60].includes(row.status)" v-perm="'bb:adverse:report'" link type="danger" @click="openAdverse(row)">不良反应</el-button>
              <!-- 一百一十九轮【4】P2：补取消入口（后端支持 10/20/30→70，配血被他人先发等卡死场景的出口） -->
              <el-button
                v-if="[10, 20, 30].includes(row.status)"
                v-perm="'bb:request:create'"
                link
                type="warning"
                @click="handleCancelRequest(row)"
              >
                取消
              </el-button>
              <el-button v-perm="'bb:request:query'" link type="info" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
            :total="total" :page-sizes="[10, 20]" background layout="total, prev, pager, next" @current-change="fetchList" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="血袋库存" name="bags">
        <el-table v-loading="bagLoading" :data="bags" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="bagNo" label="血袋号" min-width="140" />
          <el-table-column label="血型" width="110" align="center">
            <template #default="{ row }">{{ bloodTypeLabel(row.bloodType) }} / {{ rhLabel(row.rh) }}</template>
          </el-table-column>
          <el-table-column label="成分" width="90" align="center">
            <template #default="{ row }">{{ componentLabel(row.component) }}</template>
          </el-table-column>
          <el-table-column prop="volumeMl" label="容量ml" width="90" align="center" />
          <el-table-column label="失效日期" width="130" align="center">
            <template #default="{ row }">
              <!-- 一百四十九轮 D-30：过期/临期血袋显性标识——原与正常袋同样式平铺 -->
              <span :style="bagExpireStyle(row)">
                {{ row.expireDate }}{{ isBagExpired(row) ? '（已过期）' : bagExpiringSoon(row) ? '（临期）' : '' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ { 1: '在库', 2: '已发用', 3: '报废', 4: '退回' }[row.status] ?? row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 1" v-perm="'bb:bags:manage'" link type="danger" @click="handleScrap(row)">报废</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="bagQuery.pageNum" v-model:page-size="bagQuery.pageSize"
            :total="bagTotal" :page-sizes="[20, 50]" background
            layout="total, sizes, prev, pager, next" @current-change="fetchBags" @size-change="() => { bagQuery.pageNum = 1; fetchBags() }" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="reqVisible" title="用血申请" width="500px" destroy-on-close>
      <el-form :model="reqForm" label-width="100px">
        <el-form-item label="就诊ID" required><el-input-number v-model="reqForm.admissionId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="患者" required>
          <RemotePicker v-model="reqForm.patientId" kind="patient" placeholder="输入姓名/建档号检索患者" />
        </el-form-item>
        <el-form-item label="血型" required>
          <el-select v-model="reqForm.bloodType" style="width: 100%">
            <el-option v-for="o in BB_BLOOD_TYPE" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="Rh" required>
          <el-select v-model="reqForm.rh" style="width: 100%">
            <el-option :value="1" label="阳性" /><el-option :value="2" label="阴性" />
          </el-select>
        </el-form-item>
        <el-form-item label="血液成分" required>
          <el-select v-model="reqForm.component" style="width: 100%">
            <el-option v-for="o in BB_COMPONENT" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请量ml" required><el-input-number v-model="reqForm.volumeMl" :min="50" :max="5000" :step="50" style="width: 100%" /></el-form-item>
        <el-form-item label="用血目的"><el-input v-model="reqForm.usePurpose" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reqVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateReq">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bagVisible" title="血袋入库" width="500px" destroy-on-close>
      <el-form :model="bagForm" label-width="100px">
        <el-form-item label="血袋号" required><el-input v-model="bagForm.bagNo" placeholder="如 XDJ20260010" /></el-form-item>
        <el-form-item label="血型" required>
          <el-select v-model="bagForm.bloodType" style="width: 100%">
            <el-option v-for="o in BB_BLOOD_TYPE" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="Rh" required>
          <el-select v-model="bagForm.rh" style="width: 100%">
            <el-option :value="1" label="阳性" /><el-option :value="2" label="阴性" />
          </el-select>
        </el-form-item>
        <el-form-item label="成分" required>
          <el-select v-model="bagForm.component" style="width: 100%">
            <el-option v-for="o in BB_COMPONENT" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="失效日期" required><el-date-picker v-model="bagForm.expireDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bagVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateBag">入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="crossVisible" title="交叉配血" width="520px" destroy-on-close>
      <el-form :model="crossForm" label-width="100px">
        <el-form-item label="血袋（在库）" required>
          <el-select v-model="crossForm.bagId" style="width: 100%">
            <el-option v-for="b in availableBags" :key="b.id" :value="b.id"
              :label="`${b.bagNo} ${bloodTypeLabel(b.bloodType)} ${componentLabel(b.component)} 效期${b.expireDate}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="配血方法" required><el-input v-model="crossForm.crossMethod" placeholder="盐水介质/抗人球" /></el-form-item>
        <el-form-item label="配血结果" required>
          <el-radio-group v-model="crossForm.crossResult">
            <el-radio :value="1">相容</el-radio>
            <el-radio :value="2">不相容</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert type="warning" :closable="false" title="不相容配血后该血袋将被禁止发血（患者安全硬阻断）" class="mb8" />
      </el-form>
      <template #footer>
        <el-button @click="crossVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCross">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="issueVisible" title="发血（取血护士签收）" width="440px" destroy-on-close>
      <el-form :model="issueForm" label-width="110px">
        <el-form-item label="取血护士" required>
          <RemotePicker v-model="issueForm.receiverId" kind="staff" placeholder="输入姓名/账号检索护士" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" @click="handleIssue">发血</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transVisible" title="输血开始（床边双人双签）" width="500px" destroy-on-close>
      <el-form :model="transForm" label-width="120px">
        <el-form-item label="血袋ID" required><el-input-number v-model="transForm.bagId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="核对签1（本人）"><el-input :model-value="currentUserName" disabled /></el-form-item>
        <el-form-item label="核对签2" required>
          <RemotePicker v-model="transForm.checker2Id" kind="staff" placeholder="双人核对第二人（姓名/账号）" />
        </el-form-item>
        <el-form-item label="输血前体征"><el-input v-model="transForm.vitalBefore" placeholder="T/P/R/BP JSON 或文本" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTransfusion">开始输注</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finishVisible" title="输血结束" width="440px" destroy-on-close>
      <el-form :model="finishForm" label-width="100px">
        <el-form-item label="转归" required>
          <el-radio-group v-model="finishForm.outcome">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="2">有反应</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="finishForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFinish">结束</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adverseVisible" title="输血不良反应登记" width="500px" destroy-on-close>
      <el-form :model="adverseForm" label-width="100px">
        <el-form-item label="类型" required>
          <el-select v-model="adverseForm.type" style="width: 100%">
            <el-option :value="1" label="发热" /><el-option :value="2" label="过敏" />
            <el-option :value="3" label="溶血" /><el-option :value="4" label="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度" required>
          <el-radio-group v-model="adverseForm.severity">
            <el-radio :value="1">轻</el-radio><el-radio :value="2">中</el-radio><el-radio :value="3">重</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置记录" required><el-input v-model="adverseForm.handleNote" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adverseVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdverse">登记</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="用血详情" size="600px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请号">{{ (detail.request as BbRequest)?.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ bbStatusLabel((detail.request as BbRequest)?.status ?? 0) }}</el-descriptions-item>
        </el-descriptions>
        <div class="mt8">配血记录</div>
        <el-table :data="(detail.crossMatches as Array<Record<string, unknown>>) || []" border size="small" class="mt4">
          <el-table-column prop="bagId" label="血袋ID" width="80" align="center" />
          <el-table-column prop="crossMethod" label="方法" min-width="100" />
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.crossResult === 1 ? 'success' : 'danger'">
                {{ row.crossResult === 1 ? '相容' : '不相容' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt8">输注执行</div>
        <el-table :data="(detail.transfusions as Array<Record<string, unknown>>) || []" border size="small" class="mt4">
          <el-table-column prop="startTime" label="开始" width="150" />
          <el-table-column prop="endTime" label="结束" width="150" />
          <el-table-column prop="checker1Name" label="核对1" width="90" align="center" show-overflow-tooltip />
          <el-table-column prop="checker2Name" label="核对2" width="90" align="center" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import RemotePicker from '@/components/RemotePicker.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  bbStatusTagType, bbStatusLabel, bloodTypeLabel, componentLabel, rhLabel,
  BB_BLOOD_TYPE, BB_COMPONENT, cancelRequest, createBag, createRequest,
  crossMatch, finishTransfusion, getAvailableBags, getBagPage, getRequestDetail,
  getRequestPage, issueBlood, reportAdverse, reviewRequest, scrapBag, startTransfusion,
  type BbBag, type BbRequest,
} from '@/api/bb'
import { useUserStore } from '@/stores/user'

const auth = useUserStore()
const currentUserName = auth.userInfo?.realName ?? String(auth.userInfo?.userId ?? '')

const tab = ref('req')
const loading = ref(false)
const bagLoading = ref(false)
const list = ref<BbRequest[]>([])
const bags = ref<BbBag[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: undefined as number | undefined })

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getRequestPage({ pageNum: query.pageNum, pageSize: query.pageSize, status: query.status })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } finally { loading.value = false }
}
async function fetchBags() {
  bagLoading.value = true
  try {
    const res = await getBagPage({ pageNum: bagQuery.pageNum, pageSize: bagQuery.pageSize, status: 1, bloodType: bagQuery.bloodType })
    bags.value = res.list ?? []
    bagTotal.value = res.total ?? 0
  } finally { bagLoading.value = false }
}

/** 一百四十九轮 D-30 配套：库存分页/血型筛选 + 效期标识 */
const bagQuery = reactive({ pageNum: 1, pageSize: 20, bloodType: undefined as number | undefined })
const bagTotal = ref(0)
function isBagExpired(row: { expireDate?: string | null }) {
  return !!row.expireDate && row.expireDate < new Date().toISOString().slice(0, 10)
}
function bagExpiringSoon(row: { expireDate?: string | null }) {
  if (!row.expireDate) return false
  const diff = (new Date(row.expireDate).getTime() - Date.now()) / 86400000
  return diff >= 0 && diff <= 7
}
function bagExpireStyle(row: { expireDate?: string | null }) {
  return isBagExpired(row) ? 'color:#f56c6c;font-weight:700' : bagExpiringSoon(row) ? 'color:#e6a23c' : ''
}

const reqVisible = ref(false)
const reqForm = reactive<{ admissionId?: number; patientId?: number; bloodType: number; rh: number; component: number; volumeMl: number; usePurpose: string }>(
  { admissionId: undefined, patientId: undefined, bloodType: 4, rh: 1, component: 1, volumeMl: 200, usePurpose: '' })

function resetReqForm() {
  Object.assign(reqForm, { admissionId: undefined, patientId: undefined, bloodType: 4, rh: 1, component: 1, volumeMl: 200, usePurpose: '' })
}
function resetBagForm() {
  Object.assign(bagForm, { bagNo: '', bloodType: 4, rh: 1, component: 1, expireDate: '' })
}
async function handleCreateReq() {
  if (!reqForm.admissionId || !reqForm.patientId) {
    ElMessage.warning('请填写就诊与患者')
    return
  }
  await createRequest({ admissionId: reqForm.admissionId, patientId: reqForm.patientId, bloodType: reqForm.bloodType, rh: reqForm.rh, component: reqForm.component, volumeMl: reqForm.volumeMl, usePurpose: reqForm.usePurpose || undefined })
  ElMessage.success('用血申请已提交')
  reqVisible.value = false
  fetchList()
}

async function handleReview(row: BbRequest, approved: boolean) {
  let note: string | undefined
  if (!approved) {
    // 一百二十四轮【6】P3：驳回理由必填（对齐 ORS 范式——审计事件 approved:false 无 reason 留痕缺失）
    try {
      const { value } = await ElMessageBox.prompt(`驳回用血申请 ${row.reqNo}，请填写理由：`, '驳回用血申请', {
        type: 'warning',
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '驳回理由（必填）',
      })
      note = (value ?? '').trim()
    } catch {
      return
    }
    if (!note) {
      ElMessage.warning('请填写驳回理由')
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(`确认通过用血申请 ${row.reqNo}？`, '用血审核')
    } catch {
      return
    }
  }
  await reviewRequest(row.id, approved, note)
  ElMessage.success(approved ? '已通过' : '已驳回')
  fetchList()
}

const bagVisible = ref(false)
const bagForm = reactive<{ bagNo: string; bloodType: number; rh: number; component: number; expireDate: string }>({ bagNo: '', bloodType: 4, rh: 1, component: 1, expireDate: '' })

async function handleCreateBag() {
  if (!bagForm.bagNo || !bagForm.expireDate) {
    ElMessage.warning('请填写血袋号与失效日期')
    return
  }
  await createBag({ bagNo: bagForm.bagNo, bloodType: bagForm.bloodType, rh: bagForm.rh, component: bagForm.component, expireDate: bagForm.expireDate })
  ElMessage.success('血袋已入库')
  bagVisible.value = false
  fetchBags()
}

async function handleScrap(row: BbBag) {
  try {
    await ElMessageBox.confirm(`确认报废血袋 ${row.bagNo}？`, '血袋报废')
    await scrapBag(row.id)
    ElMessage.success('已报废')
    fetchBags()
  } catch { /* 取消 */ }
}

let currentRow: BbRequest | null = null
const availableBags = ref<BbBag[]>([])
const crossVisible = ref(false)
const crossForm = reactive<{ bagId?: number; crossMethod: string; crossResult: number; note: string }>({ bagId: undefined, crossMethod: '盐水介质', crossResult: 1, note: '' })

async function openCross(row: BbRequest) {
  currentRow = row
  availableBags.value = await getAvailableBags({ bloodType: row.bloodType, component: row.component })
  // 一百二十四轮【1】P1：openCross 只重置 bagId——crossMethod/crossResult/note 残留上一次
  // 打开的值（"不相容"被误带到下一单，血袋被误禁发），补全字段重置
  crossForm.bagId = undefined
  crossForm.crossMethod = '盐水介质'
  crossForm.crossResult = 1
  crossForm.note = ''
  crossVisible.value = true
}

async function handleCross() {
  if (!currentRow || !crossForm.bagId || !crossForm.crossMethod) {
    ElMessage.warning('请选择血袋与配血方法')
    return
  }
  await crossMatch(currentRow.id, { bagId: crossForm.bagId, crossMethod: crossForm.crossMethod, crossResult: crossForm.crossResult, note: crossForm.note || undefined })
  ElMessage.success(crossForm.crossResult === 1 ? '配血相容，可发血' : '不相容已登记（该血袋禁止发血）')
  crossVisible.value = false
  fetchList()
}

const issueVisible = ref(false)
const issueForm = reactive<{ receiverId?: number }>({ receiverId: undefined })

function openIssue(row: BbRequest) {
  currentRow = row
  issueForm.receiverId = undefined
  issueVisible.value = true
}

async function handleIssue() {
  if (!currentRow || !issueForm.receiverId) {
    ElMessage.warning('请填写取血护士')
    return
  }
  // 发血目标血袋：最近一次相容配血的血袋（从详情取）
  const detail = await getRequestDetail(currentRow.id)
  const matches = (detail.crossMatches as Array<Record<string, unknown>>) ?? []
  const compatible = matches.filter((m) => m.crossResult === 1)
  const last = compatible[compatible.length - 1]
  if (!last) {
    ElMessage.warning('无相容配血记录')
    return
  }
  await issueBlood(currentRow.id, { bagId: last.bagId as number, receiverId: issueForm.receiverId })
  ElMessage.success('发血完成（取血护士已签收）')
  issueVisible.value = false
  fetchList()
}

const transVisible = ref(false)
const transForm = reactive<{ bagId?: number; checker2Id?: number; vitalBefore: string }>({ bagId: undefined, checker2Id: undefined, vitalBefore: '' })

async function openTransfusion(row: BbRequest) {
  currentRow = row
  transForm.bagId = undefined
  transForm.checker2Id = undefined
  transForm.vitalBefore = ''
  transVisible.value = true
  // 一百二十四轮【5】P3：按详情查已发血袋自动预填（原让用户手填袋 ID，错输仅靠后端兜底）
  try {
    const detail = await getRequestDetail(row.id)
    // issues 为已发血记录（bagId 即发血袋）
    const issued = (detail.issues ?? []) as Array<{ bagId?: number }>
    if (issued.length > 0 && issued[0].bagId) {
      transForm.bagId = issued[0].bagId
    }
  } catch {
    // 详情取不到则留空手填（后端 startTransfusion 硬校验兜底）
  }
}

async function handleTransfusion() {
  if (!currentRow || !transForm.bagId || !transForm.checker2Id) {
    ElMessage.warning('请填写血袋与第二核对签')
    return
  }
  await startTransfusion(currentRow.id, { bagId: transForm.bagId, checker1Id: auth.userInfo!.userId, checker2Id: transForm.checker2Id, vitalBefore: transForm.vitalBefore || undefined })
  ElMessage.success('双人双签通过，输注开始')
  transVisible.value = false
  fetchList()
}

const finishVisible = ref(false)
const finishForm = reactive<{ outcome: number; note: string }>({ outcome: 1, note: '' })

function openFinish(row: BbRequest) {
  currentRow = row
  finishForm.outcome = 1
  finishForm.note = ''
  finishVisible.value = true
}

async function handleFinish() {
  if (!currentRow) return
  await finishTransfusion(currentRow.id, { outcome: finishForm.outcome, note: finishForm.note || undefined })
  ElMessage.success('输血闭环完成')
  finishVisible.value = false
  fetchList()
}

const adverseVisible = ref(false)
const adverseForm = reactive<{ type: number; severity: number; handleNote: string }>({ type: 1, severity: 1, handleNote: '' })

function openAdverse(row: BbRequest) {
  currentRow = row
  adverseForm.type = 1
  adverseForm.severity = 1
  adverseForm.handleNote = ''
  adverseVisible.value = true
}

async function handleAdverse() {
  if (!currentRow || !adverseForm.handleNote) {
    ElMessage.warning('请填写处置记录')
    return
  }
  await reportAdverse(currentRow.id, { type: adverseForm.type, severity: adverseForm.severity, handleNote: adverseForm.handleNote })
  ElMessage.success('不良反应已登记')
  adverseVisible.value = false
}

const detailVisible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)

async function openDetail(row: BbRequest) {
  detail.value = await getRequestDetail(row.id)
  detailVisible.value = true
}

/** 一百一十九轮【4】P2：用血申请取消（后端条件更新 10/20/30→70） */
async function handleCancelRequest(row: BbRequest) {
  try {
    await ElMessageBox.confirm(
      `确认取消用血申请 ${row.reqNo}？取消后该申请终止。`,
      '取消用血申请',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await cancelRequest(row.id)
    ElMessage.success('用血申请已取消')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

onMounted(() => { fetchList(); fetchBags() })
</script>

<style scoped>
.mt4 { margin-top: 4px; }
.mt8 { margin-top: 8px; font-weight: 600; }
.mb8 { margin-bottom: 8px; }
</style>
