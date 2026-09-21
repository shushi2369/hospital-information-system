import { get, post, type PageResult } from './request'

/**
 * EMC 急诊五大中心模块（三期第二批，《16》§5.3）。
 * 权限：emc:triage:create / emc:triage:query / emc:visit:create / emc:visit:query /
 * emc:timepoint:record / emc:visit:close / emc:stats:query。
 * 病例状态机（§4.4）：10 救治中 → 20 已关档（转归 1 收住院 2 急诊手术 3 转院 4 离院 5 死亡）。
 * 时限基准=emc_visit.start_time（登记时刻）；onTarget=节点分钟差 ≤ target_minutes。
 */

export interface EmcTriage {
  id: number
  triageNo: string
  patientId: number
  visitId?: number | null
  chiefComplaint: string
  bodyTemp?: number | null
  pulse?: number | null
  respiration?: number | null
  bloodPressure?: string | null
  spo2?: number | null
  /** 1 濒危 2 危重 3 急症 4 非急症 */
  triageLevel: number
  /** 0 无 1 胸痛 2 卒中 3 创伤 4 危重孕产妇 5 危重新生儿 */
  centerType: number
  greenChannel: number
  triageNurseId: number
  triageTime: string
  status: number
}

export interface EmcVisit {
  id: number
  visitNo: string
  triageId: number
  centerType: number
  doctorId?: number | null
  patientId: number
  admissionId?: number | null
  visitId?: number | null
  startTime: string
  outcome?: number | null
  outcomeTime?: string | null
  status: number
}

export interface EmcVisitQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  centerType?: number
  status?: number
}

export interface TimelineNode {
  nodeCode: string
  nodeName: string
  seqNo: number
  targetMinutes?: number | null
  nodeTime?: string | null
  elapsedMinutes?: number | null
  /** true 达标 / false 超时 / null 未录入 */
  onTarget?: boolean | null
}

export interface EmcVisitDetail {
  visit: EmcVisit
  triage?: EmcTriage | null
  timeline: TimelineNode[]
}

export const CENTER_TYPE_OPTIONS = [
  { value: 1, label: '胸痛中心' },
  { value: 2, label: '卒中中心' },
  { value: 3, label: '创伤中心' },
  { value: 4, label: '危重孕产妇' },
  { value: 5, label: '危重新生儿' },
]

export function centerTypeLabel(c: number): string {
  if (!c) return '无'
  return CENTER_TYPE_OPTIONS.find((o) => o.value === c)?.label ?? String(c)
}

export const TRIAGE_LEVEL_OPTIONS = [
  { value: 1, label: '1 濒危' },
  { value: 2, label: '2 危重' },
  { value: 3, label: '3 急症' },
  { value: 4, label: '4 非急症' },
]

export function triageLevelLabel(l: number): string {
  return TRIAGE_LEVEL_OPTIONS.find((o) => o.value === l)?.label ?? String(l)
}

export function triageLevelTagType(l: number): 'danger' | 'warning' | 'primary' | 'info' {
  if (l === 1) return 'danger'
  if (l === 2) return 'warning'
  if (l === 3) return 'primary'
  return 'info'
}

export const OUTCOME_OPTIONS = [
  { value: 1, label: '收住院' },
  { value: 2, label: '急诊手术' },
  { value: 3, label: '转院' },
  { value: 4, label: '离院' },
  { value: 5, label: '死亡' },
]

export function outcomeLabel(o: number): string {
  return OUTCOME_OPTIONS.find((x) => x.value === o)?.label ?? String(o)
}

export function emcVisitStatusLabel(s: number): string {
  return { 10: '救治中', 20: '已关档' }[s] ?? String(s)
}

export function emcCreateTriage(data: {
  patientId: number
  chiefComplaint: string
  bodyTemp?: number
  pulse?: number
  respiration?: number
  bloodPressure?: string
  spo2?: number
  triageLevel: number
  centerType?: number
  greenChannel?: number
}) {
  return post<string>('/emc/triage', data)
}

export function getTriagePage(params: EmcVisitQuery) {
  return get<PageResult<EmcTriage>>('/emc/triage', params)
}

export function emcCreateVisit(data: { triageId: number; centerType: number; doctorId?: number }) {
  return post<string>('/emc/visits', data)
}

export function getEmcVisitPage(params: EmcVisitQuery) {
  return get<PageResult<EmcVisit>>('/emc/visits', params)
}

export function getEmcVisitDetail(id: number) {
  return get<EmcVisitDetail>(`/emc/visits/${id}`)
}

export function addTimepoint(id: number, data: { nodeCode: string; nodeTime: string; note?: string }) {
  return post<number>(`/emc/visits/${id}/timepoints`, data)
}

export function linkVisit(
  id: number,
  data: { admissionId?: number; visitId?: number },
) {
  return post<void>(`/emc/visits/${id}/link`, data)
}

export function closeVisit(id: number, data: { outcome: number }) {
  return post<void>(`/emc/visits/${id}/close`, data)
}

export function getTimeline(id: number) {
  return get<TimelineNode[]>(`/emc/visits/${id}/timeline`)
}

/** 节点字典行（GET /emc/nodes） */
export interface EmcNodeDict {
  id: number
  nodeCode: string
  nodeName: string
  centerType: number
  seqNo: number
  targetMinutes: number | null
  status: number
}

export function getEmcNodes() {
  return get<EmcNodeDict[]>('/emc/nodes')
}

export function getEmcStats(params: { from?: string; to?: string }) {
  return get<Array<Record<string, unknown>>>('/emc/stats', params)
}
