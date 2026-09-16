import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 住院医嘱与执行模块（阶段四 /doc）。
 * 权限：doc:order:*（医生）、pharmacy:review:do（药师审核）、nur:exec:do（护士执行）。
 * 错误码：B6102 未审核/未摆药不可执行 / B6103 重复执行 / B6104 皮试未过拦截摆药。
 */

export interface OrderItemPayload {
  drugId?: number
  chargeItemId?: number
  dosage?: string
  days?: number
  quantity: number
  usageRoute?: string
  usageNote?: string
}

export interface OrderPayload {
  admissionId: number
  /** 1 长期 2 临时 */
  orderClass: number
  /** 1 药品 2 检查 3 检验 4 治疗 5 护理 6 材料 */
  category: number
  frequency?: string
  skinTestFlag?: boolean
  items: OrderItemPayload[]
}

/** 医嘱行，status: 10 待审核 20 审核通过 30 执行中 40 已执行 50 已停止 60 已作废 70 已驳回 */
export interface InpOrder {
  id: number
  orderNo: string
  admissionId: number
  patientId?: number | null
  patientName?: string | null
  doctorId?: number | null
  doctorName?: string | null
  orderClass: number
  category: number
  frequency?: string | null
  totalAmount?: string | number | null
  status: number
  skinTestFlag?: number | null
  createdAt?: string | null
  stopReason?: string | null
  reviewComment?: string | null
}

/** 医嘱明细行 */
export interface OrderItemRow {
  id: number
  drugId?: number | null
  chargeItemId?: number | null
  drugName?: string | null
  itemName?: string | null
  dosage?: string | null
  days?: number | null
  quantity: number
  unitPrice?: string | number | null
  amount?: string | number | null
  usageRoute?: string | null
  usageNote?: string | null
}

/** 医嘱执行记录行 */
export interface OrderExecutionRow {
  id: number
  execDate?: string | null
  execSlot?: string | null
  execType?: number | null
  status?: number | null
  nurseName?: string | null
  execTime?: string | null
}

export interface OrderDetail {
  order: InpOrder
  items: OrderItemRow[]
  executions: OrderExecutionRow[]
}

/** 待执行单（GET /doc/executions/todo 与 /nur/executions/pending 共用） */
export interface ExecItem {
  id: number
  orderId: number
  itemId: number
  execDate: string
  execSlot?: string | null
  /** 1 核对 2 执行 3 皮试 */
  execType: number
  bedNo?: string | null
  patientName?: string | null
  itemName?: string | null
  /** 1 待执行 2 已执行 3 跳过 */
  status: number
}

export interface OrderQuery {
  pageNum?: number
  pageSize?: number
  admissionId?: number
  category?: number
  status?: number
}

// ---------------- 接口 ----------------

/** 开医嘱，data 为医嘱号 orderNo */
export const createOrder = (data: OrderPayload) => post<string>('/doc/orders', data)

export const getOrderPage = (params: OrderQuery) =>
  get<PageResult<InpOrder>>('/doc/orders', params)

export const getOrderDetail = (id: number) => get<OrderDetail>(`/doc/orders/${id}`)

/** 审核（药师）：pass=false 驳回 */
export const reviewOrder = (id: number, pass: boolean, comment?: string) =>
  post<void>(`/doc/orders/${id}/review`, { pass, comment: comment || undefined })

/** 停嘱 */
export const stopOrder = (id: number, reason: string) =>
  post<void>(`/doc/orders/${id}/stop`, { reason })

/** 恢复停嘱 */
export const resumeOrder = (id: number) => post<void>(`/doc/orders/${id}/resume`)

/** 作废医嘱 */
export const voidOrder = (id: number, reason?: string) =>
  post<void>(`/doc/orders/${id}/void`, { reason: reason || undefined })

/** 摆药出库（B6104 皮试未过拦截） */
export const dispenseOrder = (id: number) => post<void>(`/doc/orders/${id}/dispense`)

