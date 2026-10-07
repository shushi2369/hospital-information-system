import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 病案管理模块（阶段四 /mrc）。权限：mrc:*（病案员 + 管理员）。
 * 错误码：B6301 未结算不可归档 / B6302 未编码质控或质控未通过不可归档。
 * 列表接口由后端自动惰性补建「待归档」病案。
 */

/** 病案行，archiveStatus: 10 待归档 20 已归档 30 借阅中；qcStatus: 0 未质控 1 通过 2 退回 */
export interface MrcRecord {
  id: number
  mrcNo: string
  admissionId: number
  patientId?: number | null
  patientName?: string | null
  archiveStatus: number
  qcStatus: number
  archiveTime?: string | null
}

export interface MrcQuery {
  pageNum?: number
  pageSize?: number
  archiveStatus?: number
  qcStatus?: number
  mrcNo?: string
}

/** 病案首页编码载荷（otherDiagnoses 为 {code,name} 数组，见后端 HomepageCodeRequest） */
export interface HomepageCodePayload {
  mainDiagnosisCode: string
  mainDiagnosisName: string
  otherDiagnoses?: Array<{ code?: string; name?: string }>
  operationCode?: string
}

/** 费用汇总（后端为 JSON 字符串，byType 为 {费用类别: 金额} 对象） */
export interface HomepageChargeSummary {
  total?: string | number
  byType?: Record<string, string | number>
}

/** 病案首页 */
export interface MrcHomepage {
  mainDiagnosisCode?: string | null
  mainDiagnosisName?: string | null
  otherDiagnoses?: string | Array<{ code?: string; name?: string }> | null
  operationCode?: string | null
  /** JSON 字符串（{"byType":{"3":120.00},"total":320.00}）或已解析对象 */
  chargeSummary?: string | HomepageChargeSummary | null
  [key: string]: unknown
}

/** ICD-10 诊断条目 */
export interface Icd10Item {
  code: string
  name: string
  category?: string | null
}

// ---------------- 接口 ----------------

export const getMrcPage = (params: MrcQuery) => get<PageResult<MrcRecord>>('/mrc/records', params)

/** 保存病案首页编码 */
export const saveHomepageCode = (admissionId: number, data: HomepageCodePayload) =>
  post<void>(`/mrc/homepage/${admissionId}/code`, data)

export const getHomepage = (admissionId: number) => get<MrcHomepage>(`/mrc/homepage/${admissionId}`)

/** 病案首页质控（B6302 未编码时报错） */
export const qcHomepage = (admissionId: number, pass: boolean) =>
  post<void>(`/mrc/homepage/${admissionId}/qc`, { pass })

/** 病案归档（B6301 未结算 / B6302 质控未通过） */
export const archiveMrc = (admissionId: number) =>
  post<void>(`/mrc/admissions/${admissionId}/archive`)

/** 病案借阅 */
export const borrowMrc = (admissionId: number, expectReturnDays?: number, borrowerId?: number) =>
  post<void>(`/mrc/admissions/${admissionId}/borrow`, { expectReturnDays, borrowerId })

/** 病案归还 */
export const returnMrc = (admissionId: number) =>
  post<void>(`/mrc/admissions/${admissionId}/return`)

/** 借阅台账行（一百一十轮 M4），status: 1 借阅中 2 已归还 */
export interface MrcBorrow {
  id: number
  mrcId: number
  mrcNo?: string | null
  admissionId?: number | null
  patientId?: number | null
  patientName?: string | null
  borrowerId?: number | null
  borrowerName?: string | null
  borrowTime?: string | null
  expectReturnTime?: string | null
  returnTime?: string | null
  status: number
}

export const MRC_BORROW_STATUS_OPTIONS: DictOption[] = [
  { label: '借阅中', value: 1 },
  { label: '已归还', value: 2 },
]

export const mrcBorrowStatusLabel = (v?: number | null) => dictLabel(MRC_BORROW_STATUS_OPTIONS, v)

export function mrcBorrowStatusTagType(v?: number | null): 'primary' | 'success' {
  return v === 2 ? 'success' : 'primary'
}

/** 借阅台账分页（GET /mrc/borrows，含病案号/患者/借阅人批量回填） */
export const getMrcBorrowPage = (params: { pageNum: number; pageSize: number; status?: number }) =>
  get<PageResult<MrcBorrow>>('/mrc/borrows', params)

/** ICD-10 字典远程搜索 */
export const searchIcd10 = (keyword: string) =>
  get<Icd10Item[]>('/mrc/icd10', keyword.trim() ? { keyword: keyword.trim() } : undefined)

// ---------------- 数据字典 ----------------

export const ARCHIVE_STATUS_OPTIONS: DictOption[] = [
  { label: '待归档', value: 10 },
  { label: '已归档', value: 20 },
  { label: '借阅中', value: 30 },
]

export const MRC_QC_STATUS_OPTIONS: DictOption[] = [
  { label: '未质控', value: 0 },
  { label: '质控通过', value: 1 },
  { label: '质控退回', value: 2 },
]

export const archiveStatusLabel = (v?: number | null) => dictLabel(ARCHIVE_STATUS_OPTIONS, v)
export const mrcQcStatusLabel = (v?: number | null) => dictLabel(MRC_QC_STATUS_OPTIONS, v)

/** 归档状态 → el-tag type */
export function archiveStatusTagType(
  v?: number | null
): 'primary' | 'success' | 'warning' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
      return 'success'
    case 30:
      return 'primary'
    default:
      return 'info'
  }
}

/** 病案质控状态 → el-tag type */
export function mrcQcStatusTagType(v?: number | null): 'success' | 'danger' | 'info' {
  if (v === 1) return 'success'
  if (v === 2) return 'danger'
  return 'info'
}

/** otherDiagnoses 兼容「JSON 字符串 / 数组」两种后端形态 */
export function otherDiagnosesText(v: MrcHomepage['otherDiagnoses']): string {
  if (!v) return '-'
  let value: unknown = v
  if (typeof v === 'string') {
    try {
      value = JSON.parse(v)
    } catch {
      return v || '-'
    }
  }
  if (Array.isArray(value)) {
    return (
      value
        .map((d) =>
          typeof d === 'string' ? d : [d?.code, d?.name].filter(Boolean).join(' ')
        )
        .filter(Boolean)
        .join('；') || '-'
    )
  }
  return String(value)
}

/** 解析 chargeSummary（JSON 字符串或对象），产出可渲染的按类别金额列表 */
export function parseChargeSummary(
  v: MrcHomepage['chargeSummary']
): { total: string | number; rows: Array<{ feeType: number; amount: string | number }> } {
  let data: HomepageChargeSummary | null = null
  if (!v) data = null
  else if (typeof v === 'string') {
    try {
      data = JSON.parse(v) as HomepageChargeSummary
    } catch {
      data = null
    }
  } else {
    data = v
  }
  const rows: Array<{ feeType: number; amount: string | number }> = []
  const byType = data?.byType
  if (byType && typeof byType === 'object') {
    Object.keys(byType).forEach((k) => {
      const feeType = Number(k)
      if (Number.isFinite(feeType)) {
        rows.push({ feeType, amount: byType[k] })
      }
    })
  }
  return { total: data?.total ?? 0, rows }
}
