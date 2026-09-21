import { get, post, type PageResult } from './request'

/**
 * 输血闭环模块（四期，《22》§5.1）。
 * 权限：bb:request:create/query/review、bb:bags:manage、bb:cross:match、
 * bb:issue:manage、bb:transfusion:execute、bb:adverse:report。
 * 状态机：10 待审核 → 20 配血中 → 30 配血完成 → 40 已发血 → 50 输血中 → 60 已完成；70 取消 80 驳回。
 * 三道门禁：不相容禁止发血 / 发血须取血护士签收 / 输注开始须床边双人双签。
 */
export interface BbBag {
  id: number
  bagNo: string
  bloodType: number
  rh: number
  component: number
  volumeMl: number
  bloodStation?: string | null
  expireDate: string
  status: number
}

export interface BbRequest {
  id: number
  reqNo: string
  admissionId: number
  patientId: number
  doctorId: number
  bloodType: number
  rh: number
  component: number
  volumeMl: number
  usePurpose?: string | null
  status: number
}

export const BB_BLOOD_TYPE = [
  { value: 1, label: 'A' }, { value: 2, label: 'B' },
  { value: 3, label: 'AB' }, { value: 4, label: 'O' },
]

export function bloodTypeLabel(t: number): string {
  return BB_BLOOD_TYPE.find((o) => o.value === t)?.label ?? String(t)
}

export const BB_COMPONENT = [
  { value: 1, label: '红细胞' }, { value: 2, label: '血浆' },
  { value: 3, label: '血小板' }, { value: 4, label: '冷沉淀' },
]

export function componentLabel(c: number): string {
  return BB_COMPONENT.find((o) => o.value === c)?.label ?? String(c)
}

export function rhLabel(r: number): string {
  return r === 2 ? 'Rh 阴性' : 'Rh 阳性'
}

export const BB_STATUS_OPTIONS = [
  { value: 10, label: '待审核' }, { value: 20, label: '配血中' },
  { value: 30, label: '配血完成' }, { value: 40, label: '已发血' },
  { value: 50, label: '输血中' }, { value: 60, label: '已完成' },
  { value: 70, label: '已取消' }, { value: 80, label: '已驳回' },
]

export function bbStatusLabel(s: number): string {
  return BB_STATUS_OPTIONS.find((o) => o.value === s)?.label ?? String(s)
}

export function bbStatusTagType(s: number): 'info' | 'primary' | 'warning' | 'success' | 'danger' {
  if (s === 60) return 'success'
  if (s === 50 || s === 40) return 'warning'
  if (s === 70 || s === 80) return 'danger'
  if (s === 30) return 'primary'
  return 'info'
}

export function getBagPage(params: { pageNum?: number; pageSize?: number; bloodType?: number; status?: number }) {
  return get<PageResult<BbBag>>('/bb/bags', params)
}

export function getAvailableBags(params: { bloodType?: number; component?: number }) {
  return get<BbBag[]>('/bb/bags/available', params)
}

export function createBag(data: { bagNo: string; bloodType: number; rh: number; component: number; volumeMl?: number; bloodStation?: string; collectDate?: string; expireDate: string }) {
  return post<string>('/bb/bags', data)
}

export function scrapBag(id: number) {
  return post<void>(`/bb/bags/${id}/scraps`)
}

export function getRequestPage(params: { pageNum?: number; pageSize?: number; admissionId?: number; status?: number }) {
  return get<PageResult<BbRequest>>('/bb/requests', params)
}

export function getRequestDetail(id: number) {
  return get<Record<string, unknown>>(`/bb/requests/${id}`)
}

export function createRequest(data: { admissionId: number; patientId: number; bloodType: number; rh: number; component: number; volumeMl: number; usePurpose?: string }) {
  return post<string>('/bb/requests', data)
}

export function reviewRequest(id: number, approved: boolean, note?: string) {
  return post<void>(`/bb/requests/${id}/review?approved=${approved}&note=${encodeURIComponent(note ?? '')}`)
}

export function cancelRequest(id: number) {
  return post<void>(`/bb/requests/${id}/cancel`)
}

export function crossMatch(id: number, data: { bagId: number; crossMethod: string; crossResult: number; note?: string }) {
  return post<number>(`/bb/requests/${id}/cross-match`, data)
}

export function issueBlood(id: number, data: { bagId: number; receiverId: number }) {
  return post<number>(`/bb/requests/${id}/issue`, data)
}

export function startTransfusion(id: number, data: { bagId: number; checker1Id: number; checker2Id: number; vitalBefore?: string }) {
  return post<number>(`/bb/requests/${id}/transfusion`, data)
}

export function finishTransfusion(id: number, data: { outcome: number; note?: string }) {
  return post<void>(`/bb/requests/${id}/finish`, data)
}

export function reportAdverse(id: number, data: { type: number; severity: number; handleNote: string }) {
  return post<number>(`/bb/requests/${id}/adverse`, data)
}
