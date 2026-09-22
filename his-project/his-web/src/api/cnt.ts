import { get, post, type PageResult } from './request'

/**
 * 会诊管理模块（四期，《22》§8）。
 * 状态机：10 待接受 → 20 已接受（会诊中）→ 30 已完成（填写会诊意见）。
 * 权限：cnt:request（申请）/ cnt:query / cnt:execute（接受+完成）。
 */
export interface CntRequest {
  id: number
  reqNo: string
  admissionId?: number | null
  visitId?: number | null
  patientId: number
  applicantId: number
  deptId: number
  consultDoctorId: number
  urgent: number
  reason: string
  status: number
  acceptTime?: string | null
  opinion?: string | null
  opinionTime?: string | null
}

export const CNT_STATUS_OPTIONS = [
  { value: 10, label: '待接受' },
  { value: 20, label: '会诊中' },
  { value: 30, label: '已完成' },
]

export function cntStatusLabel(status: number): string {
  return CNT_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? String(status)
}

export function cntStatusTagType(status: number): 'warning' | 'primary' | 'success' {
  if (status === 10) return 'warning'
  if (status === 20) return 'primary'
  return 'success'
}

export const getCntPage = (params: { pageNum: number; pageSize: number; status?: number }) =>
  get<PageResult<CntRequest>>('/cnt/requests', params)

/** 新建会诊申请（住院/门诊至少关联一项），返回会诊单号 */
export const createCntRequest = (data: {
  admissionId?: number
  visitId?: number
  patientId: number
  deptId: number
  consultDoctorId: number
  urgent?: number
  reason: string
}) => post<string>('/cnt/requests', data)

/** 接受会诊（10 → 20），权限 cnt:execute */
export const acceptCntRequest = (id: number) => post<void>(`/cnt/requests/${id}/accept`)

/** 完成会诊（20 → 30，附会诊意见），权限 cnt:execute */
export const completeCntRequest = (id: number, opinion: string) =>
  post<void>(`/cnt/requests/${id}/complete?opinion=${encodeURIComponent(opinion)}`)
