/**
 * PHI 展示层脱敏（列表/详情只读视图）。
 * 原则：手机号保留前 3 后 4，身份证保留前 4 后 4；编辑表单与授权导出不在此层处理。
 */
export function maskPhone(phone?: string | null): string {
  if (!phone) return ''
  const digits = phone.replace(/\s/g, '')
  if (digits.length < 7) return digits
  return digits.slice(0, 3) + '****' + digits.slice(-4)
}

export function maskIdCard(idCard?: string | null): string {
  if (!idCard) return ''
  const s = idCard.trim()
  if (s.length < 9) return s
  return s.slice(0, 4) + '**********' + s.slice(-4)
}
