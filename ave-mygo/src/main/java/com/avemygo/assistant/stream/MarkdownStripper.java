package com.avemygo.assistant.stream;

/**
 * Markdown 清洗器（小灯 v2）。
 *
 * 为什么需要：提示词已明确「不要用 Markdown」，但 qwen3:8b 仍会输出 `**id=19**`。
 * 前端按规范用纯文本渲染（不用 v-html），星号就会裸露给用户。
 * 与其反复调提示词（8B 模型不总遵守，docs/10 §17 的教训），不如在流式出口直接删掉标记字符：
 * 结构上保证「出去的文字里不可能出现 markdown 标记」。
 *
 * 策略：逐字符过滤，不做跨 chunk 缓存——`*` 直接丢弃，所以 `**` 被切成两个 chunk 也不会漏。
 * 代价：正文里合法的 `*`/`` ` `` 也会被删（本项目文案里几乎不用），换取零延迟与零漏网。
 */
public final class MarkdownStripper {

    private boolean atLineStart = true;

    /** 流式清洗：喂一段增量，返回可以安全推给前端的文本 */
    public String push(String chunk) {
        if (chunk == null || chunk.isEmpty()) return "";
        StringBuilder out = new StringBuilder(chunk.length());
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);

            // 粗体/斜体/行内代码标记：一律删除
            if (c == '*' || c == '`') continue;

            // 行首 # 标题标记与紧随其后的缩进空白：一起删除（行中的 # 保留，如「#1」曲序）
            if (atLineStart && (c == '#' || (c == ' ' || c == '\t'))) continue;

            out.append(c);
            if (c == '\n') {
                atLineStart = true;
            } else if (!Character.isWhitespace(c)) {
                atLineStart = false;
            }
        }
        return out.toString();
    }

    /** 一次性清洗（用于落库的历史与最终正文） */
    public static String strip(String text) {
        return text == null ? null : new MarkdownStripper().push(text);
    }
}
