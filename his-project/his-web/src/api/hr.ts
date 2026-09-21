import { get, post, put, type PageResult } from './request'

/**
 * HR 人事模块（三期第三批，《19》§5.1）。
 * 权限：hr:staff:query / hr:staff:manage。员工与 sys_user 弱关联（userId 可空）。
 */
export interface HrStaff {
  id: number
  staffNo: string
  userId?: number | null
  name: string
  deptId: number
  title: string
  licenseNo?: string | null
  phone?: string | null
  entryDate: string
  exitDate?: string | null
  status: number
}

export interface HrTitleChange {
  id: number
  staffId: number
  oldTitle: string
  newTitle: string
  effectiveDate: string
  note?: string | null
}

export function getStaffPage(params: { pageNum?: number; pageSize?: number; deptId?: number; status?: number; name?: string }) {
  return get<PageResult<HrStaff>>('/hr/staff', params)
}

export function getStaffDetail(id: number) {
  return get<{ staff: HrStaff; titleChanges: HrTitleChange[] }>(`/hr/staff/${id}`)
}

export function createStaff(data: { name: string; deptId: number; title: string; licenseNo?: string; phone?: string; entryDate: string; userId?: number }) {
  return post<string>('/hr/staff', data)
}

export function updateStaff(id: number, data: { name: string; deptId: number; title: string; licenseNo?: string; phone?: string; entryDate: string }) {
  return put<void>(`/hr/staff/${id}`, data)
}

export function exitStaff(id: number) {
  return post<void>(`/hr/staff/${id}/exit`)
}

export function titleChange(id: number, data: { newTitle: string; effectiveDate: string; note?: string }) {
  return post<number>(`/hr/staff/${id}/title-change`, data)
}
