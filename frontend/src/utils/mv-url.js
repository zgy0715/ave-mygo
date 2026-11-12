const HOSTS = new Set([
  'bilibili.com', 'www.bilibili.com', 'player.bilibili.com',
  'youtube.com', 'www.youtube.com', 'youtu.be',
])

/** 旧数据同样经过来源校验；iframe 只由可信播放器地址重新构造。 */
export function parseMvUrl(value, platform) {
  const empty = { embedUrl: '', externalUrl: '', isBangumi: false }
  if (typeof value !== 'string') return empty
  const raw = value.trim()
  if (!raw || raw.length > 255 || /[\u0000-\u0020\u007f\\]/.test(raw)) return empty
  let url
  try {
    url = new URL(raw)
  } catch {
    return empty
  }
  if (!['http:', 'https:'].includes(url.protocol) || !HOSTS.has(url.hostname)
      || url.username || url.password || url.port) return empty

  const bilibili = ['bilibili.com', 'www.bilibili.com', 'player.bilibili.com'].includes(url.hostname)
  const ep = bilibili && url.searchParams.get('ep_id')
  const isBangumi = platform === 'BILIBILI_PAGE' || Boolean(ep && /^\d+$/.test(ep))
  if (ep && /^\d+$/.test(ep)) {
    return { embedUrl: '', externalUrl: `https://www.bilibili.com/bangumi/play/ep${ep}`, isBangumi }
  }
  const bvid = url.searchParams.get('bvid')
  if (url.hostname === 'player.bilibili.com' && url.pathname === '/player.html'
      && bvid && /^BV[A-Za-z0-9]+$/.test(bvid)) {
    const externalUrl = `https://www.bilibili.com/video/${bvid}`
    if (isBangumi) return { embedUrl: '', externalUrl, isBangumi }
    const embed = new URL('https://player.bilibili.com/player.html')
    embed.searchParams.set('bvid', bvid)
    const page = url.searchParams.get('page')
    if (page && /^[1-9]\d*$/.test(page)) embed.searchParams.set('page', page)
    for (const [key, value] of Object.entries({ high_quality: '1', danmaku: '0', as_wide: '1', autoplay: '0' })) {
      embed.searchParams.set(key, value)
    }
    return { embedUrl: embed.toString(), externalUrl, isBangumi }
  }
  return { embedUrl: '', externalUrl: url.toString(), isBangumi }
}
