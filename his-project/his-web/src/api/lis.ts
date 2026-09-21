import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 检验（/lis）+ 危急值（/alerts）模块（三期）。
 * 权限：lab:request:query / lab:specimen:collect / lab:result:entry / lab:report:publish /
 * lab:report:query / lab:threshold:query / lab:threshold:manage；alert:query / alert:notify / alert:confirm。
 * 状态机（LIS §4）：10 待采集 → 20 已采集 → 30 检验中 → 40 已报告；50 作废。
 * 危急值闭环（W §4.2）：10 待处理 → 20 已通知 → 30 已确认 → 40 已闭环。
 */

/** 检验申请单（GET /lis/requests 行） */
export interface LisRequest {
  id: number
  requestNo: string
  orderId: number
  admissionId: number
  patientId: number
  doctorId: number
  /** 标本类型，如 血液 / 尿液 */
  specimenType?: string | null
  /** 10 待采集 20 已采集 30 检验中 40 已报告 50 作废 */
  status: number
}

export interface LisRequestQuery {
  pageNum?: number
  pageSize?: number
  admissionId?: number
  patientId?: number
  status?: number
}

/** 检验报告行（GET /lis/reports 行） */
export interface LisReport {
  id: number
  reportNo: string
  requestId: number
  resultSummary?: string | null
  reporterId?: number | null
  reportTime?: string | null
  /** 20 已发布 */
  status?: number
}

/** 检验结果行 */
export interface LisResultRow {
  id?: number
  requestId?: number
  itemName: string
  resultValue: string
  unit?: string | null
  referenceRange?: string | null
  /** 0 正常 1 偏高 2 偏低 */
  abnormalFlag: number
  /** 1 危急 0 非危急 */
  criticalFlag: number
  instrument?: string | null
}

/** 报告详情（GET /lis/reports/{requestId}） */
export interface LisReportDetail {
  request: LisRequest
  report: LisReport | null
  results: LisResultRow[]
}

/** 结果录入载荷：fetch=true 时由 Mock 仪器取数，rows 手工行可选 */
export interface ResultEntryPayload {
  requestId: number
  fetch: boolean
  rows?: Array<{
    itemName: string
    resultValue: string
    unit?: string
    referenceRange?: string
    abnormalFlag?: number
  }>
}

/** 危急值阈值（GET /lis/thresholds 行），low/high 可空 = 不判该方向 */
export interface LisThreshold {
  id: number
  itemName: string
  lowValue?: number | null
  highValue?: number | null
}

export interface ThresholdPayload {
  itemName: string
  lowValue?: number | null
  highValue?: number | null
}

/** 危急值记录（GET /alerts 行） */
export interface AlertItem {
  id: number
  alertNo: string
  /** 来源：1 检验 */
  source: number
  requestId: number
  resultId: number
  patientId: number
  admissionId?: number | null
  itemName: string
  criticalValue: string
  notifiedNurse?: number | null
  notifiedAt?: string | null
  confirmedDoctor?: number | null
  confirmedAt?: string | null
  handleNote?: string | null
  /** 10 待处理 20 已通知 30 已确认 40 已闭环 */
  status: number
}

export interface AlertQuery {
  status?: number
  pageNum?: number
  pageSize?: number
}

// ---------------- 接口 ----------------

/** 检验申请单分页 */
export const getRequestPage = (params: LisRequestQuery) =>
  get<PageResult<LisRequest>>('/lis/requests', params)

/** 申请单详情 */
export const getRequestDetail = (id: number) => get<LisRequest>(`/lis/requests/${id}`)

/** 标本采集（10 → 20），返回 { requestNo, specimenNo } */
export const collectSpecimen = (requestId: number) =>
  post<{ requestNo: string; specimenNo: string }>(`/lis/specimens/collect?requestId=${requestId}`)

/** 标本接收（20 → 30） */
export const receiveSpecimen = (requestId: number) =>
  post<void>(`/lis/specimens/${requestId}/receive`)

/** 结果录入（检验中），data 为危急项数；fetch=true 走 Mock 仪器取数 */
export const entryResults = (data: ResultEntryPayload) => post<number>('/lis/results/entry', data)

