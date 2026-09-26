import { get, post, type PageResult } from './request'

/**
 * 药房工作台模块（阶段三）。权限：pharmacy:review:* / pharmacy:dispense:* / pharmacy:return:*。
 * 错误码：B4001 未审核 / B4002 未收费 / B4003 重复发药 / B4004 库存不足 /
 * B4005 过期 / B4008 重复审核。
 */

/** 处方明细行 */
export interface RxItem {
  id: number
  drugName: string
  spec?: string | null
  dosage: string
  frequency: string
  usageRoute: string
  days: number
  quantity: number
  unit?: string | null
  unitPrice: string | number
  amount: string | number
  usageNote?: string | null
}

/** 审核队列 / 可发药队列行 */
export interface RxQueueItem {
  id: number
  rxNo: string
  visitId: number
  visitNo: string
  patientName: string
  patientNo: string
  doctorName?: string | null
  deptName?: string | null
  totalAmount: string | number
  /** 10 待审核 20 审核通过 30 已发药 40 审核驳回 50 已作废 */
  status: number
  /** 0 未收费 1 已收费 */
  chargeStatus: number
  createdAt?: string | null
  items: RxItem[]
}

/** 发药单 */
export interface DispenseOrder {
  id: number
  dispenseNo: string
  rxNo: string
  prescriptionId: number
  patientName: string
  dispenserName?: string | null
  totalQuantity: number
  dispenseTime?: string | null
}

/** 退药单 */
export interface ReturnOrder {
  id: number
  returnNo: string
  dispenseNo: string
  rxNo: string
  patientName: string
  reason?: string | null
  returnTime?: string | null
  operatorName?: string | null
}

export interface DispenseOrderQuery {
  pageNum?: number
  pageSize?: number
  dispenseNo?: string
  startDate?: string
  endDate?: string
}

export interface ReturnOrderQuery {
  pageNum?: number
  pageSize?: number
  returnNo?: string
}

// ---------------- 接口 ----------------

/** 处方审核队列（status 默认 10 待审核，可传 20 查看已通过） */
export const getPrescriptions = (status = 10) =>
  get<RxQueueItem[]>('/pharmacy/prescriptions', { status })

/** 处方审核：pass=通过/驳回，意见由页面必填约束；重复审核 B4008 */
export const reviewPrescription = (id: number, pass: boolean, comment?: string) =>
  post<void>(`/pharmacy/prescriptions/${id}/review`, { pass, comment: comment || undefined })

/** 可发药队列（审核通过且已收费） */
export const getDispensable = () => get<RxQueueItem[]>('/pharmacy/prescriptions/dispensable')

/** 发药，返回发药单号（B4001 未审核 / B4002 未收费 / B4003 重复发药 / B4004 库存不足 / B4005 过期） */
export const dispensePrescription = (id: number) =>
  post<string>(`/pharmacy/prescriptions/${id}/dispense`)

export const getDispenseOrderPage = (params: DispenseOrderQuery) =>
  get<PageResult<DispenseOrder>>('/pharmacy/dispense-orders', params)

/** 整方退药，返回退药单号；退费需到收费处办理 */
export const createReturn = (dispenseOrderId: number, reason: string) =>
  post<string>('/pharmacy/returns', { dispenseOrderId, reason })

export const getReturnPage = (params: ReturnOrderQuery) =>
  get<PageResult<ReturnOrder>>('/pharmacy/returns', params)

// ---------------- 数据字典 ----------------
// 处方状态 / 收费状态字典与门诊模块共用同一枚举，直接复用 clinic / registration 的实现
export {
  PRESCRIPTION_STATUS_OPTIONS as RX_STATUS_OPTIONS,
  prescriptionStatusLabel as rxStatusLabel,
  prescriptionStatusTagType as rxStatusTagType,
  chargeStatusLabel,
  chargeStatusTagType,
} from './clinic'
