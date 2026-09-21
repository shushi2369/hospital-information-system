import { get, post, type PageResult } from './request'

/**
 * 物资耗材模块（三期第三批，《19》§5.2）。
 * 权限：mat:material:manage / mat:stock:query / mat:purchase:* / mat:requisition:*。
 * 库存原子操作：入库累加、领用递减（不足拒绝）。
 */
export interface MatMaterial {
  id: number
  materialCode: string
  name: string
  category: number
  unit: string
  price: number
  safeStock: number
  status: number
}

export interface MatStock {
  id: number
  materialId: number
  quantity: number
}

export interface MatPurchase {
  id: number
  poNo: string
  supplierId: number
  materialId: number
  quantity: number
  unitPrice: number
  expectedDate?: string | null
  status: number
}

export interface MatRequisition {
  id: number
  reqNo: string
  materialId: number
  deptId: number
  quantity: number
  purpose?: string | null
  status: number
}

export const MAT_CATEGORY_OPTIONS = [
  { value: 1, label: '卫生材料' },
  { value: 2, label: '低值易耗' },
  { value: 3, label: '办公用品' },
  { value: 4, label: '试剂' },
]

export function matCategoryLabel(c: number): string {
  return MAT_CATEGORY_OPTIONS.find((o) => o.value === c)?.label ?? String(c)
}

export const MAT_PO_STATUS_OPTIONS = [
  { value: 10, label: '待审批' },
  { value: 20, label: '已下单' },
  { value: 30, label: '已入库' },
  { value: 40, label: '已取消' },
]

export function matPoStatusLabel(s: number): string {
  return MAT_PO_STATUS_OPTIONS.find((o) => o.value === s)?.label ?? String(s)
}

export function matPoStatusTagType(s: number): 'info' | 'primary' | 'success' | 'danger' {
  if (s === 30) return 'success'
  if (s === 20) return 'primary'
  if (s === 40) return 'danger'
  return 'info'
}

export function getMaterialPage(params: { pageNum?: number; pageSize?: number; category?: number }) {
  return get<PageResult<Record<string, unknown>>>('/mat/materials', params)
}

export function createMaterial(data: { materialCode: string; name: string; category: number; unit: string; price: number; safeStock: number }) {
  return post<string>('/mat/materials', data)
}

export function getStocks() {
  return get<MatStock[]>('/mat/stocks')
}

export function getPurchasePage(params: { pageNum?: number; pageSize?: number; status?: number }) {
  return get<PageResult<MatPurchase>>('/mat/purchases', params)
}

export function createPurchase(data: { supplierId: number; materialId: number; quantity: number; unitPrice: number; expectedDate?: string }) {
  return post<string>('/mat/purchases', data)
}

export function approvePurchase(id: number) {
  return post<void>(`/mat/purchases/${id}/approve`)
}

export function receivePurchase(id: number) {
  return post<void>(`/mat/purchases/${id}/receive`)
}

export function cancelPurchase(id: number) {
  return post<void>(`/mat/purchases/${id}/cancel`)
}

export function getRequisitionPage(params: { pageNum?: number; pageSize?: number; materialId?: number }) {
  return get<PageResult<MatRequisition>>('/mat/requisitions', params)
}

export function createRequisition(data: { materialId: number; deptId: number; quantity: number; purpose?: string }) {
  return post<string>('/mat/requisitions', data)
}

export interface MatBatchRow {
  id: number
  materialId: number
  batchNo: string
  expireDate: string
  quantity: number
  expireSoon: boolean
}

export function getBatches(params?: { materialId?: number }) {
  return get<MatBatchRow[]>('/mat/batches', params)
}
