import { get, post, put, type PageResult } from './request'
import service from './request'
import { dictLabel, type DictOption } from './basedata'
import type { Registration } from './registration'

/**
 * DELETE 请求（request.ts 仅导出 get/post/put 助手，走默认实例保持拦截器行为一致）
 */
const del = <T = void>(url: string): Promise<T> =>
  service.delete(url) as unknown as Promise<T>

/** 就诊患者摘要 */
export interface VisitPatient {
  id: number
  patientNo: string
  name: string
  /** 1 男 2 女 */
  gender: number
  /** yyyy-MM-dd */
  birthDate: string
  phone?: string | null
  allergyHistory?: string | null
}

/** 诊断 */
export interface VisitDiagnosis {
  id: number
  diagnosisCode?: string | null
  diagnosisName: string
  /** 1 主诊断 2 次诊断 */
  diagnosisType: number
}

/** 医嘱 */
export interface VisitOrder {
  id: number
  /** 1 用药指导 2 治疗 3 复诊建议 9 其他 */
  orderType: number
  content: string
  createdAt?: string
}

/** 处方明细行 */
export interface PrescriptionItem {
  id: number
  drugName: string
  spec?: string | null
  dosage: string
  frequency: string
  usageRoute: string
  days: number
  quantity: number | string
  unit?: string | null
  unitPrice: string | number
  amount: string | number
  usageNote?: string | null
}

/** 处方 */
export interface VisitPrescription {
  id: number
  rxNo: string
  totalAmount: string | number
  /** 10 待审核 20 审核通过 30 已发药 40 审核驳回 50 已作废 */
  status: number
  /** 0 未收费 1 已收费 */
  chargeStatus: number
  items: PrescriptionItem[]
}

/** 检查/检验申请 */
export interface VisitExamApplication {
  id: number
  applyNo: string
  itemName: string
  applyType?: number | null
  price: string | number
  /** 0 未收费 1 已收费 */
  chargeStatus: number
  status?: number | null
}

/** 就诊详情 */
export interface VisitDetail {
  id: number
  visitNo: string
  patient: VisitPatient
  doctorName: string
  deptName: string
  visitDate?: string
  chiefComplaint?: string | null
  presentIllness?: string | null
  physicalExam?: string | null
  advice?: string | null
  /** 20 接诊中 30 已完成 */
  status: number
  startTime?: string | null
  endTime?: string | null
  diagnoses: VisitDiagnosis[]
  orders: VisitOrder[]
  prescriptions: VisitPrescription[]
  examApplications: VisitExamApplication[]
}

/** 就诊历史行（GET /clinic/visits） */
export interface VisitBrief {
  id: number
  visitNo: string
  visitDate?: string
  patientName?: string
  patientNo?: string
  deptName?: string
  doctorName?: string
  chiefComplaint?: string | null
  diagnosisNames?: string | null
  /** 20 接诊中 30 已完成 */
  status: number
  createdAt?: string
}

export interface VisitQuery {
  pageNum?: number
  pageSize?: number
  regDate?: string
  status?: number
}

export interface DiagnosisPayload {
  diagnosisCode?: string
  diagnosisName: string
  diagnosisType: number
}

export interface OrderPayload {
  orderType: number
  content: string
}

export interface PrescriptionItemPayload {
  drugId: number
  dosage: string
  frequency: string
  usageRoute: string
  days: number
  quantity: number
  usageNote?: string
}

export interface PrescriptionPayload {
  items: PrescriptionItemPayload[]
}

export interface PrescriptionResult {
  rxNo: string
  totalAmount: string | number
}

export interface ExamApplicationPayload {
  chargeItemId: number
  requirement?: string
}

export interface ExamApplicationResult {
  applyNo: string
  price: string | number
}

// ---------------- 接口 ----------------

/** 我的候诊队列（当前登录医生 + 当前日期），结构同 Registration */
export const getClinicQueue = () => get<Registration[]>('/clinic/queue')

/** 接诊（开始就诊），后端 R<Long> 直接返回 visitId；未挂号已收费等约束由后端校验 */
export const startVisit = (registrationId: number) =>
  post<number>(`/clinic/visits/${registrationId}/start`)

export const getVisitDetail = (id: number) => get<VisitDetail>(`/clinic/visits/${id}`)

/** 暂存病历四要素 */
export const saveVisitRecord = (
  id: number,
  data: { chiefComplaint?: string; presentIllness?: string; physicalExam?: string; advice?: string }
) => put<void>(`/clinic/visits/${id}/record`, data)

export const addDiagnosis = (visitId: number, data: DiagnosisPayload) =>
  post<void>(`/clinic/visits/${visitId}/diagnoses`, data)

