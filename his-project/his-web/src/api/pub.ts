import { get, post, type PageResult } from './request'

/**
 * 公卫上报模块（四期，《22》§7）。
 * 传染病卡状态机：10 待审核 → 20 待上报（审核通过）→ 30 已上报 → 40 已反馈（回执登记）。
 * 院感病例：报告（POST /hai）→ 确认（后端按 targetStatus 推进，无列表查询接口，确认入口暂缺）。
 * 权限：pub:card:query / pub:card:report（审核+上报）/ pub:card:receipt / pub:hai:confirm。
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
  { value: 10, label: '待审核' },
  { value: 20, label: '待上报' },
  { value: 30, label: '已上报' },
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

/** 审核（10 → 20），权限 pub:card:report */
export const approvePubCard = (id: number) => post<void>(`/pub/cards/${id}/approve`)

/** 上报（20 → 30），权限 pub:card:report */
export const reportPubCard = (id: number) => post<void>(`/pub/cards/${id}/report`)

/** 反馈回执登记（30 → 40），权限 pub:card:receipt */
export const receiptPubCard = (id: number, receiptNo: string) =>
  post<void>(`/pub/cards/${id}/receipt`, { receiptNo })

export interface PubHaiPayload {
  admissionId: number
  patientId: number
  infectionType: number
  infectionSite: string
  diagnoseDate: string
}

/** 院感病例报告，返回病例号 */
export const reportHaiCase = (data: PubHaiPayload) => post<string>('/pub/hai', data)
