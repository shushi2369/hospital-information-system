<template>
  <div class="page-card">
    <!-- 顶部：在院患者选择 -->
    <el-form class="search-bar" inline>
      <el-form-item label="在院患者">
        <el-select
          v-model="admissionId"
          filterable
          remote
          :remote-method="searchAdmissions"
          :loading="admSearching"
          placeholder="输入住院号搜索在院患者"
          style="width: 330px"
          @change="admissionId ? ((query.pageNum = 1), fetchRecords()) : null"
          @visible-change="(v: boolean) => v && !admissionId && searchAdmissions('')"
        >
          <el-option
            v-for="a in admissionOptions"
            :key="a.id"
            :label="`${a.admissionNo}｜${a.patientName}｜${a.wardName || '-'} ${a.bedNo || ''}`"
            :value="a.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          v-perm="'emr:record:create'"
          type="primary"
          :icon="Plus"
          :disabled="!admissionId"
          @click="openCreateDialog"
        >
          新建文书
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 文书列表 -->
    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="recordNo" label="文书编号" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="docTypeTagType(row.docType)">
            {{ docTypeLabel(row.docType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="recordTime" label="书写时间" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.recordTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="95" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="emrStatusTagType(row.status)">
            {{ emrStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质控问题" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ qcIssuesText(row.qcIssues) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 10 || row.status === 40"
            v-perm="'emr:record:update'"
            link
            type="primary"
            @click="openEditor(row, false)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 10"
            v-perm="'emr:record:submit'"
            link
            type="success"
            @click="handleSubmit(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="row.status === 20"
            v-perm="'emr:qc:do'"
            link
            type="warning"
            @click="openQcDialog(row)"
          >
            质控
          </el-button>
          <el-button link type="primary" @click="openEditor(row, true)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="handleSizeChange"
        @current-change="fetchRecords"
      />
    </div>

    <!-- 新建文书弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建文书" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="文书类型" prop="docType">
          <el-select v-model="createForm.docType" placeholder="选择文书类型" style="width: 100%" @change="fetchTemplates">
            <el-option v-for="o in DOC_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="请输入文书标题" maxlength="128" />
        </el-form-item>
        <el-form-item label="套用模板">
          <el-select
            v-model="createForm.templateId"
            placeholder="可选模板，套用后可再编辑"
            clearable
            style="width: 100%"
            :loading="templateLoading"
          >
            <el-option
              v-for="t in templates"
              :key="t.id"
              :label="t.templateName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="入院记录必填节：主诉/现病史/查体/初步诊断；出院记录必填节：诊疗经过/出院诊断。"
      />
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建并编辑</el-button>
      </template>
    </el-dialog>

    <!-- 编辑页弹窗 -->
    <el-dialog
      v-model="editorVisible"
      :title="editorReadonly ? '查看文书' : '编辑文书'"
      width="860px"
      top="4vh"
      destroy-on-close
      append-to-body
    >
      <template v-if="editing">
        <el-descriptions :column="3" border size="small" class="editor-desc">
          <el-descriptions-item label="文书编号">{{ editing.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ docTypeLabel(editing.docType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="emrStatusTagType(editing.status)">
              {{ emrStatusLabel(editing.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="90px" class="editor-form">
          <el-form-item label="标题">
            <el-input v-model="editTitle" maxlength="128" :disabled="editorReadonly" />
          </el-form-item>
        </el-form>

        <div class="section-toolbar">
          <span class="toolbar-title">文书内容（按节编辑）</span>
          <div v-if="!editorReadonly" class="section-add">
            <el-input
              v-model="newSectionKey"
              placeholder="新节名称"
              size="small"
              style="width: 140px"
              maxlength="32"
            />
            <el-button type="primary" size="small" :icon="Plus" @click="addSection">添加节</el-button>
          </div>
        </div>

        <div class="section-list">
          <div v-for="(s, idx) in sections" :key="`${s.key}-${idx}`" class="section-item">
            <div class="section-head">
              <span class="section-key">
                {{ s.key }}
                <el-tag
                  v-if="isRequiredSection(s.key)"
                  size="small"
                  type="danger"
                  class="required-tag"
                >
                  必填
                </el-tag>
                <el-tag v-if="s.isJson" size="small" type="info" class="required-tag">JSON</el-tag>
              </span>
              <el-button
                v-if="!editorReadonly"
                link
                type="danger"
                size="small"
                @click="removeSection(idx)"
              >
                删除
              </el-button>
            </div>
            <el-input
              v-model="s.value"
              type="textarea"
              :rows="s.isJson ? 5 : 3"
              :autosize="{ minRows: 2, maxRows: 10 }"
              :disabled="editorReadonly"
              :placeholder="`请输入${s.key}内容`"
            />
          </div>
          <el-empty
            v-if="sections.length === 0"
            description="暂无内容节，请添加内容节"
            :image-size="70"
          />
        </div>
      </template>
      <template #footer>
        <el-button @click="editorVisible = false">关闭</el-button>
        <el-button
          v-if="!editorReadonly"
          type="warning"
          :loading="saving"
          @click="handleSave"
        >
          暂存
        </el-button>
        <el-button
          v-if="!editorReadonly"
          type="primary"
          :loading="submitting"
          @click="handleSubmitFromEditor"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 质控弹窗 -->
    <el-dialog v-model="qcDialogVisible" title="文书质控" width="520px" destroy-on-close>
      <div v-if="qcRow" class="qc-line">文书编号：{{ qcRow.recordNo }}｜{{ qcRow.title }}</div>
      <el-form label-width="80px">
        <el-form-item label="质控结论">
          <el-radio-group v-model="qcPass">
            <el-radio :value="true">质控通过</el-radio>
            <el-radio :value="false">质控退回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!qcPass" label="问题清单" required>
          <el-input
            v-model="qcIssuesInput"
            type="textarea"
            :rows="4"
            placeholder="每行一条质控问题，退回时必填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="qcSubmitting" @click="handleQcSubmit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createEmrRecord,
  docTypeLabel,
  docTypeTagType,
  DOC_TYPE_DEFAULT_SECTIONS,
  DOC_TYPE_OPTIONS,
  DOC_TYPE_REQUIRED_SECTIONS,
  emrStatusLabel,
  emrStatusTagType,
  getEmrRecordDetail,
  getEmrRecordPage,
  getEmrTemplates,
  qcEmrRecord,
  qcIssuesText,
  submitEmrRecord,
  updateEmrRecord,
  type EmrRecord,
  type EmrTemplate,
} from '@/api/emr'
import { getAdmissionPage, type Admission } from '@/api/inp'

// ---------------- 在院患者远程选择 ----------------
const admissionId = ref<number | undefined>(undefined)
const admissionOptions = ref<Admission[]>([])
const admSearching = ref(false)

async function searchAdmissions(kw: string) {
  admSearching.value = true
  try {
    const res = await getAdmissionPage({
      pageNum: 1,
      pageSize: 50,
      status: 10,
      admissionNo: kw.trim() || undefined,
    })
    admissionOptions.value = res.list ?? []
  } catch {
    admissionOptions.value = []
  } finally {
    admSearching.value = false
  }
}

// ---------------- 文书列表 ----------------
const loading = ref(false)
const records = ref<EmrRecord[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

async function fetchRecords() {
  if (!admissionId.value) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const res = await getEmrRecordPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      admissionId: admissionId.value,
    })
    records.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  query.pageNum = 1
  fetchRecords()
}

// ---------------- 新建文书 ----------------
const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const templates = ref<EmrTemplate[]>([])
const templateLoading = ref(false)
const createForm = reactive({
  docType: undefined as number | undefined,
  title: '',
  templateId: undefined as number | undefined,
})

const createRules: FormRules = {
  docType: [{ required: true, message: '请选择文书类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入文书标题', trigger: 'blur' }],
}

async function fetchTemplates() {
  templates.value = []
  createForm.templateId = undefined
  if (!createForm.docType) return
  templateLoading.value = true
  try {
    templates.value = ((await getEmrTemplates({ docType: createForm.docType })) ?? []).filter(
      (t) => t.status === undefined || t.status === 1
    )
  } catch {
    templates.value = []
  } finally {
    templateLoading.value = false
  }
}

function openCreateDialog() {
  createForm.docType = undefined
  createForm.title = ''
  createForm.templateId = undefined
  templates.value = []
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid || !admissionId.value || createForm.docType === undefined) return
  creating.value = true
  try {
    // 内容来源：模板 → 默认节 → 空对象
    let content: Record<string, unknown> = {}
    const tpl = templates.value.find((t) => t.id === createForm.templateId)
    if (tpl?.contentJson) {
      try {
        content = JSON.parse(tpl.contentJson) as Record<string, unknown>
      } catch {
        content = {}
      }
    }
    if (Object.keys(content).length === 0) {
      const defaults = DOC_TYPE_DEFAULT_SECTIONS[createForm.docType] ?? ['内容']
      defaults.forEach((k) => (content[k] = ''))
    }
    const res = await createEmrRecord({
      admissionId: admissionId.value,
      docType: createForm.docType,
      title: createForm.title.trim(),
      content,
    })
    ElMessage.success(`文书创建成功，编号：${res.recordNo}`)
    createDialogVisible.value = false
    await fetchRecords()
    openEditor(
      {
        id: res.id,
        recordNo: res.recordNo,
        docType: createForm.docType as number,
        title: createForm.title.trim(),
        status: 10,
      } as EmrRecord,
      false
    )
  } catch {
    // 拦截器已统一提示
  } finally {
    creating.value = false
  }
}

// ---------------- 编辑页（动态节编辑） ----------------
interface Section {
  key: string
  value: string
  isJson: boolean
}

const editorVisible = ref(false)
const editorReadonly = ref(false)
const editing = ref<EmrRecord | null>(null)
const editTitle = ref('')
const sections = ref<Section[]>([])
const newSectionKey = ref('')
const saving = ref(false)
const submitting = ref(false)

const editingDocType = computed(() => editing.value?.docType)

function isRequiredSection(key: string): boolean {
  const required = editingDocType.value ? DOC_TYPE_REQUIRED_SECTIONS[editingDocType.value] : undefined
  return !!required && required.includes(key)
}

function parseSections(json: string | null | undefined): Section[] {
  if (!json) return []
  let obj: Record<string, unknown> = {}
  try {
    obj = JSON.parse(json) as Record<string, unknown>
  } catch {
    return []
  }
  return Object.entries(obj).map(([key, value]) => ({
    key,
    value: typeof value === 'string' ? value : JSON.stringify(value, null, 2),
    isJson: typeof value !== 'string',
  }))
}

function buildContent(): Record<string, unknown> {
  const obj: Record<string, unknown> = {}
  for (const s of sections.value) {
    const key = s.key.trim()
    if (!key) continue
    if (s.isJson) {
      try {
        obj[key] = JSON.parse(s.value)
      } catch {
        obj[key] = s.value
      }
    } else {
      obj[key] = s.value
    }
  }
  return obj
}

function addSection() {
  const key = newSectionKey.value.trim()
  if (!key) {
    ElMessage.warning('请输入节名称')
    return
  }
  if (sections.value.some((s) => s.key === key)) {
    ElMessage.warning('已存在同名内容节')
    return
  }
  sections.value.push({ key, value: '', isJson: false })
  newSectionKey.value = ''
}

function removeSection(idx: number) {
  sections.value.splice(idx, 1)
}

async function openEditor(row: EmrRecord, readonly: boolean) {
  if (!row?.id) return
  editorVisible.value = true
  editorReadonly.value = readonly
  editing.value = null
  sections.value = []
  try {
    const detail = await getEmrRecordDetail(row.id)
    editing.value = detail
    editTitle.value = detail.title
    sections.value = parseSections(detail.contentJson)
  } catch {
    editing.value = null
  }
}

async function handleSave() {
  if (!editing.value) return
  saving.value = true
  try {
    await updateEmrRecord(editing.value.id, {
      title: editTitle.value.trim() || undefined,
      content: buildContent(),
    })
    ElMessage.success('文书暂存成功')
    editorVisible.value = false
    fetchRecords()
  } catch {
    // B6202 已质控锁定等已在拦截器统一提示
  } finally {
    saving.value = false
  }
}

function handleSubmit(row: EmrRecord) {
  ElMessageBox.confirm(
    `确认提交文书「${row.title}」？提交后进入质控环节，必填节缺失将被退回。`,
    '提交文书',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' }
  )
    .then(async () => {
      await submitEmrRecord(row.id)
      ElMessage.success('文书已提交，等待质控')
      fetchRecords()
    })
    .catch(() => {
      // 取消或 B6201 必填节缺失（拦截器已提示缺失节名）
    })
}

async function handleSubmitFromEditor() {
  if (!editing.value) return
  submitting.value = true
  try {
    // 先暂存当前编辑内容，再提交
    await updateEmrRecord(editing.value.id, {
      title: editTitle.value.trim() || undefined,
      content: buildContent(),
    })
    await submitEmrRecord(editing.value.id)
    ElMessage.success('文书已提交，等待质控')
    editorVisible.value = false
    fetchRecords()
  } catch {
    // B6201 必填节缺失 / B6202 已质控锁定已在拦截器统一提示
  } finally {
    submitting.value = false
  }
}

// ---------------- 质控 ----------------
const qcDialogVisible = ref(false)
const qcSubmitting = ref(false)
const qcRow = ref<EmrRecord | null>(null)
const qcPass = ref(true)
const qcIssuesInput = ref('')

function openQcDialog(row: EmrRecord) {
  qcRow.value = row
  qcPass.value = true
  qcIssuesInput.value = ''
  qcDialogVisible.value = true
}

async function handleQcSubmit() {
  if (!qcRow.value) return
  const issues = qcIssuesInput.value
    .split(/[\n；;]/)
    .map((s) => s.trim())
    .filter(Boolean)
  if (!qcPass.value && issues.length === 0) {
    ElMessage.warning('质控退回时请填写问题清单（每行一条）')
    return
  }
  qcSubmitting.value = true
  try {
    await qcEmrRecord(qcRow.value.id, qcPass.value, qcPass.value ? [] : issues)
    ElMessage.success(qcPass.value ? '质控通过' : '已退回，医生可继续修改')
    qcDialogVisible.value = false
    fetchRecords()
  } catch {
    // 拦截器已统一提示
  } finally {
    qcSubmitting.value = false
  }
}

onMounted(() => searchAdmissions(''))
</script>

<style scoped>
.editor-desc {
  margin-bottom: 12px;
}

.editor-form {
  margin-bottom: 4px;
}

.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0;
}

.section-add {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-list {
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}

.section-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 10px;
  margin-bottom: 10px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.section-key {
  font-weight: 600;
  color: #303133;
}

.required-tag {
  margin-left: 6px;
}

.qc-line {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}
</style>
