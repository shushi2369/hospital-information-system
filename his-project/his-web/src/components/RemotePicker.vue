<template>
  <!--
    一百五十八轮：统一远程检索选择器（裸 ID 输入家族总解）。
    kind: patient → /common/patient-search（急诊分诊/用血申请等）
          staff   → /common/staff-search（取血护士/核查第二签名/输血核对签2）
          doctor  → /common/doctor-search（排台主刀）
    v-model 绑定数字 id；最小字段展示，不含 PII。
  -->
  <el-select
    :model-value="modelValue ?? undefined"
    filterable
    remote
    clearable
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="onPick"
  >
    <el-option
      v-for="o in options"
      :key="o.id"
      :label="o.label"
      :value="o.id"
    >
      <span>{{ o.label }}</span>
      <span class="pick-sub">{{ o.sub }}</span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { get } from '@/api/request'

const props = withDefaults(defineProps<{
  modelValue?: number | null
  kind: 'patient' | 'staff' | 'doctor'
  placeholder?: string
}>(), {
  placeholder: '输入关键词检索',
})

const emit = defineEmits<{ (e: 'update:modelValue', v: number | undefined): void }>()

interface Opt { id: number; label: string; sub: string }

const options = ref<Opt[]>([])
const loading = ref(false)
let searchSequence = 0

const ENDPOINT: Record<string, string> = {
  patient: '/common/patient-search',
  staff: '/common/staff-search',
  doctor: '/common/doctor-search',
}

async function search(keyword: string) {
  // 清空输入也使在途请求失效；只有最新检索可以更新结果和加载状态。
  const sequence = ++searchSequence
  if (!keyword || !keyword.trim()) {
    options.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    const rows = (await get<Array<Record<string, unknown>>>(ENDPOINT[props.kind], { keyword: keyword.trim() })) ?? []
    if (sequence !== searchSequence) return
    options.value = rows.map((r) => ({
      id: Number(r.id),
      label: String(r.name ?? ''),
      sub: String(r.patientNo ?? r.username ?? r.title ?? ''),
    }))
  } catch {
    if (sequence === searchSequence) options.value = []
  } finally {
    if (sequence === searchSequence) loading.value = false
  }
}

function onPick(v: number | undefined) {
  emit('update:modelValue', v ?? undefined)
}
</script>

<style scoped>
.pick-sub {
  float: right;
  color: #909399;
  font-size: 12px;
  margin-left: 12px;
}
</style>
