import { describe, expect, it } from 'vitest'
import { escapeHtml, unescapeHtml, parseRich, RICH_MAX_INPUT } from '@/utils/richText'

/**
 * 安全轻排版单测（docs/12 P1：先转义再替换、禁止 v-html）
 * 覆盖：转义往返、加粗配对与未闭合、跨行、URL、列表四类、续行、
 *      超长串、控制字符、上限、恶意标签、空输入
 */
const runsText = (blocks) => blocks.flatMap((b) => b.runs.map((r) => r.text)).join('')
const flatRuns = (blocks) => blocks.flatMap((b) => b.runs)

describe('richText · 转义', () => {
  it('五类字符全部转义', () => {
    expect(escapeHtml('<a & "b">\'')).toBe('&lt;a &amp; &quot;b&quot;&gt;&#39;')
  })

  it('转义/反转义往返幂等', () => {
    const s = '<div class="x">A&B \' q</div>'
    expect(unescapeHtml(escapeHtml(s))).toBe(s)
    expect(unescapeHtml('&amp;amp;')).toBe('&amp;')
  })

  it('恶意标签原样保留为文本，不产生标签结构', () => {
    const raw = '<img src=x onerror=alert(1)>'
    const { blocks } = parseRich(raw)
    expect(runsText(blocks)).toBe(raw)
    expect(blocks[0]).not.toHaveProperty('html')
  })
})

describe('richText · 加粗', () => {
  it('**加粗**普通 → 两段 run，无星号残留', () => {
    const { blocks } = parseRich('**加粗**普通')
    expect(blocks[0].runs).toEqual([
      { text: '加粗', bold: true },
      { text: '普通', bold: false },
    ])
    expect(runsText(blocks)).not.toContain('**')
  })

  it('未闭合（非流式）按字面保留，不吞后半句', () => {
    const { blocks } = parseRich('**粗体')
    expect(blocks[0].runs).toEqual([{ text: '**粗体', bold: false }])
  })

  it('未闭合 + streaming → 收尾为加粗，不闪星号', () => {
    const { blocks } = parseRich('结论：**正在生成', { streaming: true })
    const last = blocks[0].runs[blocks[0].runs.length - 1]
    expect(last).toEqual({ text: '正在生成', bold: true })
    expect(runsText(blocks)).not.toContain('**')
  })

  it('跨行的 ** 不配对（两行各自按字面）', () => {
    const { blocks } = parseRich('**第一行\n第二行**')
    expect(flatRuns(blocks).every((r) => !r.bold)).toBe(true)
    expect(runsText(blocks)).toContain('**第一行')
    expect(runsText(blocks)).toContain('第二行**')
  })

  it('URL 内的 ** 当字面量', () => {
    const { blocks } = parseRich('https://x/**/y')
    expect(runsText(blocks)).toBe('https://x/**/y')
    expect(flatRuns(blocks).every((r) => !r.bold)).toBe(true)
  })

  it('**** 与 ** 粗 ** 都是字面量', () => {
    expect(runsText(parseRich('****').blocks)).toBe('****')
    expect(runsText(parseRich('** 粗 **').blocks)).toBe('** 粗 **')
    expect(flatRuns(parseRich('****').blocks).every((r) => !r.bold)).toBe(true)
  })

  it('**a**b**c** 两个加粗段，中间不吞字', () => {
    const { blocks } = parseRich('**a**b**c**')
    expect(blocks[0].runs).toEqual([
      { text: 'a', bold: true },
      { text: 'b', bold: false },
      { text: 'c', bold: true },
    ])
  })
})

describe('richText · 列表', () => {
  it('有序列表：marker 保留，ordered=true', () => {
    const { blocks } = parseRich('1. 高松燈\n2. 千早愛音')
    expect(blocks.map((b) => b.type)).toEqual(['li', 'li'])
    expect(blocks[0]).toMatchObject({ ordered: true, marker: '1.' })
    expect(blocks[1]).toMatchObject({ ordered: true, marker: '2.' })
    expect(blocks[0].runs[0].text).toBe('高松燈')
  })

  it('无序列表 - / * 都归一为 •', () => {
    const { blocks } = parseRich('- 甲\n* 乙')
    expect(blocks.map((b) => b.marker)).toEqual(['•', '•'])
    expect(blocks.every((b) => b.ordered === false)).toBe(true)
  })

  it('列表项内支持加粗', () => {
    const { blocks } = parseRich('1. **重要**的一行')
    expect(blocks[0].runs[0]).toEqual({ text: '重要', bold: true })
  })

  it('段落夹列表：p / li / li / p 顺序正确', () => {
    const { blocks } = parseRich('开头\n1. 一\n2. 二\n结尾')
    expect(blocks.map((b) => b.type)).toEqual(['p', 'li', 'li', 'p'])
  })

  it('列表续行（2 空格）并入上一条，不新增块', () => {
    const { blocks } = parseRich('1. 第一行\n  续行内容')
    expect(blocks).toHaveLength(1)
    expect(runsText(blocks)).toContain('续行内容')
  })
})

describe('richText · 健壮性', () => {
  it('10000 字无空格串不抛错且不丢字', () => {
    const raw = 'a'.repeat(10000)
    const { blocks } = parseRich(raw)
    expect(blocks).toHaveLength(1)
    expect(runsText(blocks).length).toBe(10000)
  })

  it('CRLF 归一、控制字符被剔除', () => {
    const { blocks } = parseRich('第一行\r\n第二\u0000行')
    expect(runsText(blocks)).toBe('第一行\n第二行')
  })

  it('超过上限按 20000 字截断', () => {
    const { blocks } = parseRich('b'.repeat(25000))
    expect(runsText(blocks).length).toBe(RICH_MAX_INPUT)
  })

  it('空输入返回空 blocks', () => {
    expect(parseRich('').blocks).toEqual([])
    expect(parseRich(null).blocks).toEqual([])
  })
})
