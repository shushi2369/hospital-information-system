import { get } from './request'

/**
 * 国考绩效看板（三期第三批，《19》§5.5）：只读聚合，0 新表。
 * 权限：kpi:view（管理员/对账员）。
 */
export function getWorkload(params?: { from?: string; to?: string }) {
  return get<Record<string, number | string>>('/report/kpi/workload', params)
}

export function getEfficiency() {
  return get<Record<string, number | string>>('/report/kpi/efficiency')
}

export function getSafety() {
  return get<Record<string, number | string>>('/report/kpi/safety')
}
