<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in AE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
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
      <span class="toolbar-title">不良事件列表</span>
      <span class="status-legend">
        处理流程：上报 → 质控派单 → 科室整改登记 → 质控关闭
      </span>
      <el-button
        v-perm="'ae:report'"
        type="warning"
        plain
        size="small"
        class="toolbar-action"
        @click="resetReportForm(); reportDialogVisible = true"
      >
        上报不良事件
      </el-button>
    </div>

    <!-- 不良事件表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="eventNo" label="事件号" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ aeEventTypeLabel(row.eventType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="严重程度" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="aeSeverityTagType(row.severity)">
            {{ aeSeverityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="发生科室" width="110" align="center" show-overflow-tooltip />
      <el-table-column prop="eventTime" label="发生时间" min-width="150" show-overflow-tooltip />
      <el-table-column prop="description" label="事件描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="aeStatusTagType(row.status)">
            {{ aeStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handlerNote" label="整改/处理记录" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.handlerNote || '-' }}</template>
      </el-table-column>
      <el-table-column prop="closedTime" label="关闭时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.closedTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            v-perm="'ae:qc:assign'"
            link
            type="primary"
            @click="handleAssign(row)"
          >
            派单
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'ae:report'"
            link
            type="warning"
            @click="openRectifyDialog(row)"
          >
            整改登记
          </el-button>
          <el-button
            v-if="row.status === 30"
            v-perm="'ae:close'"
            link
            type="success"
            @click="handleClose(row)"
          >
            关闭
          </el-button>
          <span v-if="row.status === 40">-</span>
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

    <!-- 上报弹窗 -->
    <el-dialog v-model="reportDialogVisible" title="上报不良事件" width="560px" destroy-on-close append-to-body>
      <el-form label-width="90px">
        <el-form-item label="事件类型" required>
          <el-select v-model="reportForm.eventType" style="width: 100%">
            <el-option v-for="opt in AE_EVENT_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度" required>
          <el-radio-group v-model="reportForm.severity">
            <el-radio-button v-for="opt in AE_SEVERITY_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发生科室" required>
          <!-- 一百一十轮 A2：裸 ID 手输改科室下拉，防错输不存在的科室 -->
          <el-select v-model="reportForm.departmentId" filterable style="width: 100%" placeholder="选择发生科室">
            <el-option v-for="d in deptOptions" :key="d.id" :value="d.id" :label="d.deptName" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生时间" required>
          <el-date-picker
            v-model="reportForm.eventTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="事件描述" required>
          <el-input
            v-model="reportForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="客观描述事件经过，不针对个人"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reportSubmitting" @click="handleReportSubmit">提交上报</el-button>
      </template>
    </el-dialog>

    <!-- 整改登记弹窗（20 → 30） -->
    <el-dialog v-model="rectifyDialogVisible" title="整改登记" width="520px" destroy-on-close append-to-body>
      <div v-if="rectifyRow" class="dialog-line">{{ rectifyRow.eventNo }}｜{{ aeEventTypeLabel(rectifyRow.eventType) }}</div>
      <el-form label-width="90px">
        <el-form-item label="整改措施" required>
          <el-input
            v-model="handlerNote"
            type="textarea"
            :rows="3"
            maxlength="300"
            placeholder="已采取的整改措施与责任人"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rectifySubmitting" @click="handleRectifySubmit">
          登记整改
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
  AE_EVENT_TYPE_OPTIONS,
  AE_SEVERITY_OPTIONS,
  AE_STATUS_OPTIONS,
  aeEventTypeLabel,
  aeSeverityLabel,
  aeSeverityTagType,
  aeStatusLabel,
  aeStatusTagType,
  assignAeEvent,
  closeAeEvent,
  getAePage,
  rectifyAeEvent,
  reportAeEvent,
  type AeEvent,
} from '@/api/ae'
import { getDepartmentListCached, type Department } from '@/api/basedata'

// 发生科室下拉（一百零九轮 #A2：裸 ID 手输改下拉，防错输不存在的科室）
const deptOptions = ref<Department[]>([])
onMounted(async () => {
  try {
    deptOptions.value = (await getDepartmentListCached({ status: 1 })) ?? []
  } catch {
    // 拦截器已统一提示
  }
})

// ---------------- 列表查询 ----------------
const loading = ref(false)
const list = ref<AeEvent[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: 10 as number | undefined, // 活跃态默认过滤：打开即看待派单
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getAePage({
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

// ---------------- 上报（→ 10） ----------------
const reportDialogVisible = ref(false)
const reportSubmitting = ref(false)
const reportForm = reactive({
  eventType: undefined as number | undefined,
  severity: 1,
  departmentId: undefined as number | undefined,
  eventTime: '',
  description: '',
})

function resetReportForm() {
  reportForm.eventType = undefined
  reportForm.severity = 1
  reportForm.departmentId = undefined
  reportForm.eventTime = ''
  reportForm.description = ''
}

async function handleReportSubmit() {
  if (!reportForm.eventType || !reportForm.departmentId || !reportForm.eventTime || !reportForm.description.trim()) {
    ElMessage.warning('请完整填写事件信息')
    return
  }
  reportSubmitting.value = true
  try {
    const eventNo = await reportAeEvent({
      eventType: reportForm.eventType,
      severity: reportForm.severity,
      departmentId: reportForm.departmentId,
      eventTime: reportForm.eventTime,
      description: reportForm.description.trim(),
    })
    ElMessage.success(`不良事件已上报：${eventNo}`)
    reportDialogVisible.value = false
    resetReportForm()
    handleSearch()
  } catch {
    // 拦截器已统一提示
  } finally {
    reportSubmitting.value = false
  }
}

/**
 * 过滤 tab 下操作成功后不重置页码（一百零九轮 #A4）：
 * 留在当前页刷新；若当前页被推进掏空则回退一页，避免"操作成功后列表空白"。
 */
async function refreshKeepPage() {
  await fetchList()
  if (!list.value.length && query.pageNum > 1) {
    query.pageNum -= 1
    await fetchList()
  }
}

// ---------------- 质控派单（10 → 20） ----------------
async function handleAssign(row: AeEvent) {
  try {
    await ElMessageBox.confirm(
      `将事件 ${row.eventNo}（${aeEventTypeLabel(row.eventType)}）派单至责任科室整改？`,
      '质控派单',
      { type: 'warning', confirmButtonText: '确认派单', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await assignAeEvent(row.id)
    ElMessage.success('派单成功，待科室整改')
    refreshKeepPage()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 整改登记（20 → 30） ----------------
const rectifyDialogVisible = ref(false)
const rectifySubmitting = ref(false)
const rectifyRow = ref<AeEvent | null>(null)
const handlerNote = ref('')

function openRectifyDialog(row: AeEvent) {
  rectifyRow.value = row
  handlerNote.value = ''
  rectifyDialogVisible.value = true
}

async function handleRectifySubmit() {
  if (!rectifyRow.value) return
  if (!handlerNote.value.trim()) {
    ElMessage.warning('请填写整改措施')
    return
  }
  rectifySubmitting.value = true
  try {
    await rectifyAeEvent(rectifyRow.value.id, handlerNote.value.trim())
    ElMessage.success('整改登记完成，待质控关闭')
    rectifyDialogVisible.value = false
    refreshKeepPage()
  } catch {
    // 拦截器已统一提示
  } finally {
    rectifySubmitting.value = false
  }
}

// ---------------- 关闭（30 → 40） ----------------
async function handleClose(row: AeEvent) {
  try {
    await ElMessageBox.confirm(
      `确认关闭事件 ${row.eventNo}？关闭后进入统计，不再变更。`,
      '不良事件关闭',
      { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await closeAeEvent(row.id)
    ElMessage.success('事件已关闭')
    refreshKeepPage()
  } catch {
    // 拦截器已统一提示
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
