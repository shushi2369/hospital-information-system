import { get, post, type PageResult } from './request'

/**
 * 体检模块（三期第三批，《19》§5.3）。
 * 权限：pe:package:manage / pe:record:query / pe:record:create / pe:record:manage /
 * pe:result:entry / pe:report:publish。
 * 状态机：10 已登记 → 20 检查中 → 30 已完成 → 40 报告已发。
 */
export interface PePackage {
  id: number
  packageNo: string
  name: string
  price: number
  items: string
  status: number
}

export interface PeRecord {
  id: number
  recordNo: string
  patientId: number
  packageId: number
  examDate: string
  status: number
}

export interface PeResultRow {
  id: number
  chargeItemId: number
  itemName: string
  resultValue: string
  abnormalFlag: number
  note?: string | null
}

export const PE_STATUS_OPTIONS = [
  { value: 10, label: '已登记' },
  { value: 20, label: '检查中' },
  { value: 30, label: '已完成' },
  { value: 40, label: '报告已发' },
]

export function peStatusLabel(s: number): string {
  return PE_STATUS_OPTIONS.find((o) => o.value === s)?.label ?? String(s)
}

export function peStatusTagType(s: number): 'info' | 'primary' | 'warning' | 'success' {
  if (s === 40) return 'success'
  if (s === 30) return 'primary'
  if (s === 20) return 'warning'
  return 'info'
}

export function getPackagePage(params: { pageNum?: number; pageSize?: number }) {
  return get<PageResult<PePackage>>('/pe/packages', params)
}

export function createPackage(data: { name: string; price: number; items: Array<{ chargeItemId: number; itemName: string; price: number }> }) {
  return post<string>('/pe/packages', data)
}

export function getRecordPage(params: { pageNum?: number; pageSize?: number; patientId?: number; status?: number }) {
  return get<PageResult<PeRecord>>('/pe/records', params)
}

export function getRecordDetail(id: number) {
  return get<Record<string, unknown>>(`/pe/records/${id}`)
}

export function registerPe(data: { patientId: number; packageId: number; examDate: string }) {
  return post<string>('/pe/records', data)
}

export function startPe(id: number) {
  return post<void>(`/pe/records/${id}/start`)
}

export function savePeResult(id: number, data: { chargeItemId: number; itemName: string; resultValue: string; abnormalFlag?: number; note?: string }) {
  return post<number>(`/pe/records/${id}/results`, data)
}

export function finishPe(id: number) {
  return post<void>(`/pe/records/${id}/finish`)
}

export function publishPeReport(id: number, data: { summary: string }) {
  return post<string>(`/pe/records/${id}/report`, data)
}
