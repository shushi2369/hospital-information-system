<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="命中记录" name="hits">
        <el-table v-loading="loading" :data="hits" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="orderId" label="医嘱ID" width="100" align="center" />
          <el-table-column prop="doctorId" label="开单医生ID" width="110" align="center" />
          <el-table-column prop="message" label="提示" min-width="240" show-overflow-tooltip />
          <el-table-column label="医生处置" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.ignored === 1 ? 'warning' : 'info'">
                {{ row.ignored === 1 ? '坚持开立' : '已放弃' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hitTime" label="命中时间" width="160" align="center" show-overflow-tooltip />
        </el-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
            :total="total" :page-sizes="[10, 20]" background layout="total, prev, pager, next" @current-change="fetchHits" />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="canManageRule" label="规则维护" name="rules">
        <div class="table-toolbar">
          <span class="toolbar-title">CDSS 规则（提示级，不阻断开单）</span>
          <el-button type="primary" @click="ruleVisible = true">新建规则</el-button>
        </div>
        <el-table v-loading="ruleLoading" :data="rules" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="ruleCode" label="规则编码" min-width="130" />
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">{{ cdssTypeLabel(row.ruleType) }}</template>
          </el-table-column>
          <el-table-column prop="refAId" label="参照A" width="90" align="center" />
          <el-table-column prop="refBId" label="参照B" width="90" align="center">
            <template #default="{ row }">{{ row.refBId ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="message" label="提示文案" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="ruleVisible" title="新建规则" width="500px" destroy-on-close>
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则编码" required><el-input v-model="ruleForm.ruleCode" placeholder="如 CDSS-DUP-002" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option v-for="o in CDSS_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="参照A ID" required><el-input-number v-model="ruleForm.refAId" :min="1" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="参照B ID/上限"><el-input-number v-model="ruleForm.refBId" :min="0" :precision="0" style="width: 100%" /></el-form-item>
        <el-form-item label="提示文案" required><el-input v-model="ruleForm.message" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { createRule, cdssTypeLabel, CDSS_TYPE_OPTIONS, getHitPage, getRulePage, type CdssHit, type CdssRule } from '@/api/cdss'

const auth = useUserStore()
const canManageRule = computed(() => (auth.userInfo?.roleCodes ?? []).includes('ADMIN'))

const tab = ref('hits')
const loading = ref(false)
const ruleLoading = ref(false)
const hits = ref<CdssHit[]>([])
const rules = ref<CdssRule[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

async function fetchHits() {
  loading.value = true
  try {
    const res = await getHitPage({ pageNum: query.pageNum, pageSize: query.pageSize })
    hits.value = res.list ?? []
    total.value = res.total ?? 0
  } finally { loading.value = false }
}
async function fetchRules() {
  ruleLoading.value = true
  try {
    const res = await getRulePage({ pageNum: 1, pageSize: 50 })
    rules.value = res.list ?? []
  } finally { ruleLoading.value = false }
}

const ruleVisible = ref(false)
const ruleForm = reactive<{ ruleCode: string; ruleType: number; refAId?: number; refBId?: number; message: string }>({ ruleCode: '', ruleType: 2, refAId: undefined, refBId: undefined, message: '' })

async function handleCreateRule() {
  if (!ruleForm.ruleCode || !ruleForm.refAId || !ruleForm.message) {
    ElMessage.warning('请完整填写规则')
    return
  }
  await createRule({
    ruleCode: ruleForm.ruleCode, ruleType: ruleForm.ruleType, refAId: ruleForm.refAId,
    refBId: ruleForm.refBId, message: ruleForm.message,
  })
  ElMessage.success('规则已创建')
  ruleVisible.value = false
  fetchRules()
}

onMounted(() => { fetchHits(); if (canManageRule.value) fetchRules() })
</script>
