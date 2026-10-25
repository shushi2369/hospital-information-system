<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="toolbar-title">我的候诊队列（今日）</span>
      <el-button :icon="Refresh" :loading="loading" @click="fetchQueue">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="queue" border stripe>
      <el-table-column prop="queueNo" label="排队号" width="90" align="center" />
      <el-table-column prop="patientName" label="患者" min-width="90" />
      <el-table-column prop="patientNo" label="建档号" min-width="120" show-overflow-tooltip />
      <el-table-column label="号别" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.regType === 2 ? 'warning' : 'info'">
            {{ regTypeLabel(row.regType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时段" width="80" align="center">
        <template #default="{ row }">{{ periodLabel(row.period) }}</template>
      </el-table-column>
      <el-table-column prop="regNo" label="挂号单号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="deptName" label="科室" min-width="110" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="regStatusTagType(row.status)">
            {{ regStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10"
            link
            type="primary"
            :loading="startingId === row.id"
            @click="handleStart(row)"
          >
            接诊
          </el-button>
          <!-- 一百四十五轮 U-4：已接诊/已完成行提供查看入口（完成态工作台自动只读） -->
          <el-button
            v-else-if="row.visitId"
            link
            type="primary"
            @click="router.push({ path: '/clinic/workbench', query: { visitId: String(row.visitId) } })"
          >
            查看
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getClinicQueue, startVisit } from '@/api/clinic'
import {
  periodLabel,
  regStatusTagType,
  regStatusLabel,
  regTypeLabel,
  type Registration,
} from '@/api/registration'
import { toList } from '@/api/request'

const router = useRouter()

const loading = ref(false)
const queue = ref<Registration[]>([])
const startingId = ref<number | null>(null)

async function fetchQueue() {
  loading.value = true
  try {
    // 一百四十五轮 U-3：待接诊(10)置顶——医生上班第一眼看到的是"还有谁等我"
    const list = toList<Registration>(await getClinicQueue())
    list.sort((a, b) => (a.status ?? 99) - (b.status ?? 99) || a.queueNo - b.queueNo)
    queue.value = list
  } finally {
    loading.value = false
  }
}

/** 接诊：开始就诊后携带 visitId 跳转医生工作台 */
async function handleStart(row: Registration) {
  startingId.value = row.id
  try {
    const res = await startVisit(row.id)
    await router.push({ path: '/clinic/workbench', query: { visitId: String(res) } })
  } catch {
    // B2001 就诊已完成等错误已在拦截器中统一提示
  } finally {
    startingId.value = null
  }
}

onMounted(fetchQueue)
</script>
