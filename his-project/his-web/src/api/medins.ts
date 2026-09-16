import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 医保结算模块（阶段四 /medins）。权限：medins:*（医保专员 + 管理员）。
 * 错误码：B6401 重复申报。
 */

/** 医保申报单，status: 10 已申报 20 对账通过 30 对账差异 */
export interface InsuranceSettle {
  id: number
  settleNo: string
  billId: number
  admissionId?: number | null
  /** 1 职工 2 居民 */
  insuranceType: number
  totalAmount: string | number
  accountPay: string | number
  poolPay: string | number
  selfPay: string | number
  applyTime?: string | null
  status: number
}

export interface InsuranceSettleQuery {
  pageNum?: number
  pageSize?: number
  settleNo?: string
  status?: number
}

/**
 * 日对账汇总：后端返回当日申报单列表（GET /medins/reconcile/daily?settleDate → List）。
 */
export type DailyReconcile = InsuranceSettle[]

// ---------------- 接口 ----------------

/**
 * 医保申报（billId 与 insuranceType 走 query，B6401 重复申报）。
 * data 为结算单号 settleNo（兼容对象形态）。
 */
export const applyInsuranceSettle = (billId: number, insuranceType: number) =>
  post<string | { settleNo?: string }>(
    `/medins/settles?billId=${billId}&insuranceType=${insuranceType}`
  )

export const getInsuranceSettlePage = (params: InsuranceSettleQuery) =>
  get<PageResult<InsuranceSettle>>('/medins/settles', params)

/** 单笔对账 */
export const reconcileSettle = (id: number, remark?: string) =>
  post<void>(`/medins/settles/${id}/reconcile`, { remark: remark || undefined })

/** 日对账汇总（settleDate 格式 yyyy-MM-dd，返回当日申报单列表） */
export const getDailyReconcile = (settleDate: string) =>
  get<DailyReconcile>('/medins/reconcile/daily', { settleDate })

// ---------------- 数据字典 ----------------

export const INSURANCE_TYPE_OPTIONS: DictOption[] = [
  { label: '职工医保', value: 1 },
  { label: '居民医保', value: 2 },
]

export const MEDINS_STATUS_OPTIONS: DictOption[] = [
  { label: '已申报', value: 10 },
  { label: '对账通过', value: 20 },
  { label: '对账差异', value: 30 },
]

export const insuranceTypeLabel = (v?: number | null) => dictLabel(INSURANCE_TYPE_OPTIONS, v)
export const medinsStatusLabel = (v?: number | null) => dictLabel(MEDINS_STATUS_OPTIONS, v)

/** 医保类型 → el-tag type */
export function insuranceTypeTagType(v?: number | null): 'primary' | 'success' {
  return v === 2 ? 'success' : 'primary'
}

/** 申报单状态 → el-tag type */
export function medinsStatusTagType(
  v?: number | null
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
      return 'success'
    case 30:
      return 'danger'
    default:
      return 'info'
  }
}

/** 申报成功返回的 settleNo 归一化 */
export function normalizeSettleNo(v: string | { settleNo?: string } | null | undefined): string {
  if (!v) return ''
  if (typeof v === 'string') return v
  return v.settleNo ?? ''
}