/** 发布报告（检验中 → 已报告），data 为报告号 */
export const publishReport = (requestId: number) =>
  post<string>(`/lis/reports/${requestId}/publish`)

/** 报告分页 */
export const getReportPage = (params: { pageNum?: number; pageSize?: number }) =>
  get<PageResult<LisReport>>('/lis/reports', params)

/** 按申请单查报告详情 */
export const getReportDetail = (requestId: number) =>
  get<LisReportDetail>(`/lis/reports/${requestId}`)

/** 危急值阈值列表 */
export const getThresholds = () => get<LisThreshold[]>('/lis/thresholds')

/** 新增/编辑阈值（同条目名 upsert），data 为阈值 id */
export const saveThreshold = (data: ThresholdPayload) => post<number>('/lis/thresholds', data)

// ---------------- 危急值 ----------------

/** 危急值分页 */
export const getAlertPage = (params: AlertQuery = {}) =>
  get<PageResult<AlertItem>>('/alerts', params)

/** 技师登记通知（10 → 20） */
export const notifyAlert = (id: number) => post<void>(`/alerts/${id}/notify`)

/** 医生确认（20 → 30） */
export const confirmAlert = (id: number) => post<void>(`/alerts/${id}/confirm`)

/** 处置闭环（30 → 40） */
export const closeAlert = (id: number, handleNote?: string) =>
  post<void>(`/alerts/${id}/close`, { handleNote: handleNote || undefined })

// ---------------- 数据字典 ----------------

/** 检验申请单状态 */
export const LIS_STATUS_OPTIONS: DictOption[] = [
  { label: '待采集', value: 10 },
  { label: '已采集', value: 20 },
  { label: '检验中', value: 30 },
  { label: '已报告', value: 40 },
  { label: '作废', value: 50 },
]

export const lisStatusLabel = (v?: number | null) => dictLabel(LIS_STATUS_OPTIONS, v)

/** 检验状态 → el-tag type */
export function lisStatusTagType(v?: number | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
    case 30:
      return 'primary'
    case 40:
      return 'success'
    case 50:
      return 'danger'
    default:
      return 'info'
  }
}

/** 异常标志：0 正常 1 偏高 2 偏低（Mock 仪器约定） */
export const ABNORMAL_OPTIONS: DictOption[] = [
  { label: '正常', value: 0 },
  { label: '偏高', value: 1 },
  { label: '偏低', value: 2 },
]

export const abnormalLabel = (v?: number | null) => dictLabel(ABNORMAL_OPTIONS, v)

/** 异常标志 → el-tag type */
export function abnormalTagType(v?: number | null): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 1) return 'danger'
  if (v === 2) return 'warning'
  if (v === 0) return 'success'
  return 'info'
}

/** 危急标志：1 危急 */
export const criticalLabel = (v?: number | null) => (v === 1 ? '危急' : '—')

/** 危急标志 → el-tag type（危急红标） */
export function criticalTagType(v?: number | null): 'danger' | 'info' {
  return v === 1 ? 'danger' : 'info'
}

/** 危急值状态 */
export const ALERT_STATUS_OPTIONS: DictOption[] = [
  { label: '待处理', value: 10 },
  { label: '已通知', value: 20 },
  { label: '已确认', value: 30 },
  { label: '已闭环', value: 40 },
]

export const alertStatusLabel = (v?: number | null) => dictLabel(ALERT_STATUS_OPTIONS, v)

/** 危急值状态 → el-tag type */
export function alertStatusTagType(v?: number | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'danger'
    case 20:
      return 'warning'
    case 30:
      return 'primary'
    case 40:
      return 'success'
    default:
      return 'info'
  }
}

/** 危急值来源：1 检验 */
export const ALERT_SOURCE_OPTIONS: DictOption[] = [{ label: '检验', value: 1 }]

export const alertSourceLabel = (v?: number | null) => dictLabel(ALERT_SOURCE_OPTIONS, v)

// 金额格式化复用（阈值等数值展示口径与全局一致）
export { fmtMoney } from './registration'
