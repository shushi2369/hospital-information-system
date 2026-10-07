import { get, post, type PageResult } from './request'

/**
 * 不良事件模块（四期，《22》§9）。
 * 状态机：10 已上报（待派单）→ 20 已派单（待整改）→ 30 已整改（待关闭）→ 40 已关闭。
 * 权限：ae:report（上报+整改）/ ae:query / ae:qc:assign（派单）/ ae:close（关闭）。
 */
export interface AeEvent {
  id: number
  eventNo: string
  eventType: number
  severity: number
  departmentId: number
  eventTime: string
  description: string
  reporterId: number
  qcId?: number | null
  handlerNote?: string | null
  handlerId?: number | null
  closedTime?: string | null
  status: number
}

// 一百零九轮：对齐 V34 字典枚举（原前端标签 2~4 与后端字典错位——
// 跌倒入库为 2 却显示"用药错误"，铁证见 V35 演示数据 AE2026001）
export const AE_EVENT_TYPE_OPTIONS = [
  { value: 1, label: '药品' },
  { value: 2, label: '跌倒/坠床' },
  { value: 3, label: '器械' },
  { value: 4, label: '输血' },
  { value: 5, label: '手术相关' },
  { value: 6, label: '院感相关' },
  { value: 7, label: '其他' },
]

export const AE_SEVERITY_OPTIONS = [
  { value: 1, label: '轻微' },
  { value: 2, label: '一般' },
  { value: 3, label: '严重' },
  { value: 4, label: '危急' },
]

export const AE_STATUS_OPTIONS = [
  { value: 10, label: '待派单' },
  { value: 20, label: '待整改' },
  { value: 30, label: '待关闭' },
  { value: 40, label: '已关闭' },
]

export function aeEventTypeLabel(type: number): string {
  return AE_EVENT_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? String(type)
}

export function aeSeverityLabel(severity: number): string {
  return AE_SEVERITY_OPTIONS.find((o) => o.value === severity)?.label ?? String(severity)
}

export function aeSeverityTagType(severity: number): 'info' | 'warning' | 'danger' {
  if (severity <= 1) return 'info'
  if (severity === 2) return 'warning'
  return 'danger'
}

export function aeStatusLabel(status: number): string {
  return AE_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? String(status)
}

export function aeStatusTagType(status: number): 'warning' | 'primary' | 'info' | 'success' {
  if (status === 10) return 'warning'
  if (status === 20) return 'primary'
  if (status === 30) return 'info'
  return 'success'
}

export const getAePage = (params: { pageNum: number; pageSize: number; status?: number }) =>
  get<PageResult<AeEvent>>('/ae', params)

/** 上报不良事件（→ 10），返回事件号 */
export const reportAeEvent = (data: {
  eventType: number
  severity: number
  departmentId: number
  eventTime: string
  description: string
}) => post<string>('/ae', data)

/** 质控派单（10 → 20），权限 ae:qc:assign */
export const assignAeEvent = (id: number) => post<void>(`/ae/${id}/assign`)

/** 整改登记（20 → 30），权限 ae:report */
export const rectifyAeEvent = (id: number, handlerNote: string) =>
  post<void>(`/ae/${id}/rectify?handlerNote=${encodeURIComponent(handlerNote)}`)

/** 关闭（30 → 40），权限 ae:close */
export const closeAeEvent = (id: number) => post<void>(`/ae/${id}/close`)
