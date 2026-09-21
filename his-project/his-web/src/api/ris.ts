import { get, post, type PageResult } from './request'

/**
 * RIS 影像检查模块（三期第二批，《16》§5.2）。
 * 权限：ris:request:query / ris:appt:manage / ris:exam:execute / ris:report:write /
 * ris:report:review / ris:report:query / ris:device:query。
 * 状态机（§4.2）：10 待预约 → 20 已预约 → 30 检查中 → 40 已报告；50 作废。
 * 报告（§4.3）：10 书写中 → 20 已发布（双签 reviewer≠reporter）/ 30 已驳回。
 * 危急征象发布时生成危急值 source=2（复用 alert 闭环）。
 */

export interface RisRequest {
  id: number
  requestNo: string
  orderId: number
  admissionId: number
  patientId: number
  doctorId: number
  /** 1 DR 2 CT 3 MR 4 超声 5 心电 */
  modality: number
  bodyPart: string
  requirement?: string | null
  /** 1 常规 2 急查 */
  urgency: number
  status: number
}

export interface RisRequestQuery {
  pageNum?: number
  pageSize?: number
  admissionId?: number
  patientId?: number
  status?: number
  modality?: number
  urgency?: number
}

export interface RisDevice {
  id: number
  deviceNo: string
  deviceName: string
  modality: number
  status: number
}

export interface RisReport {
  id: number
  reportNo: string
  requestId: number
  finding: string
  conclusion: string
  criticalSign?: string | null
  criticalFlag: number
  reporterId: number
  reportTime: string
  reviewerId?: number | null
  reviewTime?: string | null
  /** 10 书写中 20 已发布 30 已驳回 */
  status: number
  mutualFlag?: number
  mutualNote?: string | null
}

export interface RisDetail {
  request: RisRequest
  appointment?: Record<string, unknown> | null
  image?: Record<string, unknown> | null
  report?: RisReport | null
}

export const RIS_STATUS_OPTIONS = [
  { value: 10, label: '待预约' },
  { value: 20, label: '已预约' },
  { value: 30, label: '检查中' },
  { value: 40, label: '已报告' },
  { value: 50, label: '作废' },
]

export function risStatusLabel(s: number): string {
  return RIS_STATUS_OPTIONS.find((o) => o.value === s)?.label ?? String(s)
}

export function risStatusTagType(s: number): 'info' | 'primary' | 'warning' | 'success' | 'danger' {
  if (s === 40) return 'success'
  if (s === 30) return 'warning'
  if (s === 50) return 'danger'
  return 'info'
}

export function modalityLabel(m: number): string {
  return { 1: 'DR', 2: 'CT', 3: 'MR', 4: '超声', 5: '心电' }[m] ?? String(m)
}

export function reportStatusLabel(s: number): string {
  return { 10: '书写中', 20: '已发布', 30: '已驳回' }[s] ?? String(s)
}

export function reportStatusTagType(s: number): 'info' | 'success' | 'danger' {
  if (s === 20) return 'success'
  if (s === 30) return 'danger'
  return 'info'
}

export function getRisRequestPage(params: RisRequestQuery) {
  return get<PageResult<RisRequest>>('/ris/requests', params)
}

export function getRisDetail(id: number) {
  return get<RisDetail>(`/ris/requests/${id}`)
}

export function appointExam(id: number, data: { deviceId: number; apptTime: string }) {
  return post<number>(`/ris/requests/${id}/appoint`, data)
}

export function startExam(id: number) {
  return post<void>(`/ris/requests/${id}/start`)
}

export function archiveImages(
  id: number,
  data: { fetch: boolean; seriesCount?: number; imageCount?: number; impressionText?: string },
) {
  return post<string>(`/ris/requests/${id}/images`, data)
}

export function finishExam(id: number) {
  return post<void>(`/ris/requests/${id}/finish`)
}

export function writeReport(data: {
  requestId: number
  finding: string
  conclusion: string
  criticalSign?: string
  mutualFlag?: number
  mutualNote?: string
}) {
  return post<string>('/ris/reports', data)
}

export function reviewReport(id: number, data: { approved: boolean; reason?: string }) {
  return post<void>(`/ris/reports/${id}/review`, data)
}

export function getRisReportPage(params: RisRequestQuery) {
  return get<PageResult<RisReport>>('/ris/reports', params)
}

export function getRisReportDetail(requestId: number) {
  return get<Record<string, unknown>>(`/ris/reports/${requestId}`)
}

export function getRisDevices() {
  return get<RisDevice[]>('/ris/devices')
}
