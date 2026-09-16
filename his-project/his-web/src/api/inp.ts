import { get, post, put, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 住院管理模块（阶段四 /inp）。权限：inp:*（收费员 + 管理员）。
 * 错误码：B6002 已有在院记录 / B6005 无可用床位 / B6006 有未完成事项。
 */

/** 病区（GET /inp/wards） */
export interface Ward {
  id: number
  wardCode: string
  wardName: string
  deptId: number
  location?: string | null
}

/** 床位，bedStatus: 1 空闲 2 占用 3 预约 4 停用 */
export interface Bed {
  id: number
  wardId: number
  wardName?: string | null
  bedNo: string
  bedStatus: number
  currentAdmissionId?: number | null
  patientName?: string | null
  bedFee?: string | number | null
}

/** 住院记录，status: 10 在院 20 出院未结 30 已结算 40 转院 */
export interface Admission {
  id: number
  admissionNo: string
  patientId: number
  patientName: string
  patientNo?: string | null
  deptId: number
  deptName?: string | null
  wardId: number
  wardName?: string | null
  bedId?: number | null
  bedNo?: string | null
  doctorId?: number | null
  doctorName?: string | null
  admissionType: number
  admissionTime?: string | null
  plannedDiagnosis?: string | null
  depositTotal: string | number
  dischargeWay?: number | null
  dischargeDiagnosis?: string | null
  dischargeTime?: string | null
  status: number
  chargeStatus?: number | null
}

export interface AdmissionQuery {
  pageNum?: number
  pageSize?: number
  patientId?: number
  deptId?: number
  wardId?: number
  status?: number
  admissionNo?: string
}

export interface AdmissionPayload {
  patientId: number
  deptId: number
  wardId: number
  bedId: number
  doctorId: number
  admissionType: number
  plannedDiagnosis?: string
  depositAmount?: number
  payMethod?: number
}

export interface CreateBedPayload {
  wardId: number
  bedNo: string
  chargeItemId: number
}

export interface TransferPayload {
  toWardId: number
  toBedId: number
  reason: string
}

export interface DepositPayload {
  amount: number
  payMethod: number
}

export interface DischargePayload {
  /** 1 治愈 2 好转 3 未愈 4 死亡 5 自动离院 6 转院 */
  dischargeWay: number
  dischargeDiagnosis?: string
}

/** 补押金返回：累计押金（兼容数字/字符串/对象三种形态） */
export type DepositTotalResult = string | number | { depositTotal?: string | number } | null

export function normalizeDepositTotal(v: DepositTotalResult): string | number {
  if (v === null || v === undefined) return 0
  if (typeof v === 'object') return v.depositTotal ?? 0
  return v
}

/** 住院一日清费用明细行 */
export interface DailyFeeItem {
  id: number
  feeType: number
  sourceType?: number | null
  itemName: string
  quantity: number
  unitPrice: string | number
  amount: string | number
}

/** 住院一日清按日分组 */
export interface DailyFeeGroup {
  feeDate: string
  totalAmount: string | number
  items: DailyFeeItem[]
}

/** 出院结算结果（复用一期 billing 结算通道） */
export interface InpSettleResult {
  billNo?: string
  totalAmount?: string | number
  depositTotal?: string | number
  refundAmount?: string | number
  [key: string]: unknown
}

// ---------------- 接口 ----------------

export const getWards = () => get<Ward[]>('/inp/wards')

export const getBeds = (params: { wardId?: number; bedStatus?: number } = {}) =>
  get<Bed[]>('/inp/beds', params)

export const createBed = (data: CreateBedPayload) => post<void>('/inp/beds', data)

/** 更新床位状态（停用/启用等） */
export const updateBedStatus = (id: number, bedStatus: number) =>
  put<void>(`/inp/beds/${id}/status`, { bedStatus })

/** 入院登记（B6002 已有在院记录），返回住院记录 */
export const createAdmission = (data: AdmissionPayload) => post<Admission>('/inp/admissions', data)

export const getAdmissionPage = (params: AdmissionQuery) =>
  get<PageResult<Admission>>('/inp/admissions', params)

export const getAdmissionDetail = (id: number) => get<Admission>(`/inp/admissions/${id}`)

/** 转科（B6005 无可用床位） */
export const transferAdmission = (id: number, data: TransferPayload) =>
  post<void>(`/inp/admissions/${id}/transfers`, data)

/** 补押金，data 为累计押金 */
export const addDeposit = (id: number, data: DepositPayload) =>
  post<DepositTotalResult>(`/inp/admissions/${id}/deposits`, data)

/** 住院费用一日清 */
export const getDailyFees = (id: number) => get<DailyFeeGroup[]>(`/inp/admissions/${id}/daily-fees`)

/** 手工记账（admissionId 走 query） */
export const createManualFee = (
  admissionId: number,
  data: { feeType: number; itemName: string; quantity: number; unitPrice: number }
) => post<void>(`/inp/daily-fees/manual?admissionId=${admissionId}`, data)

/** 出院申请（B6006 有未完成事项） */
export const dischargeAdmission = (id: number, data: DischargePayload) =>
  post<void>(`/inp/admissions/${id}/discharge`, data)

/**
 * 出院结算（复用一期收费结算通道 POST /billing/admissions/{id}/settle）。
 * 为不改动既有 billing.ts，在此补充定义。
 */
export const settleInpAdmission = (admissionId: number, payMethod: number) =>
  post<InpSettleResult>(`/billing/admissions/${admissionId}/settle`, { payMethod })

// ---------------- 数据字典 ----------------

export const INP_STATUS_OPTIONS: DictOption[] = [
  { label: '在院', value: 10 },
  { label: '出院未结', value: 20 },
  { label: '已结算', value: 30 },
  { label: '转院', value: 40 },
]

export const BED_STATUS_OPTIONS: DictOption[] = [
  { label: '空闲', value: 1 },
  { label: '占用', value: 2 },
  { label: '预约', value: 3 },
  { label: '停用', value: 4 },
]

/** 入院类型（契约未给枚举，按通用取值约定） */
export const ADMISSION_TYPE_OPTIONS: DictOption[] = [
  { label: '普通入院', value: 1 },
  { label: '急诊入院', value: 2 },
  { label: '转院入院', value: 3 },
]

export const DISCHARGE_WAY_OPTIONS: DictOption[] = [
  { label: '治愈', value: 1 },
  { label: '好转', value: 2 },
  { label: '未愈', value: 3 },
  { label: '死亡', value: 4 },
  { label: '自动离院', value: 5 },
  { label: '转院', value: 6 },
]

/** 住院费用类别（1~8，含床位费；与门诊 1~7 不同） */
export const INP_FEE_TYPE_OPTIONS: DictOption[] = [
  { label: '挂号费', value: 1 },
  { label: '诊查费', value: 2 },
  { label: '检查费', value: 3 },
  { label: '检验费', value: 4 },
  { label: '治疗费', value: 5 },
  { label: '材料费', value: 6 },
  { label: '药品费', value: 7 },
  { label: '床位费', value: 8 },
]

export const inpStatusLabel = (v?: number | null) => dictLabel(INP_STATUS_OPTIONS, v)
export const bedStatusLabel = (v?: number | null) => dictLabel(BED_STATUS_OPTIONS, v)
export const admissionTypeLabel = (v?: number | null) => dictLabel(ADMISSION_TYPE_OPTIONS, v)
export const dischargeWayLabel = (v?: number | null) => dictLabel(DISCHARGE_WAY_OPTIONS, v)
export const inpFeeTypeLabel = (v?: number | null) => dictLabel(INP_FEE_TYPE_OPTIONS, v)

/** 住院状态 → el-tag type */
export function inpStatusTagType(v?: number | null): 'primary' | 'success' | 'warning' | 'info' {
  switch (v) {
    case 10:
      return 'primary'
    case 20:
      return 'warning'
    case 30:
      return 'success'
    default:
      return 'info'
  }
}

/** 床位状态 → el-tag type */
export function bedStatusTagType(v?: number | null): 'success' | 'danger' | 'warning' | 'info' {
  switch (v) {
    case 1:
      return 'success'
    case 2:
      return 'danger'
    case 3:
      return 'warning'
    default:
      return 'info'
  }
}
