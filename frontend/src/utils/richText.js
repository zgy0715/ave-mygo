/**
 * 轻量富文本解析（docs/12 P1「先转义再替换，禁止 v-html」）
 *
 * 安全模型：
 *  1) 先把 < > & " ' 全部转义 —— 任何输入都不可能拼出标签；
 *  2) 只在转义后的串上识别 **加粗** 与列表标记（此时不存在 <，标记无法伪装成标签）；
 *  3) 输出前把每个 run 反转义回纯文本，交给 Vue 的 {{ }} 渲染。
 * 因此返回值里**没有任何 html 字段**，渲染路径无法执行注入内容。
 */

export const RICH_MAX_INPUT = 20000

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
const UNESC = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'" }

/** 唯一产出 HTML 实体的出口（渲染路径不消费它的字符串结果） */
export function escapeHtml(str) {
  return String(str == null ? '' : str).replace(/[&<>"']/g, (m) => ESC[m])
}

/** 反向还原五个实体；与 escapeHtml 往返幂等 */
export function unescapeHtml(str) {
  return String(str == null ? '' : str).replace(/&(amp|lt|gt|quot|#39);/g, (m) => UNESC[m] || m)
}

/** 规范化：CRLF→LF、去控制字符/零宽字符、硬上限 */
function sanitize(raw) {
  return String(raw == null ? '' : raw)
    .replace(/\r\n?/g, '\n')
    // eslint-disable-next-line no-control-regex
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .slice(0, RICH_MAX_INPUT)
}

const ORDERED = /^ {0,3}(\d{1,2})[.)]\s+/
const BULLET = /^ {0,3}[-*•]\s+/
const BOLD_RE = /\*\*(\S(?:[^*\n]{0,198}\S)?)\*\*/g
const IN_URL = /(?:https?:\/\/|www\.)\S*$/
const CONTINUATION = /^ {2,}\S/

/**
 * 单行内配对加粗；未闭合的 ** 默认按字面保留（不吞后半句）
 * 流式场景允许把行尾未闭合的 ** 收尾为加粗，避免闪烁
 */
function inlineRuns(text, { allowUnclosed = false } = {}) {
  const runs = []
  let last = 0
  let m
  BOLD_RE.lastIndex = 0
  while ((m = BOLD_RE.exec(text))) {
    const before = text.slice(0, m.index)
    if (IN_URL.test(before)) continue        // 标记落在 URL 里 → 当字面量
    if (m.index > last) runs.push({ text: text.slice(last, m.index), bold: false })
    runs.push({ text: m[1], bold: true })
    last = m.index + m[0].length
  }
  let tail = text.slice(last)

  if (allowUnclosed) {
    const i = tail.lastIndexOf('**')
    if (i >= 0 && tail.indexOf('**', i + 2) === -1 && tail.slice(i + 2).trim()) {
      const head = tail.slice(0, i)
      if (head) runs.push({ text: head, bold: false })
      runs.push({ text: tail.slice(i + 2), bold: true })
      tail = ''
    }
  }
  if (tail) runs.push({ text: tail, bold: false })

  return runs.map((r) => ({ text: unescapeHtml(r.text), bold: r.bold }))
}

/**
 * @param {string} raw 纯文本（模型输出）
 * @param {{streaming?: boolean}} [opts]
 * @returns {{blocks: Array<{type:'p', runs: Run[]} | {type:'li', ordered:boolean, marker:string, runs: Run[]}>}}
 *          Run = { text: string, bold: boolean }
 */
export function parseRich(raw, opts = {}) {
  const text = sanitize(raw)
  if (!text.trim() && !text.includes('\n')) return { blocks: [] }

  const streaming = !!opts.streaming
  const lines = text.split('\n')
  const blocks = []
  let para = []
  let lastLi = null

  const flushPara = () => {
    if (!para.length) return
    blocks.push({ type: 'p', runs: inlineRuns(para.join('\n'), { allowUnclosed: streaming }) })
    para = []
  }

  lines.forEach((line) => {
    const om = line.match(ORDERED)
    const bm = om ? null : line.match(BULLET)

    if (om || bm) {
      flushPara()
      const li = {
        type: 'li',
        ordered: !!om,
        marker: om ? `${om[1]}.` : '•',
        runs: inlineRuns(line.slice((om || bm)[0].length), { allowUnclosed: streaming }),
      }
      blocks.push(li)
      lastLi = li
      return
    }

    if (lastLi && CONTINUATION.test(line)) {
      // 列表续行（2 空格缩进）并入上一条，不切成新段落
      lastLi.runs.push(...inlineRuns('\n' + line.trim(), { allowUnclosed: streaming }))
      return
    }

    para.push(line)
    lastLi = null
  })
  flushPara()

  return { blocks }
}
