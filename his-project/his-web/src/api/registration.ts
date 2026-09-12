import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/** 挂号单（B-11），金额字段后端可能以字符串返回 */
export interface Registration {
  id: number
  regNo: string
  patientId: number
  patientName: string
  patientNo: string
  cardNo?: string | null
  deptId: number
  deptName: string
  doctorId: number
  doctorName: string
  /** yyyy-MM-dd */
  regDate: string
  /** 1 上午 2 下午 */
  period: number
  /** 1 普通号 2 专家号 */
  regType: number
  regFee: string | number
  consultationFee: string | number
  queueNo: number
  /** 10 已挂号 20 已退号 30 已就诊 40 已过号 */
  status: number
  /** 0 未收费 1 已收费 */
  chargeStatus: number
  createdAt?: string
  /** 已创建的就诊 ID（医生候诊队列接口返回，用于"继续接诊"直达工作台） */
  visitId?: number
}

export interface RegistrationPayload {
  patientId: number
  doctorId: number
  regDate: string
  period: number
  regType: number
}

/** 挂号成功返回 */
export interface RegistrationResult {
  regNo: string
  queueNo: number
  regFee: string | number
  consultationFee: string | number
  totalFee: string | number
}

export interface RegistrationQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  doctorId?: number
  regDate?: string
  status?: number
}

/** 现场挂号；重复挂号 B1002 / 号源已满 B1003 */
export const createRegistration = (data: RegistrationPayload) =>
  post<RegistrationResult>('/registrations', data)

/** 退号（B1004 无法退号），退号不自动退费 */
export const cancelRegistration = (id: number) => post<void>(`/registrations/${id}/cancel`)

export const getRegistrationPage = (params: RegistrationQuery) =>
  get<PageResult<Registration>>('/registrations', params)

/** 叫号队列（按 queueNo 升序），不传参数默认当天 */
export const getRegistrationQueue = (
  params: { doctorId?: number; regDate?: string; period?: number } = {}
) => get<Registration[]>('/registrations/queue', params)

// ---------------- 数据字典 ----------------

export const PERIOD_OPTIONS: DictOption[] = [
  { label: '上午', value: 1 },
  { label: '下午', value: 2 },
]

export const REG_TYPE_OPTIONS: DictOption[] = [
  { label: '普通号', value: 1 },
  { label: '专家号', value: 2 },
]

export const REG_STATUS_OPTIONS: DictOption[] = [
  { label: '已挂号', value: 10 },
  { label: '已退号', value: 20 },
  { label: '已就诊', value: 30 },
  { label: '已过号', value: 40 },
]

export const CHARGE_STATUS_OPTIONS: DictOption[] = [
  { label: '未收费', value: 0 },
  { label: '已收费', value: 1 },
]

export const periodLabel = (v?: number | null) => dictLabel(PERIOD_OPTIONS, v)
export const regTypeLabel = (v?: number | null) => dictLabel(REG_TYPE_OPTIONS, v)
export const regStatusLabel = (v?: number | null) => dictLabel(REG_STATUS_OPTIONS, v)
export const chargeStatusLabel = (v?: number | null) => dictLabel(CHARGE_STATUS_OPTIONS, v)

/** 挂号状态 → el-tag type */
export function regStatusTagType(v?: number | null): 'primary' | 'success' | 'info' | 'warning' {
  switch (v) {
    case 10:
      return 'primary'
    case 20:
      return 'info'
    case 30:
      return 'success'
    case 40:
      return 'warning'
    default:
      return 'info'
  }
}

/** 收费状态 → el-tag type */
export function chargeStatusTagType(v?: number | null): 'success' | 'info' {
  return v === 1 ? 'success' : 'info'
}

/** 金额统一格式化（后端可能返回字符串） */
export function fmtMoney(v?: string | number | null): string {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/** 当天日期（yyyy-MM-dd） */
export function todayStr(): string {
  const d = new Date()
  const month = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}
