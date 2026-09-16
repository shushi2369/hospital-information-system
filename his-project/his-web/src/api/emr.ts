import { get, post, put, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 电子病历（EMR）模块（阶段四 /emr）。
 * 权限：emr:record:*（医生书写）、emr:qc:do（质控）、emr:template:manage（模板维护）。
 * 错误码：B6201 必填节缺失 / B6202 已质控锁定。
 */

export interface EmrRecord {
  id: number
  recordNo: string
  admissionId: number
  /** 1 入院记录 2 病程记录 3 出院记录 4 知情同意书 9 其他 */
  docType: number
  title: string
  /** JSON 字符串，页面按节 parse/序列化 */
  contentJson?: string | null
  doctorId?: number | null
  recordTime?: string | null
  /** 10 书写中 20 已提交 30 质控通过 40 质控退回 */
  status: number
  qcIssues?: string | string[] | null
}

export interface EmrRecordPayload {
  admissionId: number
  docType: number
  title: string
  content: Record<string, unknown>
}

export interface EmrRecordQuery {
  pageNum?: number
  pageSize?: number
  admissionId?: number
  docType?: number
  status?: number
}

/** 病历模板 */
export interface EmrTemplate {
  id: number
  templateName: string
  docType: number
  deptId?: number | null
  contentJson?: string | null
  status?: number | null
}

export interface EmrTemplatePayload {
  templateName: string
  docType: number
  deptId?: number
  content: Record<string, unknown>
}

// ---------------- 接口 ----------------

/** 新建文书，返回 { recordNo, id } */
export const createEmrRecord = (data: EmrRecordPayload) =>
  post<{ recordNo: string; id: number }>('/emr/records', data)

/** 暂存修改（B6202 已质控锁定） */
export const updateEmrRecord = (
  id: number,
  data: { title?: string; content?: Record<string, unknown> }
) => put<void>(`/emr/records/${id}`, data)

/** 提交文书（B6201 必填节缺失） */
export const submitEmrRecord = (id: number) => post<void>(`/emr/records/${id}/submit`)

export const getEmrRecordPage = (params: EmrRecordQuery) =>
  get<PageResult<EmrRecord>>('/emr/records', params)

export const getEmrRecordDetail = (id: number) => get<EmrRecord>(`/emr/records/${id}`)

/** 质控：pass=false 时 issues 为问题清单 */
export const qcEmrRecord = (id: number, pass: boolean, issues: string[] = []) =>
  post<void>(`/emr/records/${id}/qc`, { pass, issues })

/** 待质控文书 */
export const getQcPending = () => get<EmrRecord[]>('/emr/qc/pending')

export const getEmrTemplates = (params: { docType?: number } = {}) =>
  get<EmrTemplate[]>('/emr/templates', params)

export const createEmrTemplate = (data: EmrTemplatePayload) => post<void>('/emr/templates', data)

export const stopEmrTemplate = (id: number) => put<void>(`/emr/templates/${id}/stop`)

// ---------------- 数据字典 ----------------

export const DOC_TYPE_OPTIONS: DictOption[] = [
  { label: '入院记录', value: 1 },
  { label: '病程记录', value: 2 },
  { label: '出院记录', value: 3 },
  { label: '知情同意书', value: 4 },
  { label: '其他', value: 9 },
]

export const EMR_STATUS_OPTIONS: DictOption[] = [
  { label: '书写中', value: 10 },
  { label: '已提交', value: 20 },
  { label: '质控通过', value: 30 },
  { label: '质控退回', value: 40 },
]

export const docTypeLabel = (v?: number | null) => dictLabel(DOC_TYPE_OPTIONS, v)
export const emrStatusLabel = (v?: number | null) => dictLabel(EMR_STATUS_OPTIONS, v)

/** 文书类型 → el-tag type */
export function docTypeTagType(v?: number | null): 'primary' | 'success' | 'warning' | 'info' {
  switch (v) {
    case 1:
      return 'primary'
    case 3:
      return 'success'
    case 4:
      return 'warning'
    default:
      return 'info'
  }
}

/** 文书状态 → el-tag type */
export function emrStatusTagType(
  v?: number | null
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
      return 'primary'
    case 30:
      return 'success'
    case 40:
      return 'danger'
    default:
      return 'info'
  }
}

/** 各文书类型的默认必填节（与 B6201 校验规则一致） */
export const DOC_TYPE_REQUIRED_SECTIONS: Record<number, string[]> = {
  1: ['主诉', '现病史', '查体', '初步诊断'],
  3: ['诊疗经过', '出院诊断'],
}

/** 新建空白文书时的默认节 */
export const DOC_TYPE_DEFAULT_SECTIONS: Record<number, string[]> = {
  1: ['主诉', '现病史', '查体', '初步诊断'],
  2: ['病程记录'],
  3: ['诊疗经过', '出院诊断'],
  4: ['知情同意'],
  9: ['内容'],
}

/** qcIssues 兼容字符串/数组 */
export function qcIssuesText(v?: string | string[] | null): string {
  if (!v) return ''
  if (Array.isArray(v)) return v.join('；')
  return String(v)
}
