import { get, post, put, type PageResult } from './request'

/**
 * CDSS 模块（三期第三批，《19》§5.4）。
 * 权限：cdss:rule:manage（管理员）/ cdss:hit:query（医生限本人命中，管理员全量）。
 * 规则全部为提示级（不阻断开单）；命中由 doc 开单 hook 自动留痕。
 */
export interface CdssRule {
  id: number
  ruleCode: string
  ruleType: number
  refAId: number
  refBId?: number | null
  level: number
  message: string
  status: number
}

export interface CdssHit {
  id: number
  orderId: number
  doctorId: number
  ruleId: number
  message: string
  ignored: number
  hitTime: string
}

export const CDSS_TYPE_OPTIONS = [
  { value: 1, label: '配伍禁忌' },
  { value: 2, label: '重复检查' },
  { value: 3, label: '剂量上限' },
  { value: 4, label: '过敏映射' },
]

export function cdssTypeLabel(t: number): string {
  return CDSS_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? String(t)
}

export function getRulePage(params: { pageNum?: number; pageSize?: number; ruleType?: number }) {
  return get<PageResult<CdssRule>>('/cdss/rules', params)
}

export function createRule(data: { ruleCode: string; ruleType: number; refAId: number; refBId?: number; allergyKeyword?: string; level?: number; message: string }) {
  return post<string>('/cdss/rules', data)
}

export function updateRule(id: number, data: { ruleType: number; refAId: number; refBId?: number; level?: number; message: string; status?: number }) {
  return put<void>(`/cdss/rules/${id}`, data)
}

export function getHitPage(params: { pageNum?: number; pageSize?: number; orderId?: number }) {
  return get<PageResult<CdssHit>>('/cdss/hits', params)
}

/** 一百一十八轮【04】：命中处置回写（ignored=false 表示提示后放弃开立；后端校验开单医生本人） */
export const acknowledgeHit = (id: number, ignored: boolean) =>
  post<void>(`/cdss/hits/${id}/acknowledge?ignored=${ignored}`)

/** 门诊处方 CDSS 轻量守门（一百四十九轮 D-14）：开方确认前调用，提醒不阻断 */
export const outpatientCheck = (visitId: number, patientId: number, drugIds: number[]) =>
  get<string[]>('/cdss/outpatient-check', { visitId, patientId, drugIds: drugIds.join(',') })
