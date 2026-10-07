import { get, post, type PageResult } from './request'

/**
 * 公卫上报模块（四期，《22》§7）。
 * 传染病卡状态机（对齐 PubService）：10 待上报（填卡）→ 20 已上报（待公卫科审核）→
 * 30 已审核（待回执）→ 40 已反馈（回执登记闭环）。
 * 院感病例：报告（POST /hai）→ 确认（后端按 targetStatus 推进，无列表查询接口，确认入口暂缺）。
 * 权限：pub:card:query / pub:card:report（上报登记+审核）/ pub:card:receipt / pub:hai:confirm。
 */
export interface PubCard {
  id: number
  cardNo: string
  visitId?: number | null
  admissionId?: number | null
  patientId: number
  doctorId?: number | null
  diseaseName: string
  diseaseCategory?: string | null
  diagnoseDate: string
  status: number
  publicDoctorId?: number | null
  reportTime?: string | null
  receiptNo?: string | null
  receiptTime?: string | null
}

export const PUB_CARD_STATUS_OPTIONS = [
  { value: 10, label: '待上报' },
  { value: 20, label: '待审核' },
  { value: 30, label: '待回执' },
  { value: 40, label: '已反馈' },
]

export function pubCardStatusLabel(status: number): string {
  return PUB_CARD_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? String(status)
}

export function pubCardStatusTagType(status: number): 'warning' | 'primary' | 'info' | 'success' {
  if (status === 10) return 'warning'
  if (status === 20) return 'primary'
  if (status === 30) return 'info'
  return 'success'
}

export const getPubCardPage = (params: {
  pageNum: number
  pageSize: number
  status?: number
}) => get<PageResult<PubCard>>('/pub/cards', params)

/** 上报登记（10 → 20，PUB-02），权限 pub:card:report */
export const reportPubCard = (id: number) => post<void>(`/pub/cards/${id}/report`)

/** 审核（20 → 30），权限 pub:card:report */
export const approvePubCard = (id: number) => post<void>(`/pub/cards/${id}/approve`)

/** 反馈回执登记（30 → 40），权限 pub:card:receipt */
export const receiptPubCard = (id: number, receiptNo: string) =>
  post<void>(`/pub/cards/${id}/receipt`, { receiptNo })

export interface PubHaiPayload {
  admissionId: number
  patientId: number
  infectionType: number
  infectionSite: string
}

/** 院感病例报告，返回病例号 */
export const reportHaiCase = (data: PubHaiPayload) => post<string>('/pub/hai', data)

/** 院感病例状态机（对齐 PubService.haiConfirm）：10 待确认 → 20 已确认（整改中）→ 30 整改完成 */
export const PUB_HAI_STATUS_OPTIONS = [
  { value: 10, label: '待确认' },
  { value: 20, label: '整改中' },
  { value: 30, label: '已闭环' },
]

export function pubHaiStatusLabel(status: number): string {
  return PUB_HAI_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? String(status)
}

export function pubHaiStatusTagType(status: number): 'warning' | 'primary' | 'success' {
  if (status === 10) return 'warning'
  if (status === 20) return 'primary'
  return 'success'
}

export interface PubHaiCase {
  id: number
  caseNo: string
  admissionId: number
  patientId: number
  patientName?: string | null
  infectionType: number
  infectionSite: string
  diagnoseDate: string
  reporterId?: number | null
  status: number
  confirmNote?: string | null
  confirmTime?: string | null
}

/** 院感病例分页（一百一十轮 P5 补齐列表接口） */
export const getPubHaiPage = (params: { pageNum: number; pageSize: number; status?: number }) =>
  get<PageResult<PubHaiCase>>('/pub/hai', params)

/** 院感确认/整改推进（10→20 或 20→30），权限 pub:hai:confirm */
export const confirmPubHai = (id: number, targetStatus: number, note?: string) =>
  post<void>(`/pub/hai/${id}/confirm?targetStatus=${targetStatus}${note ? `&note=${encodeURIComponent(note)}` : ''}`)
