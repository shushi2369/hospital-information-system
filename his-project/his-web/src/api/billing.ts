import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 收费结算模块（阶段三）。权限：billing:charge:* / billing:refund:* / billing:settle:*。
 * 错误码：B3001 已收费 / B3002 无待缴 / B3003 退费超限 /
 * B3004 已发药先退药 / B3005 已完成就诊挂号费不可退 / B3006 已日结。
 */

/** 未收费就诊行（GET /billing/visits/unpaid） */
export interface UnpaidVisit {
  visitId: number
  visitNo: string
  patientName: string
  visitDate?: string | null
  doctorName?: string | null
  unpaidAmount: string | number
}

/** 待缴明细行 */
export interface PayableItem {
  /** 1 挂号单 2 处方明细 3 检查申请 */
  sourceType: number
  sourceDetailId: number
  itemName: string
  /** 1 挂号费 2 诊查费 3 检查费 4 检验费 5 治疗费 6 材料费 7 药品费 */
  feeType: number
  quantity: number
  unitPrice: string | number
  amount: string | number
}

/** 待缴汇总（GET /billing/visits/{visitId}/payable），金额为字符串 */
export interface PayableResult {
  visitId: number
  items: PayableItem[]
  totalAmount: string | number
}

export interface CreateBillPayload {
  visitId: number
  /** 1 现金 2 银行卡 3 微信 4 支付宝 */
  payMethod: number
}

export interface CreateBillResult {
  billNo: string
  totalAmount: string | number
}

/** 收费单（GET /billing/bills 列表行） */
export interface Bill {
  id: number
  billNo: string
  visitId: number
  patientId: number
  patientName: string
  totalAmount: string | number
  discountAmount: string | number
  payableAmount: string | number
  paidAmount: string | number
  refundAmount: string | number
  payMethod: number
  payTime?: string | null
  cashierName?: string | null
  /** 10 已支付 20 部分退费 30 全额退费 */
  status: number
}

/** 收费明细行 */
export interface ChargeDetail {
  id: number
  billId: number
  feeType: number
  sourceType: number
  sourceDetailId: number
  itemName: string
  quantity: number
  unitPrice: string | number
  amount: string | number
  /** 0 未退 1 部分退 2 全退 */
  refundStatus: number
}

/** 支付记录 */
export interface PaymentRecord {
  id: number
  payNo: string
  payMethod: number
  amount: string | number
  transactionId?: string | null
  payTime?: string | null
}

/** 退费单 */
export interface RefundBill {
  id: number
  refundNo: string
  billId: number
  billNo: string
  refundAmount: string | number
  reason?: string | null
  refundMethod?: number | null
  refundTime?: string | null
  operatorName?: string | null
}

/** 收费单详情（GET /billing/bills/{id}） */
export interface BillDetail {
  bill: Bill
  details: ChargeDetail[]
  payments: PaymentRecord[]
  refunds: RefundBill[]
}

export interface RefundDetailPayload {
  chargeDetailId: number
  refundQuantity: number
}

export interface RefundPayload {
  billId: number
  reason: string
  details: RefundDetailPayload[]
}

/** 日结执行结果（POST /billing/settlements） */
export interface SettlementResult {
  settlementNo: string
  billCount: number
  refundCount: number
  totalChargeAmount: string | number
  totalRefundAmount: string | number
  netAmount: string | number
}

/** 日结记录 */
export interface Settlement {
  id: number
  settlementNo: string
  settleDate: string
  cashierId: number
  cashierName?: string | null
  billCount: number
  refundCount: number
  totalChargeAmount: string | number
  totalRefundAmount: string | number
  netAmount: string | number
}

export interface BillQuery {
  pageNum?: number
  pageSize?: number
  billNo?: string
  patientId?: number
  startDate?: string
  endDate?: string
}

export interface RefundQuery {
  pageNum?: number
  pageSize?: number
  refundNo?: string
  startDate?: string
  endDate?: string
}

export interface SettlementQuery {
  pageNum?: number
  pageSize?: number
  settleDate?: string
  cashierId?: number
}

// ---------------- 接口 ----------------

