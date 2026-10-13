/**
 * 用户后台公共格式化工具
 */

/**
 * 时间格式化：后端 LocalDateTime 序列化后形如 2026-09-19T10:20:30
 *
 * @param value 原始时间字符串
 * @returns 形如 2026-09-19 10:20
 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return ''
  }
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}
