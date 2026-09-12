import axios from 'axios'
import { ElMessage } from 'element-plus'
import { get, TOKEN_KEY, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'
import { fmtMoney, todayStr } from './registration'
import { FEE_TYPE_OPTIONS, feeTypeLabel, feeTypeTagType } from './billing'

/**
 * 统计报表模块（阶段四）。权限：report:query（管理员 + 对账员）。
 * 所有金额字段后端返回字符串，统一用 fmtMoney 格式化。
 */

// 复用既有字典与格式化工具，视图层统一从本模块引入
export { FEE_TYPE_OPTIONS, feeTypeLabel, feeTypeTagType, fmtMoney, todayStr }

// ---------------- 类型定义 ----------------

/** 日趋势点（挂号量 / 门诊量） */
export interface DailyCountPoint {
  /** yyyy-MM-dd */
  date: string
  count: number
}

/** 科室挂号排名行（降序） */
export interface DeptRegStat {
  name: string
  count: number
}

/** 收入日趋势行（金额为字符串） */
export interface DailyRevenuePoint {
  /** yyyy-MM-dd */
  date: string
  chargeAmount: string | number
  refundAmount: string | number
  netAmount: string | number
}

/** 收入类别分布行 */
export interface FeeTypeAmount {
  /** 1 挂号费 2 诊查费 3 检查费 4 检验费 5 治疗费 6 材料费 7 药品费 */
  feeType: number
  amount: string | number
}

/** 收入明细分页查询参数 */
export interface RevenueDetailQuery {
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}

/** 收入明细行 */
export interface RevenueDetailRow {
  /** 业务发生时间（yyyy-MM-dd HH:mm:ss） */
  time?: string | null
  /** 收费单号 / 退费单号 */
  docNo: string
  /** 1 收费 2 退费 */
  type: number
  patientName?: string | null
  itemName: string
  feeType: number
  quantity: number
  unitPrice: string | number
  amount: string | number
}

/** 药品批次行 */
export interface DrugBatchInfo {
  batchNo: string
  expiryDate?: string | null
  quantity: number
  /** 1 可用 3 过期 4 用尽 */
  status: number
}

/** 药品库存统计行（GET /stats/drug-inventory） */
export interface DrugInventoryRow {
  drugId: number
  drugName: string
  totalQuantity: number
  stockWarningQty: number
  /** 总量低于等于预警下限时为 true */
  warning: boolean
  batches: DrugBatchInfo[]
}

// ---------------- 接口 ----------------

/** 挂号量日趋势 */
export const getRegistrationDaily = (startDate: string, endDate: string) =>
  get<DailyCountPoint[]>('/stats/registrations/daily', { startDate, endDate })

/** 挂号量按科室排名（降序） */
export const getRegistrationByDept = (startDate: string, endDate: string) =>
  get<DeptRegStat[]>('/stats/registrations/dept', { startDate, endDate })

/** 门诊量日趋势（按就诊完成时间） */
export const getVisitDaily = (startDate: string, endDate: string) =>
  get<DailyCountPoint[]>('/stats/visits/daily', { startDate, endDate })

/** 收入日趋势（收费 / 退费 / 净额） */
export const getRevenueDaily = (startDate: string, endDate: string) =>
  get<DailyRevenuePoint[]>('/stats/revenue/daily', { startDate, endDate })

/** 收入按费用类别分布 */
export const getRevenueDistribution = (startDate: string, endDate: string) =>
  get<FeeTypeAmount[]>('/stats/revenue/distribution', { startDate, endDate })

/** 收入明细分页 */
export const getRevenueDetailPage = (params: RevenueDetailQuery) =>
  get<PageResult<RevenueDetailRow>>('/stats/revenue/detail', params)

/** 药品库存统计（实时数据，无日期参数） */
export const getDrugInventory = () => get<DrugInventoryRow[]>('/stats/drug-inventory')

// ---------------- 字典 ----------------

/** 收入明细类型：1 收费 2 退费 */
export const REVENUE_TYPE_OPTIONS: DictOption[] = [
  { label: '收费', value: 1 },
  { label: '退费', value: 2 },
]

export const revenueTypeLabel = (v?: number | null) => dictLabel(REVENUE_TYPE_OPTIONS, v)

/** 收入明细类型 → el-tag type */
export function revenueTypeTagType(v?: number | null): 'success' | 'danger' | 'info' {
  if (v === 2) return 'danger'
  if (v === 1) return 'success'
  return 'info'
}

/** 批次状态：1 可用 3 过期 4 用尽 */
export const BATCH_STATUS_OPTIONS: DictOption[] = [
  { label: '可用', value: 1 },
  { label: '过期', value: 3 },
  { label: '用尽', value: 4 },
]

export const batchStatusLabel = (v?: number | null) => dictLabel(BATCH_STATUS_OPTIONS, v)

/** 批次状态 → el-tag type */
export function batchStatusTagType(v?: number | null): 'success' | 'danger' | 'info' {
  if (v === 3) return 'danger'
  if (v === 4) return 'info'
  return 'success'
}

// ---------------- CSV 导出 ----------------

/**
 * 导出收入明细 CSV（GET /stats/revenue/detail/export）。
 * 注意：request.ts 的响应拦截器按 JSON 业务码解析，Blob 响应会走失败分支，
 * 因此这里直接用 axios 以 blob 方式请求（同样走 /api/v1 代理与 Bearer token）。
 */
export async function exportRevenueDetail(startDate: string, endDate: string): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY)
  let blob: Blob
  try {
    const resp = await axios.get('/api/v1/stats/revenue/detail/export', {
      params: { startDate, endDate },
      responseType: 'blob',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    })
    blob = resp.data as Blob
  } catch (err) {
    // 权限不足等场景后端返回 JSON 错误体，尝试解析并提示
    const data = (err as { response?: { data?: unknown } }).response?.data
    let message = '导出失败，请稍后重试'
    if (data instanceof Blob) {
      try {
        const parsed = JSON.parse(await data.text()) as { message?: string }
        if (parsed && parsed.message) message = parsed.message
      } catch {
        // 非 JSON 错误体，保留默认提示
      }
    }
    ElMessage.error(message)
    throw err
  }
  const filename = `收入明细_${startDate}_to_${endDate}.csv`
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
