<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="toolbar-title">国考绩效监测（院内演示口径）</span>
      <el-button link type="primary" @click="fetchAll">刷新</el-button>
    </div>

    <el-row :gutter="12" class="kpi-row">
      <el-col v-for="card in workloadCards" :key="card.label" :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="kpi-row">
      <el-col v-for="card in effCards" :key="card.label" :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="kpi-row">
      <el-col v-for="card in safetyCards" :key="card.label" :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <div class="mt8">明细数据（CSV 导出与上方口径一致）</div>
    <el-descriptions :column="3" border size="small" class="mt4">
      <el-descriptions-item v-for="(v, k) in workload" :key="k" :label="String(k)">{{ v }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getEfficiency, getSafety, getWorkload } from '@/api/kpi'
import { useCountUp } from '@/composables/useCountUp'

const workload = ref<Record<string, number | string>>({})
const efficiency = ref<Record<string, number | string>>({})
const safety = ref<Record<string, number | string>>({})

const workloadCards = computed(() => [
  { label: '门诊人次', value: workload.value.outpatientVisits },
  { label: '住院人次', value: workload.value.inpatientAdmissions },
  { label: '完成手术台次', value: workload.value.surgeriesCompleted },
  { label: '开立医嘱数', value: workload.value.orders },
])
const effCards = computed(() => [
  { label: '平均住院日', value: efficiency.value.avgStayDays },
  { label: '床位使用率%', value: efficiency.value.bedUsageRate },
  { label: '次均费用', value: efficiency.value.avgBillAmount },
  { label: '账单数', value: efficiency.value.billsCount },
])
const safetyCards = computed(() => [
  { label: '危急值总数', value: safety.value.alertsTotal },
  { label: '危急值闭环率%', value: safety.value.alertCloseRate },
  { label: '完成手术', value: safety.value.surgeriesDone },
  { label: '手术核查率%', value: safety.value.surgeryCheckRate },
])

async function fetchAll() {
  workload.value = await getWorkload()
  efficiency.value = await getEfficiency()
  safety.value = await getSafety()
}

onMounted(fetchAll)
</script>

<style scoped>
.kpi-row { margin-bottom: 12px; }
.kpi-card {
  text-align: center;
  background: var(--ak-bg-card-alt) !important;
  border: 1px solid var(--ak-border-light) !important;
  border-top: 2px solid var(--ak-primary) !important;
  border-radius: 0 !important;
}
.kpi-label {
  color: var(--ak-text-sec) !important;
  font-size: 13px;
  letter-spacing: 0.5px;
}
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 6px;
  color: var(--ak-primary) !important;
  font-family: var(--ak-font-mono);
}
.mt4 { margin-top: 4px; }
.mt8 { margin-top: 12px; font-weight: 600; }
</style>
