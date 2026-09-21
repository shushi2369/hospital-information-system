import { get, post, type PageResult } from './request'
import { dictLabel, type DictOption } from './basedata'

/**
 * 药库模块（三期 /whse）。权限：whse:po:query / whse:po:create / whse:po:approve / whse:supplier:manage。
 * 状态机：10 待审批 → 20 已下单 → 30 已到货；40 已取消。
 * 错误码已在拦截器统一提示（A0001 状态不对等）。
 */

/** 供应商（GET /whse/suppliers 行） */
export interface Supplier {
  id: number
  supplierCode: string
  supplierName: string
  contact?: string | null
  phone?: string | null
  status?: number
}

export interface SupplierPayload {
  supplierCode: string
  supplierName: string
  contact?: string
  phone?: string
}

/** 采购单（GET /whse/purchase-orders 行），金额后端以字符串返回 */
export interface PurchaseOrder {
  id: number
  poNo: string
  supplierId: number
  drugId: number
  quantity: number | string
  unitPrice: string | number
  /** yyyy-MM-dd */
  expectedDate?: string | null
  /** 10 待审批 20 已下单 30 已到货 40 已取消 */
  status: number
  /** 到货入库后回填 */
  inboundNo?: string | null
  approverId?: number | null
  approvedAt?: string | null
}

export interface PurchaseOrderQuery {
  pageNum?: number
  pageSize?: number
  status?: number
  supplierId?: number
}

export interface PurchaseOrderPayload {
  supplierId: number
  drugId: number
  quantity: number
  unitPrice: number
  /** yyyy-MM-dd，选填 */
  expectedDate?: string
}

// ---------------- 接口 ----------------

/** 供应商列表（全量数组） */
export const getSupplierList = () => get<Supplier[]>('/whse/suppliers')

/** 新增供应商，data 为供应商 id */
export const createSupplier = (data: SupplierPayload) => post<number>('/whse/suppliers', data)

/** 采购单分页 */
export const getPurchaseOrderPage = (params: PurchaseOrderQuery) =>
  get<PageResult<PurchaseOrder>>('/whse/purchase-orders', params)

/** 创建采购单，data 为采购单号 poNo */
export const createPurchaseOrder = (data: PurchaseOrderPayload) =>
  post<string>('/whse/purchase-orders', data)

/** 审批（10 → 20） */
export const approvePurchaseOrder = (id: number) =>
  post<void>(`/whse/purchase-orders/${id}/approve`)

/** 到货入库（20 → 30），data 为入库单号 inboundNo */
export const receivePurchaseOrder = (id: number, expiryDate: string) =>
  post<string>(`/whse/purchase-orders/${id}/receive`, { expiryDate })

/** 取消（待审批，10 → 40） */
export const cancelPurchaseOrder = (id: number) =>
  post<void>(`/whse/purchase-orders/${id}/cancel`)

// ---------------- 数据字典 ----------------

/** 采购单状态 */
export const PO_STATUS_OPTIONS: DictOption[] = [
  { label: '待审批', value: 10 },
  { label: '已下单', value: 20 },
  { label: '已到货', value: 30 },
  { label: '已取消', value: 40 },
]

export const poStatusLabel = (v?: number | null) => dictLabel(PO_STATUS_OPTIONS, v)

/** 采购单状态 → el-tag type */
export function poStatusTagType(v?: number | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
      return 'primary'
    case 30:
      return 'success'
    case 40:
      return 'info'
    default:
      return 'info'
  }
}
