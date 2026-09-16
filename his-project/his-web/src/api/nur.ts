import { get, post } from './request'
import { dictLabel, type DictOption } from './basedata'
import type { Bed } from './inp'
import type { ExecItem } from './doc'

/**
 * 护理模块（阶段四 /nur）。权限：nur:exec:do / nur:vital:* / nur:schedule:manage（护士 + 管理员）。
 * 执行/皮试直接复用 /doc 的接口（见 doc.ts）。
 * 错误码：A0001 体征超范围 / 排班重复。
 */

/** 在院患者摘要（GET /nur/patients） */
export interface NurPatient {
  admissionId: number
  admissionNo: string
  patientId: number
  patientName: string
  bedId?: number | null
  status: number
}

/** 体征录入载荷（范围：体温 30~42 / 脉搏 30~250 / 呼吸 5~60 / 收缩压 40~260 / 舒张压 20~180） */
export interface VitalSignPayload {
  admissionId: number
  recordTime?: string
  temperature: number
  pulse: number
  respiration: number
  bpHigh: number
  bpLow: number
  spo2?: number
  painScore?: number
}

export interface VitalSign {
  id?: number
  admissionId?: number
  recordTime?: string | null
  temperature?: string | number | null
  pulse?: number | string | null
  respiration?: number | string | null
  bpHigh?: number | string | null
  bpLow?: number | string | null
  spo2?: number | string | null
  painScore?: number | string | null
  nurseName?: string | null
}

/** 护士排班行，shiftType: 1 白班 2 小夜班 3 大夜班（枚举为前端约定） */
export interface Schedule {
  id: number
  nurseId: number
  nurseName?: string | null
  wardId?: number | null
  wardName?: string | null
  shiftDate: string
  shiftType: number
}

export interface SchedulePayload {
  nurseId: number
  wardId: number
  /** yyyy-MM-dd */
  shiftDate: string
  shiftType: number
}

/** 体征取值范围（与后端 A0001 校验一致） */
export const VITAL_RANGES = {
  temperature: { min: 30, max: 42 },
  pulse: { min: 30, max: 250 },
  respiration: { min: 5, max: 60 },
  bpHigh: { min: 40, max: 260 },
  bpLow: { min: 20, max: 180 },
  spo2: { min: 0, max: 100 },
  painScore: { min: 0, max: 10 },
} as const

// ---------------- 接口 ----------------

/** 病区在院患者 */
export const getNurPatients = (params: { wardId?: number } = {}) =>
  get<NurPatient[]>('/nur/patients', params)

/** 护士待执行单（结构同 /doc/executions/todo） */
export const getNurPendingExecutions = (params: { execDate?: string } = {}) =>
  get<ExecItem[]>('/nur/executions/pending', params)

/** 录入体征（超范围 A0001） */
export const createVitalSign = (data: VitalSignPayload) => post<void>('/nur/vital-signs', data)

export const getVitalSigns = (params: {
  admissionId: number
  startTime?: string
  endTime?: string
}) => get<VitalSign[]>('/nur/vital-signs', params)

export const getSchedules = (params: { wardId?: number; shiftDate?: string } = {}) =>
  get<Schedule[]>('/nur/schedules', params)

/** 新增排班（同一护士同日同班次重复 A0001） */
export const createSchedule = (data: SchedulePayload) => post<void>('/nur/schedules', data)

/** 病区床位一览（结构同 /inp/beds） */
export const getNurWardBeds = (params: { wardId?: number; bedStatus?: number } = {}) =>
  get<Bed[]>('/nur/wards/beds', params)

// ---------------- 数据字典 ----------------

export const SHIFT_TYPE_OPTIONS: DictOption[] = [
  { label: '白班', value: 1 },
  { label: '中班', value: 2 },
  { label: '夜班', value: 3 },
]

export const shiftTypeLabel = (v?: number | null) => dictLabel(SHIFT_TYPE_OPTIONS, v)

export function shiftTypeTagType(v?: number | null): 'primary' | 'warning' | 'info' {
  if (v === 1) return 'primary'
  if (v === 2) return 'warning'
  return 'info'
}
