import { get, post, put, type PageResult } from './request'
import type { DictOption } from './basedata'

/** 患者档案（B-01），列表接口手机号/身份证号返回脱敏值 */
export interface Patient {
  id: number
  patientNo: string
  name: string
  /** 1 男 2 女 */
  gender: number
  /** yyyy-MM-dd */
  birthDate: string
  phone?: string | null
  idCardNo?: string | null
  address?: string | null
  allergyHistory?: string | null
  pastHistory?: string | null
  createdAt?: string
}

export interface PatientQuery {
  pageNum?: number
  pageSize?: number
  name?: string
  phone?: string
  patientNo?: string
  idCardNo?: string
}

export interface PatientPayload {
  name: string
  gender: number
  birthDate: string
  idCardNo: string
  phone: string
  address?: string
  allergyHistory?: string
  pastHistory?: string
}

/** 建档，返回建档号 patientNo；重复建档后端返回 B1001 */
export const createPatient = (data: PatientPayload) => post<string>('/patients', data)

export const getPatientPage = (params: PatientQuery) =>
  get<PageResult<Patient>>('/patients', params)

export const getPatientDetail = (id: number) => get<Patient>(`/patients/${id}`)

/** 档案维护仅允许修改联系方式/地址/过敏史/既往史 */
export const updatePatient = (
  id: number,
  data: { phone?: string; address?: string; allergyHistory?: string; pastHistory?: string }
) => put<void>(`/patients/${id}`, data)

/** 补办就诊卡，返回新卡号 cardNo */
export const issuePatientCard = (id: number) => post<string>(`/patients/${id}/cards`)

// ---------------- 数据字典 ----------------

export const GENDER_OPTIONS: DictOption[] = [
  { label: '男', value: 1 },
  { label: '女', value: 2 },
]

export function genderLabel(v?: number | null): string {
  return GENDER_OPTIONS.find((o) => o.value === v)?.label ?? '-'
}

/** 按 birthDate（yyyy-MM-dd）计算周岁年龄 */
export function calcAge(birthDate?: string | null): number | null {
  if (!birthDate) return null
  const birth = new Date(birthDate.replace(/-/g, '/'))
  if (Number.isNaN(birth.getTime())) return null
  const now = new Date()
  let age = now.getFullYear() - birth.getFullYear()
  const month = now.getMonth() - birth.getMonth()
  if (month < 0 || (month === 0 && now.getDate() < birth.getDate())) {
    age--
  }
  return age >= 0 ? age : null
}
