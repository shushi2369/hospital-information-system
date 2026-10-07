<template>
  <div class="page-card">
    <!-- 筛选区 -->
    <el-form class="search-bar" inline @submit.prevent>
      <el-form-item label="状态">
        <el-radio-group v-model="query.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button v-for="opt in ALERT_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
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
      <span class="toolbar-title">危急值列表</span>
      <span class="status-legend">
        闭环流程：待处理 → 已通知（技师登记）→ 已确认（医生）→ 已闭环（填写处置意见）
      </span>
    </div>

    <!-- 危急值表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="alertNo" label="危急值号" min-width="130" show-overflow-tooltip />
      <el-table-column label="来源" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ alertSourceLabel(row.source) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="itemName" label="项目" min-width="100" show-overflow-tooltip />
      <el-table-column label="危急值" width="90" align="right">
        <template #default="{ row }">
          <span class="critical-value">{{ row.criticalValue }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="patientId" label="患者ID" width="80" align="center" />
      <el-table-column prop="admissionId" label="就诊ID" width="80" align="center">
        <template #default="{ row }">{{ row.admissionId ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="requestId" label="申请单ID" width="90" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="alertStatusTagType(row.status)">
            {{ alertStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通知登记" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.notifiedAt">
            {{ row.notifiedNurseName ?? 'ID ' + row.notifiedNurse }}<br />{{ row.notifiedAt }}
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="医生确认" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.confirmedAt">
            {{ row.confirmedDoctorName ?? 'ID ' + row.confirmedDoctor }}<br />{{ row.confirmedAt }}
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="handleNote" label="处置意见" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.handleNote || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            v-perm="'alert:notify'"
            link
            type="warning"
            @click="handleNotify(row)"
          >
            登记通知
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'alert:confirm'"
            link
            type="primary"
            @click="handleConfirm(row)"
          >
            确认
          </el-button>
          <el-button
            v-if="row.status === 30"
            v-perm="'alert:confirm'"
            link
            type="success"
            @click="openCloseDialog(row)"
          >
            闭环
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

    <!-- 闭环弹窗 -->
    <el-dialog v-model="closeDialogVisible" title="危急值闭环" width="480px" destroy-on-close append-to-body>
      <div v-if="closeRow" class="close-line">
        {{ closeRow.alertNo }}｜{{ closeRow.itemName }}：{{ closeRow.criticalValue }}
      </div>
      <el-form label-width="80px">
        <el-form-item label="处置意见">
          <el-input
            v-model="handleNote"
            type="textarea"
            :rows="3"
            maxlength="200"
            placeholder="选填，如已复测、已调整用药等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="closeSubmitting" @click="handleCloseSubmit">
          确认闭环
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
  ALERT_STATUS_OPTIONS,
  alertSourceLabel,
  alertStatusLabel,
  alertStatusTagType,
  closeAlert,
  confirmAlert,
  getAlertPage,
  notifyAlert,
  type AlertItem,
} from '@/api/lis'

// ---------------- 列表查询 ----------------
const loading = ref(false)
const list = ref<AlertItem[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: 10 as number | undefined, // 活跃态默认过滤：打开即看待处理
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getAlertPage({
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

// ---------------- 技师登记通知（10 → 20） ----------------
async function handleNotify(row: AlertItem) {
  try {
    await ElMessageBox.confirm(
      `确认登记危急值 ${row.alertNo}（${row.itemName}：${row.criticalValue}）已通知临床？`,
      '登记通知',
      { type: 'warning', confirmButtonText: '确认登记', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await notifyAlert(row.id)
    ElMessage.success('通知登记成功')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 医生确认（20 → 30） ----------------
async function handleConfirm(row: AlertItem) {
  try {
    await ElMessageBox.confirm(
      `确认危急值 ${row.alertNo}（${row.itemName}：${row.criticalValue}）已由医生知晓并处理？`,
      '危急值确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await confirmAlert(row.id)
    ElMessage.success('危急值确认成功')
    fetchList()
  } catch {
    // 拦截器已统一提示
  }
}

// ---------------- 闭环（30 → 40） ----------------
const closeDialogVisible = ref(false)
const closeSubmitting = ref(false)
const closeRow = ref<AlertItem | null>(null)
const handleNote = ref('')

function openCloseDialog(row: AlertItem) {
  closeRow.value = row
  handleNote.value = ''
  closeDialogVisible.value = true
}

async function handleCloseSubmit() {
  if (!closeRow.value) return
  closeSubmitting.value = true
  try {
    await closeAlert(closeRow.value.id, handleNote.value.trim() || undefined)
    ElMessage.success('危急值已闭环')
    closeDialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    closeSubmitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.status-legend {
  font-size: 12px;
  color: #909399;
}

.critical-value {
  color: #f56c6c;
  font-weight: 700;
}

.close-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}
</style>
