<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="toolbar-title">危急值阈值</span>
      <el-button v-perm="'lab:threshold:manage'" type="primary" size="small" :icon="Plus" @click="openDialog()">
        新增阈值
      </el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="结果录入时按项目名匹配阈值判定危急项：危急下限/危急上限可空，留空表示不判该方向；同项目名重复保存为覆盖更新。"
    />

    <el-table v-loading="loading" :data="list" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="itemName" label="项目名" min-width="140" show-overflow-tooltip />
      <el-table-column label="危急下限" width="120" align="right">
        <template #default="{ row }">
          <span v-if="row.lowValue !== null && row.lowValue !== undefined">{{ fmtMoney(row.lowValue) }}</span>
          <span v-else class="not-judged">不判该方向</span>
        </template>
      </el-table-column>
      <el-table-column label="危急上限" width="120" align="right">
        <template #default="{ row }">
          <span v-if="row.highValue !== null && row.highValue !== undefined">{{ fmtMoney(row.highValue) }}</span>
          <span v-else class="not-judged">不判该方向</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'lab:threshold:manage'" link type="primary" @click="openDialog(row)">
            编辑
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑阈值弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑阈值' : '新增阈值'"
      width="460px"
      destroy-on-close
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="项目名" prop="itemName">
          <el-input v-model="form.itemName" placeholder="与检验结果项目名一致，如 白细胞计数" maxlength="64" />
        </el-form-item>
        <el-form-item label="危急下限" prop="lowValue">
          <el-input-number
            v-model="form.lowValue"
            :precision="2"
            controls-position="right"
            placeholder="可空"
            style="width: 180px"
          />
          <span class="range-tip">结果 &lt; 下限判定危急偏低</span>
        </el-form-item>
        <el-form-item label="危急上限" prop="highValue">
          <el-input-number
            v-model="form.highValue"
            :precision="2"
            controls-position="right"
            placeholder="可空"
            style="width: 180px"
          />
          <span class="range-tip">结果 &gt; 上限判定危急偏高</span>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon title="下限与上限至少填写一项；同项目名保存为覆盖更新。" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getThresholds, saveThreshold, type LisThreshold } from '@/api/lis'
import { fmtMoney } from '@/api/registration'

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<LisThreshold[]>([])

async function fetchList() {
  loading.value = true
  try {
    list.value = (await getThresholds()) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 新增/编辑 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  itemName: '',
  lowValue: undefined as number | undefined,
  highValue: undefined as number | undefined,
})

const rules: FormRules = {
  itemName: [{ required: true, message: '请输入项目名', trigger: 'blur' }],
}

function openDialog(row?: LisThreshold) {
  editingId.value = row?.id ?? null
  form.itemName = row?.itemName ?? ''
  form.lowValue = row?.lowValue ?? undefined
  form.highValue = row?.highValue ?? undefined
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.lowValue === undefined && form.highValue === undefined) {
    ElMessage.warning('危急下限与危急上限至少填写一项')
    return
  }
  submitting.value = true
  try {
    await saveThreshold({
      itemName: form.itemName.trim(),
      lowValue: form.lowValue ?? null,
      highValue: form.highValue ?? null,
    })
    ElMessage.success(editingId.value ? '阈值已更新' : '阈值已新增')
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.tip {
  margin-bottom: 12px;
}

.not-judged {
  color: #c0c4cc;
  font-size: 12px;
}

.range-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
