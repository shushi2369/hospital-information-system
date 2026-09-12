import { get, post, put, type PageResult } from './request'

/** 机构信息（D-01/D-02） */
export interface Organization {
  id: number
  orgCode: string
  orgName: string
  address?: string | null
  phone?: string | null
  status: number
}

/** 科室（D-03 ~ D-05），deptType: 1临床科室 2医技科室 3药房 4收费挂号 */
export interface Department {
  id: number
  orgId: number
  deptCode: string
  deptName: string
  deptType: number
  location?: string | null
  status: number
  updatedAt?: string
}

/** 医生（D-06 ~ D-08），金额字段后端以字符串返回 */
export interface Doctor {
  id: number
  userId: number
  deptId: number
  doctorCode: string
  doctorName: string
  title: string
  isExpert: number
  normalFee: string
  expertFee: string
  dailyQuota: number
  status: number
  updatedAt?: string
}

/** 药品（D-09 ~ D-11），金额字段后端以字符串返回 */
export interface Drug {
  id: number
  drugCode: string
  drugName: string
  genericName?: string | null
  spec: string
  dosageForm?: string | null
  category: number
  manufacturer?: string | null
  unit: string
  retailPrice: string
  stockWarningQty: string | number
  isAntibiotic: number
  status: number
  updatedAt?: string
}

/** 收费项目（D-12 ~ D-14），price 为字符串 */
export interface ChargeItem {
  id: number
  itemCode: string
  itemName: string
  category: number
  price: string
  unit: string
  status: number
  updatedAt?: string
}

// ---------------- 数据字典 ----------------

export interface DictOption {
  label: string
  value: number
}

/** 科室类型字典 */
export const DEPT_TYPE_OPTIONS: DictOption[] = [
  { label: '临床科室', value: 1 },
  { label: '医技科室', value: 2 },
  { label: '药房', value: 3 },
  { label: '收费挂号', value: 4 },
]

/** 药品分类字典 */
export const DRUG_CATEGORY_OPTIONS: DictOption[] = [
  { label: '西药', value: 1 },
  { label: '中成药', value: 2 },
  { label: '中药饮片', value: 3 },
]

/** 收费项目类别字典 */
export const CHARGE_ITEM_CATEGORY_OPTIONS: DictOption[] = [
  { label: '挂号费', value: 1 },
  { label: '诊查费', value: 2 },
  { label: '检查费', value: 3 },
  { label: '检验费', value: 4 },
  { label: '治疗费', value: 5 },
  { label: '材料费', value: 6 },
  { label: '药品费', value: 7 },
]

export function dictLabel(options: DictOption[], value?: number | null): string {
  if (value === undefined || value === null) return '-'
  return options.find((o) => o.value === value)?.label ?? String(value)
}

export const deptTypeLabel = (v?: number | null) => dictLabel(DEPT_TYPE_OPTIONS, v)
export const drugCategoryLabel = (v?: number | null) => dictLabel(DRUG_CATEGORY_OPTIONS, v)
export const chargeCategoryLabel = (v?: number | null) => dictLabel(CHARGE_ITEM_CATEGORY_OPTIONS, v)

// ---------------- 机构信息 ----------------

export const getOrg = () => get<Organization>('/basedata/org')

/** orgCode 不可修改，仅提交可维护字段 */
export const updateOrg = (data: { orgName: string; address?: string; phone?: string }) =>
  put<void>('/basedata/org', data)

// ---------------- 科室管理 ----------------

export const getDepartmentList = (params: { deptType?: number; status?: number } = {}) =>
  get<Department[]>('/basedata/departments', params)

export const createDepartment = (data: {
  orgId: number
  deptCode: string
  deptName: string
  deptType: number
  location?: string
}) => post<void>('/basedata/departments', data)

export const updateDepartment = (
  id: number,
  data: { deptCode?: string; deptName: string; deptType: number; location?: string; status?: number }
) => put<void>(`/basedata/departments/${id}`, data)

// ---------------- 医生管理 ----------------

export const getDoctorList = (params: { deptId?: number; isExpert?: number; status?: number } = {}) =>
  get<Doctor[]>('/basedata/doctors', params)

export const createDoctor = (data: {
  userId: number
  deptId: number
  doctorCode: string
  doctorName: string
  title: string
  isExpert: number
  normalFee: number
  expertFee: number
  dailyQuota: number
}) => post<void>('/basedata/doctors', data)

export const updateDoctor = (
  id: number,
  data: {
    deptId: number
    doctorCode: string
    doctorName: string
    title: string
    isExpert: number
    normalFee: number
    expertFee: number
    dailyQuota: number
    status?: number
  }
) => put<void>(`/basedata/doctors/${id}`, data)

// ---------------- 药品管理（分页） ----------------

export interface DrugQuery {
  pageNum?: number
  pageSize?: number
  drugName?: string
  drugCode?: string
  category?: number
  status?: number
}

export const getDrugPage = (params: DrugQuery) => get<PageResult<Drug>>('/basedata/drugs', params)

export interface DrugPayload {
  drugCode: string
  drugName: string
  genericName?: string
  spec: string
  dosageForm?: string
  category: number
  manufacturer?: string
  unit: string
  retailPrice: number
  stockWarningQty: number
  isAntibiotic: number
}

export const createDrug = (data: DrugPayload) => post<void>('/basedata/drugs', data)

export const updateDrug = (id: number, data: DrugPayload & { status?: number }) =>
  put<void>(`/basedata/drugs/${id}`, data)

// ---------------- 收费项目管理 ----------------

export const getChargeItemList = (params: { category?: number; status?: number } = {}) =>
  get<ChargeItem[]>('/basedata/charge-items', params)

export const createChargeItem = (data: {
  itemCode: string
  itemName: string
  category: number
  price: number
  unit: string
}) => post<void>('/basedata/charge-items', data)

export const updateChargeItem = (
  id: number,
  data: { itemCode: string; itemName: string; category: number; price: number; unit: string; status?: number }
) => put<void>(`/basedata/charge-items/${id}`, data)
