/**
 * 头像 / 图片兜底工具
 *
 * 传承人头像缺失或加载失败时，退化为「灰底圆形 + 姓名首字」的 SVG 占位图，
 * 避免出现浏览器默认的破图图标。
 */

/** 转义 SVG 文本中的特殊字符，避免 XSS */
function escapeSvgText(text: string) {
  return (text || '').replace(/[&<>"']/g, (c) => {
    switch (c) {
      case '&':
        return '&amp;'
      case '<':
        return '&lt;'
      case '>':
        return '&gt;'
      case '"':
        return '&quot;'
      default:
        return '&#39;'
    }
  })
}

/**
 * 生成头像占位图（data URI）：灰色圆形底 + 姓名首字
 *
 * @param name 用于取首字的姓名
 */
export function avatarFallback(name?: string): string {
  const ch = escapeSvgText((name || '蜀').trim().charAt(0) || '蜀')
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200">` +
    `<rect width="100%" height="100%" fill="#D9D4C8"/>` +
    `<circle cx="100" cy="100" r="100" fill="#CDC7B9"/>` +
    `<text x="50%" y="50%" fill="#7A7263" font-size="86" font-family="serif" ` +
    `text-anchor="middle" dominant-baseline="central">${ch}</text></svg>`
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}

/**
 * img 的 @error 处理：替换为姓名占位图，每张只处理一次
 *
 * @param e    事件对象
 * @param name 姓名（用于取首字）
 */
export function onAvatarError(e: Event, name?: string) {
  const img = e.target as HTMLImageElement
  if (!img || img.dataset.fallback === '1') {
    return
  }
  img.dataset.fallback = '1'
  img.src = avatarFallback(name)
}