/** 未收费就诊列表 */
export const getUnpaidVisits = () => get<UnpaidVisit[]>('/billing/visits/unpaid')

/** 待缴明细（B3002 无待缴） */
export const getPayable = (visitId: number) =>
  get<PayableResult>(`/billing/visits/${visitId}/payable`)

/** 创建收费单（B3001 已收费） */
export const createBill = (data: CreateBillPayload) => post<CreateBillResult>('/billing/bills', data)

export const getBillPage = (params: BillQuery) => get<PageResult<Bill>>('/billing/bills', params)

export const getBillDetail = (id: number) => get<BillDetail>(`/billing/bills/${id}`)

/** 退费（B3003 退费超限 / B3004 已发药先退药 / B3005 已完成就诊挂号费不可退） */
// 后端 data 直接是退费单号字符串（R<String>），不是对象
export const createRefund = (data: RefundPayload) => post<string>('/billing/refunds', data)

export const getRefundPage = (params: RefundQuery) =>
  get<PageResult<RefundBill>>('/billing/refunds', params)

/** 执行日结（B3006 已日结），settleDate 格式 yyyy-MM-dd */
export const createSettlement = (settleDate: string) =>
  post<SettlementResult>('/billing/settlements', { settleDate })

export const getSettlementPage = (params: SettlementQuery) =>
  get<PageResult<Settlement>>('/billing/settlements', params)

// ---------------- 数据字典 ----------------

export const PAY_METHOD_OPTIONS: DictOption[] = [
  { label: '现金', value: 1 },
  { label: '银行卡', value: 2 },
  { label: '微信', value: 3 },
  { label: '支付宝', value: 4 },
]

/** 待缴明细来源 */
export const SOURCE_TYPE_OPTIONS: DictOption[] = [
  { label: '挂号单', value: 1 },
  { label: '处方明细', value: 2 },
  { label: '检查申请', value: 3 },
]

export const FEE_TYPE_OPTIONS: DictOption[] = [
  { label: '挂号费', value: 1 },
  { label: '诊查费', value: 2 },
  { label: '检查费', value: 3 },
  { label: '检验费', value: 4 },
  { label: '治疗费', value: 5 },
  { label: '材料费', value: 6 },
  { label: '药品费', value: 7 },
]

export const BILL_STATUS_OPTIONS: DictOption[] = [
  { label: '已支付', value: 10 },
  { label: '部分退费', value: 20 },
  { label: '全额退费', value: 30 },
]

export const REFUND_STATUS_OPTIONS: DictOption[] = [
  { label: '未退', value: 0 },
  { label: '部分退', value: 1 },
  { label: '全退', value: 2 },
]

export const payMethodLabel = (v?: number | null) => dictLabel(PAY_METHOD_OPTIONS, v)
export const sourceTypeLabel = (v?: number | null) => dictLabel(SOURCE_TYPE_OPTIONS, v)
export const feeTypeLabel = (v?: number | null) => dictLabel(FEE_TYPE_OPTIONS, v)
export const billStatusLabel = (v?: number | null) => dictLabel(BILL_STATUS_OPTIONS, v)
export const refundStatusLabel = (v?: number | null) => dictLabel(REFUND_STATUS_OPTIONS, v)

/** 收费单状态 → el-tag type */
export function billStatusTagType(v?: number | null): 'primary' | 'warning' | 'info' {
  if (v === 20) return 'warning'
  if (v === 30) return 'info'
  return 'primary'
}

/** 退费状态 → el-tag type */
export function refundStatusTagType(v?: number | null): 'info' | 'warning' | 'danger' {
  if (v === 1) return 'warning'
  if (v === 2) return 'danger'
  return 'info'
}

/** 支付方式 → el-tag type */
export function payMethodTagType(v?: number | null): 'warning' | 'primary' | 'success' {
  if (v === 1) return 'warning'
  if (v === 3) return 'success'
  return 'primary'
}

/** 费用类别 → el-tag type */
export function feeTypeTagType(
  v?: number | null
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 1:
    case 2:
      return 'primary'
    case 3:
    case 4:
      return 'warning'
    case 5:
    case 6:
      return 'success'
    case 7:
      return 'danger'
    default:
      return 'info'
  }
}
