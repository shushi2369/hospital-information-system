<template>
  <div class="page-card">
    <!-- 主索引搜索 -->
    <el-form class="search-bar" :model="query" inline>
      <el-form-item label="姓名">
        <el-input
          v-model="query.name"
          placeholder="请输入姓名"
          clearable
          style="width: 150px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="主索引号">
        <el-input
          v-model="query.mpiNo"
          placeholder="请输入主索引号"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="建档号">
        <el-input
          v-model="query.patientNo"
          placeholder="请输入建档号"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <span class="toolbar-title">患者主索引</span>
    </div>

    <!-- 主索引表格 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="mpiNo" label="主索引号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="patientName" label="姓名" min-width="90" show-overflow-tooltip>
        <template #default="{ row }">{{ row.patientName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="patientNo" label="建档号" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.patientNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="patientId" label="患者ID" width="90" align="center">
        <template #default="{ row }">{{ row.patientId ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="合并状态" width="95" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.mergeFlag === 1 ? 'danger' : 'success'">
            {{ mergeFlagLabel(row.mergeFlag) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="mergedInto" label="并入索引" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.mergedInto || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
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

    <!-- 事件查询区 -->
    <div class="event-area">
      <div class="table-toolbar">
        <span class="toolbar-title">主索引事件查询</span>
      </div>
      <el-form class="search-bar" :model="eventQuery" inline>
        <el-form-item label="事件类型">
          <el-input
            v-model="eventQuery.eventType"
            placeholder="如 PATIENT_CREATED"
            clearable
            style="width: 180px"
            @keyup.enter="fetchEvents"
          />
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input
            v-model="eventQuery.bizNo"
            placeholder="关联业务单号"
            clearable
            style="width: 170px"
            @keyup.enter="fetchEvents"
          />
        </el-form-item>
        <el-form-item label="发生日期">
          <el-date-picker
            v-model="eventRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchEvents">查询事件</el-button>
          <el-button :icon="Refresh" @click="handleEventReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="eventLoading" :data="events" border stripe size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <pre class="payload-pre">{{ payloadText(row.payload) }}</pre>
          </template>
        </el-table-column>
        <el-table-column prop="eventNo" label="事件号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="eventType" label="事件类型" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.eventType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发生时间" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 主索引详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="主索引详情" size="520px" destroy-on-close>
      <div v-loading="drawerLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="主索引号">{{ detail.mpiNo }}</el-descriptions-item>
            <el-descriptions-item label="合并状态">
              <el-tag size="small" :type="detail.mergeFlag === 1 ? 'danger' : 'success'">
                {{ mergeFlagLabel(detail.mergeFlag) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="并入索引">{{ detail.mergedInto || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.patient" class="drawer-section">关联患者档案</div>
          <el-descriptions v-if="detail.patient" :column="1" border size="small">
            <el-descriptions-item label="患者ID">{{ detail.patient.id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="建档号">{{ detail.patient.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ detail.patient.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ genderLabel(detail.patient.gender) }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ detail.patient.birthDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.patient.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="身份证号">{{ detail.patient.idCardNo || '-' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { genderLabel } from '@/api/patient'
import { mergeFlagLabel, payloadText, searchMpiIndex, getMpiDetail, getPltEvents, type MpiIndex, type MpiDetail, type PltEvent } from '@/api/plt'

// ---------------- 主索引搜索 ----------------
const loading = ref(false)
const list = ref<MpiIndex[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  mpiNo: '',
  patientNo: '',
})

async function fetchList() {
  loading.value = true
  try {
    const res = await searchMpiIndex({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      name: query.name.trim() || undefined,
      mpiNo: query.mpiNo.trim() || undefined,
      patientNo: query.patientNo.trim() || undefined,
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

function handleReset() {
  query.name = ''
  query.mpiNo = ''
  query.patientNo = ''
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

// ---------------- 详情抽屉 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const detail = ref<MpiDetail | null>(null)

async function openDetail(row: MpiIndex) {
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  try {
    detail.value = await getMpiDetail(row.mpiNo)
  } catch {
    detail.value = null
  } finally {
    drawerLoading.value = false
  }
}

// ---------------- 事件查询 ----------------
const eventLoading = ref(false)
const events = ref<PltEvent[]>([])
const eventRange = ref<[string, string] | null>(null)
const eventQuery = reactive({
  eventType: '',
  bizNo: '',
})

async function fetchEvents() {
  eventLoading.value = true
  try {
    events.value = await getPltEvents({
      eventType: eventQuery.eventType.trim() || undefined,
      bizNo: eventQuery.bizNo.trim() || undefined,
      startDate: eventRange.value?.[0] || undefined,
      endDate: eventRange.value?.[1] || undefined,
    })
  } catch {
    events.value = []
  } finally {
    eventLoading.value = false
  }
}

function handleEventReset() {
  eventQuery.eventType = ''
  eventQuery.bizNo = ''
  eventRange.value = null
  fetchEvents()
}

onMounted(() => {
  fetchList()
  fetchEvents()
})
</script>

<style scoped>
.event-area {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.payload-pre {
  margin: 0;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow-y: auto;
}

.drawer-section {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}
</style>
