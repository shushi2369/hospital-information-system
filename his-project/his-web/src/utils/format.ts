/**
 * 通用展示格式化（一百四十六轮 F-C4）：
 * 后端 LocalDateTime 序列化为 ISO `2026-10-25T11:50:51`，列表直出带 T 不符合阅读习惯。
 * 非法/空值原样回退（'-' 由调用方决定）。
 */
export function fmtDateTime(v?: string | null): string {
  if (!v) return ''
  // 已是友好格式则原样返回
  if (!v.includes('T')) return v
  return v.replace('T', ' ')
}