export const deleteDiagnosis = (id: number) => del<void>(`/clinic/diagnoses/${id}`)

export const addVisitOrder = (visitId: number, data: OrderPayload) =>
  post<void>(`/clinic/visits/${visitId}/orders`, data)

/** 开立处方，返回处方号与总金额；明细为空 B2005 */
export const createPrescription = (visitId: number, data: PrescriptionPayload) =>
  post<PrescriptionResult>(`/clinic/visits/${visitId}/prescriptions`, data)

/** 作废处方；不允许作废 B2006 */
export const voidPrescription = (id: number, reason?: string) =>
  post<void>(`/clinic/prescriptions/${id}/void`, { reason: reason || undefined })

/** 开检查/检验申请 */
export const createExamApplication = (visitId: number, data: ExamApplicationPayload) =>
  post<ExamApplicationResult>(`/clinic/visits/${visitId}/exam-applications`, data)

/** 提交病历（完成就诊）；病历不完整 B2003 / 就诊已完成 B2001 */
export const completeVisit = (id: number) => post<void>(`/clinic/visits/${id}/complete`)

export const getVisitPage = (params: VisitQuery) =>
  get<PageResult<VisitBrief>>('/clinic/visits', params)

// ---------------- 数据字典 ----------------

export const DIAGNOSIS_TYPE_OPTIONS: DictOption[] = [
  { label: '主诊断', value: 1 },
  { label: '次诊断', value: 2 },
]

export const ORDER_TYPE_OPTIONS: DictOption[] = [
  { label: '用药指导', value: 1 },
  { label: '治疗', value: 2 },
  { label: '复诊建议', value: 3 },
  { label: '其他', value: 9 },
]

export const VISIT_STATUS_OPTIONS: DictOption[] = [
  { label: '接诊中', value: 20 },
  { label: '已完成', value: 30 },
]

export const PRESCRIPTION_STATUS_OPTIONS: DictOption[] = [
  { label: '待审核', value: 10 },
  { label: '审核通过', value: 20 },
  { label: '已发药', value: 30 },
  { label: '审核驳回', value: 40 },
  { label: '已作废', value: 50 },
]

/** 给药频次选项 */
export const FREQUENCY_OPTIONS: Array<{ label: string; value: string }> = [
  { label: 'qd（每日一次）', value: 'qd' },
  { label: 'bid（每日两次）', value: 'bid' },
  { label: 'tid（每日三次）', value: 'tid' },
  { label: 'qid（每日四次）', value: 'qid' },
  { label: 'prn（必要时）', value: 'prn' },
]

/** 给药途径选项 */
export const USAGE_ROUTE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '口服', value: '口服' },
  { label: '静滴', value: '静滴' },
  { label: '肌注', value: '肌注' },
  { label: '外用', value: '外用' },
]

export const diagnosisTypeLabel = (v?: number | null) => dictLabel(DIAGNOSIS_TYPE_OPTIONS, v)
export const orderTypeLabel = (v?: number | null) => dictLabel(ORDER_TYPE_OPTIONS, v)
export const visitStatusLabel = (v?: number | null) => dictLabel(VISIT_STATUS_OPTIONS, v)
export const prescriptionStatusLabel = (v?: number | null) =>
  dictLabel(PRESCRIPTION_STATUS_OPTIONS, v)

/** 就诊状态 → el-tag type */
export function visitStatusTagType(v?: number | null): 'warning' | 'success' | 'info' {
  if (v === 20) return 'warning'
  if (v === 30) return 'success'
  return 'info'
}

/** 处方状态 → el-tag type */
export function prescriptionStatusTagType(
  v?: number | null
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (v) {
    case 10:
      return 'warning'
    case 20:
      return 'primary'
    case 30:
      return 'success'
    case 40:
      return 'danger'
    case 50:
      return 'info'
    default:
      return 'info'
  }
}

/** 收费状态（就诊模块同样使用 0/1），转发 registration 的实现 */
export { chargeStatusLabel, chargeStatusTagType } from './registration'

/**
 * 检查/检验申请执行状态：契约未给出枚举值，按常见值做兼容映射，未知值原样展示。
 */
export function examApplyStatusLabel(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  const map: Record<number, string> = { 10: '待执行', 20: '已执行', 30: '已完成' }
  return map[v] ?? String(v)
}

/** 申请类型：1 检查 2 检验（兼容收费项目类别 3 检查费 / 4 检验费），未知值原样展示 */
export function examApplyTypeLabel(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  const map: Record<number, string> = { 1: '检查', 2: '检验', 3: '检查', 4: '检验' }
  return map[v] ?? String(v)
}