/** 待审核医嘱队列（药师） */
export const getOrderReviewQueue = () => get<InpOrder[]>('/doc/orders/review-queue')

/** 待执行单（execDate 格式 yyyy-MM-dd） */
export const getExecTodo = (execDate?: string) =>
  get<ExecItem[]>('/doc/executions/todo', execDate ? { execDate } : undefined)

/** 执行单执行（B6102 未审核/未摆药 / B6103 重复执行） */
export const doExecution = (id: number) => post<void>(`/doc/executions/${id}/do`)

/** 皮试登记（阳性 → 医嘱自动作废） */
export const submitSkinTest = (execId: number, result: string, note?: string) =>
  post<void>(`/doc/skin-tests/${execId}`, { result, note: note || undefined })

// ---------------- 数据字典 ----------------

export const ORDER_CLASS_OPTIONS: DictOption[] = [
  { label: '长期', value: 1 },
  { label: '临时', value: 2 },
]

export const ORDER_CATEGORY_OPTIONS: DictOption[] = [
  { label: '药品', value: 1 },
  { label: '检查', value: 2 },
  { label: '检验', value: 3 },
  { label: '治疗', value: 4 },
  { label: '护理', value: 5 },
  { label: '材料', value: 6 },
]

export const ORDER_STATUS_OPTIONS: DictOption[] = [
  { label: '待审核', value: 10 },
  { label: '审核通过', value: 20 },
  { label: '执行中', value: 30 },
  { label: '已执行', value: 40 },
  { label: '已停止', value: 50 },
  { label: '已作废', value: 60 },
  { label: '已驳回', value: 70 },
]

export const EXEC_TYPE_OPTIONS: DictOption[] = [
  { label: '核对', value: 1 },
  { label: '执行', value: 2 },
  { label: '皮试', value: 3 },
]

/** 住院医嘱频次（与后端契约一致：qd/bid/tid/q8h/prn） */
export const ORDER_FREQUENCY_OPTIONS: Array<{ label: string; value: string }> = [
  { label: 'qd（每日一次）', value: 'qd' },
  { label: 'bid（每日两次）', value: 'bid' },
  { label: 'tid（每日三次）', value: 'tid' },
  { label: 'q8h（每 8 小时）', value: 'q8h' },
  { label: 'prn（必要时）', value: 'prn' },
]

export const EXEC_STATUS_OPTIONS: DictOption[] = [
  { label: '待执行', value: 1 },
  { label: '已执行', value: 2 },
  { label: '跳过', value: 3 },
]

export const SKIN_TEST_RESULT_OPTIONS = ['阴性', '阳性']

export const orderClassLabel = (v?: number | null) => dictLabel(ORDER_CLASS_OPTIONS, v)
export const orderCategoryLabel = (v?: number | null) => dictLabel(ORDER_CATEGORY_OPTIONS, v)
export const orderStatusLabel = (v?: number | null) => dictLabel(ORDER_STATUS_OPTIONS, v)
export const execTypeLabel = (v?: number | null) => dictLabel(EXEC_TYPE_OPTIONS, v)
export const execStatusLabel = (v?: number | null) => dictLabel(EXEC_STATUS_OPTIONS, v)

/** 医嘱状态 → el-tag type */
export function orderStatusTagType(
  v?: number | null
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
      return 'primary'
    case 30:
    case 40:
      return 'success'
    case 70:
      return 'danger'
    default:
      return 'info'
  }
}

/** 执行单状态 → el-tag type */
export function execStatusTagType(v?: number | null): 'success' | 'warning' | 'info' {
  if (v === 1) return 'warning'
  if (v === 2) return 'success'
  return 'info'
}

/** 执行类型 → el-tag type（皮试高亮） */
export function execTypeTagType(v?: number | null): 'danger' | 'primary' | 'info' {
  if (v === 3) return 'danger'
  if (v === 2) return 'primary'
  return 'info'
}
