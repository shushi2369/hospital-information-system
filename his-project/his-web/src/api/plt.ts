import { get, type PageResult } from './request'

/**
 * 患者主索引（EMPI）模块（阶段四 /plt）。权限：plt:*（管理员）。
 */

/** 主索引行，mergeFlag: 0 正常 1 已合并 */
export interface MpiIndex {
  mpiNo: string
  patientId?: number | null
  patientName?: string | null
  patientNo?: string | null
  mergeFlag: number
  mergedInto?: string | null
}

export interface MpiIndexQuery {
  pageNum?: number
  pageSize?: number
  name?: string
  mpiNo?: string
  patientNo?: string
}

/** 主索引详情 */
export interface MpiDetail {
  mpiNo: string
  mergeFlag: number
  mergedInto?: string | null
  patient?: {
    id?: number
    patientNo?: string | null
    name?: string | null
    gender?: number | null
    birthDate?: string | null
    phone?: string | null
    idCardNo?: string | null
    [key: string]: unknown
  } | null
}

/** 主索引事件 */
export interface PltEvent {
  id: number
  eventNo: string
  eventType?: string | null
  bizNo?: string | null
  /** 事件负载，可能为 JSON 字符串或对象 */
  payload?: unknown
  createdAt?: string | null
}

export interface PltEventQuery {
  eventType?: string
  bizNo?: string
  startDate?: string
  endDate?: string
}

// ---------------- 接口 ----------------

export const searchMpiIndex = (params: MpiIndexQuery) =>
  get<PageResult<MpiIndex>>('/plt/index/search', params)

export const getMpiDetail = (mpiNo: string) => get<MpiDetail>(`/plt/index/${mpiNo}`)

export const getPltEvents = (params: PltEventQuery = {}) => get<PltEvent[]>('/plt/events', params)

// ---------------- 展示辅助 ----------------

export function mergeFlagLabel(v?: number | null): string {
  return v === 1 ? '已合并' : '正常'
}

/** payload 归一化为可展示的 JSON 文本 */
export function payloadText(payload: unknown): string {
  if (payload === null || payload === undefined) return '-'
  if (typeof payload === 'string') {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2)
    } catch {
      return payload
    }
  }
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
}
