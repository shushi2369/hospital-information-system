import request from './request'

export interface RptUpload {
  id: number
  uploadNo: string
  bizType: number
  bizNo: string
  payload: string
  status: number
  receiptNo: string | null
  retryCount: number
  lastError: string | null
  uploadedAt: string | null
}

export interface RptStats {
  total: number
  uploaded: number
  pending: number
  failed: number
}

export const getRptUploads = (params: Record<string, unknown>) =>
  request.get<PageResult<RptUpload>>('/rpt/uploads', { params })

export const getRptUploadDetail = (id: number) =>
  request.get<RptUpload>(`/rpt/uploads/${id}`)

export const retryRptUpload = (id: number) =>
  request.post<RptUpload>(`/rpt/uploads/${id}/retry`)

export const deliverRptBatch = (limit = 50) =>
  request.post<number>(`/rpt/uploads/deliver?limit=${limit}`)

export const getRptStats = () => request.get<RptStats>('/rpt/uploads/stats')

export const rptBizTypeLabel = (t: number) =>
  ({ 1: '传染病报告卡', 2: '病案归档', 3: '出院结算' } as Record<number, string>)[t] ?? String(t)

export const rptStatusLabel = (s: number) =>
  ({ 10: '待上报', 20: '已上报', 30: '失败' } as Record<number, string>)[s] ?? String(s)

export const rptStatusTag = (s: number) =>
  (({ 10: 'warning', 20: 'success', 30: 'danger' }) as Record<number, string>)[s] ?? 'info'
