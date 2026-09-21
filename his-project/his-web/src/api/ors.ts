import { get, post, type PageResult } from './request'

/**
 * 手术麻醉 ORIS 模块（三期第二批，《16》§5.1）。
 * 权限：or:request:query / or:request:create / or:request:review / or:schedule:manage /
 * or:check:submit / or:stage:operate / or:anesthesia:write / or:request:complete / or:room:query。
 * 状态机（§4.1）：10 待审核 → 20 已审核 → 30 已排台 → 40 术中 → 50 复苏中 → 60 已完成；70 已取消。
 * 门禁：麻醉前(1)+切皮前(2)核查齐备才能开始手术；离室前(3)核查齐备才能离室。
 */

export interface OrsRequest {
  id: number
  requestNo: string
  admissionId: number
  patientId: number
  applicantDoctorId: number
  surgeryName: string
  surgeryCode?: string | null
  diagnosis: string
  plannedDate: string
  /** 1 全麻 2 椎管内 3 神经阻滞 4 局麻+镇静 5 基础麻醉 */
  anesthesiaMethod: number
  surgeryItemId?: number | null
  anesthesiaItemId?: number | null
  incisionTime?: string | null
  endTime?: string | null
  /** 10/20/30/40/50/60/70 */
  status: number
  /** 0 未记账 1 已记账 */
  chargeStatus?: number
}

export interface OrsRequestQuery {
  pageNum?: number
  pageSize?: number
  admissionId?: number
  patientId?: number
  status?: number
  plannedDate?: string
}

export interface OrsRoom {
  id: number
  roomNo: string
  roomName: string
  status: number
}

export interface OrsCheckRecord {
  id: number
  requestId: number
  /** 1 麻醉前 2 切皮前 3 离室前 */
  checkType: number
  checkItems: string
  checker1Id: number
  checker2Id: number
  checkedAt: string
}

export interface OrsSchedule {
  id: number
  scheduleNo: string
  requestId: number
  roomId: number
  surgeryDate: string
  seqNo: number
  surgeonId: number
  anesthetistId?: number | null
  circulatingNurseId?: number | null
  scrubNurseId?: number | null
  status: number
}

export interface OrsDetail {
  request: OrsRequest
  schedule?: OrsSchedule | null
  checks: OrsCheckRecord[]
  anesthesia?: Record<string, unknown> | null
  postop?: Record<string, unknown> | null
}

export interface CheckItem {
  item: string
  result: boolean
}

/** 手术申请状态标签 */
export const ORS_STATUS_OPTIONS = [
  { value: 10, label: '待审核' },
  { value: 20, label: '已审核' },
  { value: 30, label: '已排台' },
  { value: 40, label: '术中' },
  { value: 50, label: '复苏中' },
  { value: 60, label: '已完成' },
  { value: 70, label: '已取消' },
]

export function orsStatusLabel(s: number): string {
  return ORS_STATUS_OPTIONS.find((o) => o.value === s)?.label ?? String(s)
}

export function orsStatusTagType(s: number): 'info' | 'primary' | 'warning' | 'success' | 'danger' {
  if (s === 60) return 'success'
  if (s === 40 || s === 50) return 'warning'
  if (s === 70) return 'danger'
  if (s === 30) return 'primary'
  return 'info'
}

export function anesthesiaMethodLabel(m: number): string {
  return (
    { 1: '全麻', 2: '椎管内', 3: '神经阻滞', 4: '局麻+镇静', 5: '基础麻醉' }[m] ?? String(m)
  )
}

export function checkTypeLabel(t: number): string {
  return { 1: '麻醉前', 2: '切皮前', 3: '离室前' }[t] ?? String(t)
}

/** 默认核查清单（《15》§2.1 三方核查，双人签名） */
export function defaultChecklist(type: number): CheckItem[] {
  const items: Record<number, string[]> = {
    1: [
      '患者身份核对（姓名/腕带/住院号）',
      '手术部位标记确认',
      '术前诊断与手术方式确认',
      '知情同意书已签署',
      '过敏史与用药史核对',
      '禁食禁饮时间确认',
      '麻醉设备与药品准备',
      '血制品备血情况确认',
      '影像资料备齐',
      '体温维持设备准备',
    ],
    2: [
      '患者身份再次核对',
      '手术方式再次确认',
      '手术部位再次确认',
      '无菌物品与器械齐备',
      '仪器设备功能完好',
      '预防性抗生素已给',
      '皮肤消毒完成',
      '体位摆放正确',
      '术中用药准备',
      '团队互相介绍与确认',
    ],
    3: [
      '手术方式与实际一致确认',
      '清点器械敷料数目正确',
      '标本核对与标识',
      '引流管路标识清晰',
      '皮肤完整性检查',
      '苏醒评分评估',
      '麻醉复苏情况确认',
      '术后医嘱已开立',
      '去向确认（病房/ICU）',
      '交接内容确认',
    ],
  }
  return (items[type] ?? []).map((item) => ({ item, result: true }))
}

export function getRequestPage(params: OrsRequestQuery) {
  return get<PageResult<OrsRequest>>('/ors/requests', params)
}

export function getOrsDetail(id: number) {
  return get<OrsDetail>(`/ors/requests/${id}`)
}

export function createSurgery(data: {
  admissionId: number
  patientId: number
  surgeryName: string
  surgeryCode?: string
  diagnosis: string
  plannedDate: string
  anesthesiaMethod: number
  surgeryItemId?: number
  anesthesiaItemId?: number
}) {
  return post<string>('/ors/requests', data)
}

export function reviewSurgery(id: number, data: { approved: boolean; reason?: string }) {
  return post<void>(`/ors/requests/${id}/review`, data)
}

export function scheduleSurgery(
  id: number,
  data: {
    roomId: number
    surgeryDate: string
    seqNo: number
    surgeonId: number
    anesthetistId?: number
    circulatingNurseId?: number
    scrubNurseId?: number
  },
) {
  return post<string>(`/ors/requests/${id}/schedule`, data)
}

export function submitCheck(
  id: number,
  data: { checkType: number; items: CheckItem[]; checker2Id: number },
) {
  return post<number>(`/ors/requests/${id}/checks`, data)
}

export function startSurgery(id: number) {
  return post<void>(`/ors/requests/${id}/start`)
}

export function saveAnesthesia(
  id: number,
  data: {
    asaGrade: number
    anesthesiaMethod?: number
    startTime?: string
    endTime?: string
    drugNote?: string
    eventNote?: string
  },
) {
  return post<string>(`/ors/requests/${id}/anesthesia`, data)
}

export function finishSurgery(id: number) {
  return post<void>(`/ors/requests/${id}/finish`)
}

export function leaveRoom(
  id: number,
  data: { recoveryScore: number; destination: number; followupNote?: string },
) {
  return post<void>(`/ors/requests/${id}/leave`, data)
}

export function completeSurgery(id: number) {
  return post<Record<string, unknown>>(`/ors/requests/${id}/complete`)
}

export function cancelSurgery(id: number, reason?: string) {
  return post<void>(`/ors/requests/${id}/cancel?reason=${encodeURIComponent(reason ?? '')}`)
}

export function getRooms() {
  return get<OrsRoom[]>('/ors/rooms')
}
