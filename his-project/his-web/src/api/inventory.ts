import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 库存管理模块（阶段三）。权限：inventory:query / inventory:inbound:create。
 * 覆盖：药品入库 / 库存批次 / 库存预警 / 出入库流水。
 */

export interface InboundPayload {
  drugId: number
  batchNo: string
  /** yyyy-MM-dd */
  expiryDate: string
  quantity: number
  unitPrice?: number
  supplier?: string
}

/** 库存批次 */
export interface InventoryBatch {
  id: number
  drugId: number
  drugName: string
  batchNo: string
  expiryDate?: string | null
  quantity: number
  initialQuantity: number
  /** 1 可用 2 冻结 3 过期 4 用尽 */
  status: number
}

export interface BatchQuery {
  pageNum?: number
  pageSize?: number
  drugId?: number
  batchNo?: string
  status?: number
}

/** 库存预警行（药品总量 ≤ stockWarningQty） */
export interface InventoryWarning {
  drugId: number
  drugName: string
  totalQuantity: number
  stockWarningQty: number
  batches: InventoryBatch[]
}

/** 出入库流水 */
export interface InventoryMovement {
  id: number
  drugId: number
  drugName: string
  /** 1 入库 2 发药出库 3 退药入库 4 盘点调整 5 过期核销 */
  movementType: number
  quantity: number
  beforeQty: number
  afterQty: number
  refType?: number | string | null
  refNo?: string | null
  createdAt?: string | null
}

export interface MovementQuery {
  pageNum?: number
  pageSize?: number
  drugId?: number
  refNo?: string
}

// ---------------- 接口 ----------------

/** 药品入库，返回入库单号 */
export const createInbound = (data: InboundPayload) =>
  // 后端 data 直接是入库单号字符串（R<String>）
  post<string>('/inventory/inbound', data)

export const getBatchPage = (params: BatchQuery) =>
  get<PageResult<InventoryBatch>>('/inventory/batches', params)

/** 库存预警列表（非分页） */
export const getWarnings = () => get<InventoryWarning[]>('/inventory/warnings')

export const getMovementPage = (params: MovementQuery) =>
  get<PageResult<InventoryMovement>>('/inventory/movements', params)

// ---------------- 数据字典 ----------------

export const BATCH_STATUS_OPTIONS: DictOption[] = [
  { label: '可用', value: 1 },
  { label: '冻结', value: 2 },
  { label: '过期', value: 3 },
  { label: '用尽', value: 4 },
]

export const MOVEMENT_TYPE_OPTIONS: DictOption[] = [
  { label: '入库', value: 1 },
  { label: '发药出库', value: 2 },
  { label: '退药入库', value: 3 },
  { label: '盘点调整', value: 4 },
  { label: '过期核销', value: 5 },
]

export const batchStatusLabel = (v?: number | null) => dictLabel(BATCH_STATUS_OPTIONS, v)
export const movementTypeLabel = (v?: number | null) => dictLabel(MOVEMENT_TYPE_OPTIONS, v)

/** 批次状态 → el-tag type */
export function batchStatusTagType(v?: number | null): 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 1:
      return 'success'
    case 2:
      return 'warning'
    case 3:
      return 'danger'
    case 4:
      return 'info'
    default:
      return 'info'
  }
}

/** 流水类型 → el-tag type（入库类绿色，出库/核销类红色） */
export function movementTypeTagType(
  v?: number | null
): 'success' | 'danger' | 'warning' | 'info' {
  switch (v) {
    case 1:
    case 3:
      return 'success'
    case 2:
    case 5:
      return 'danger'
    case 4:
      return 'warning'
    default:
      return 'info'
  }
}

/** 流水数量着色：出库/核销为出向（红），其余为入向（绿） */
export function movementQtyClass(v?: number | null): string {
  return v === 2 || v === 5 ? 'qty-out' : 'qty-in'
}
