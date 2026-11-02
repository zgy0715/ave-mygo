package com.avemygo.assistant.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Markdown 清洗器测试：确保出去的文本不含 markdown 标记。 */
class MarkdownStripperTest {

    @Test
    @DisplayName("S01 粗体标记被删除（真实踩到的例子）")
    void s01_bold() {
        assertThat(MarkdownStripper.strip("1. **id=19** 《MyGO!!!!! 9th LIVE》"))
                .isEqualTo("1. id=19 《MyGO!!!!! 9th LIVE》");
    }

    @Test
    @DisplayName("S02 行内代码与标题标记被删除")
    void s02_codeAndHeading() {
        assertThat(MarkdownStripper.strip("### 演出列表")).isEqualTo("演出列表");
        assertThat(MarkdownStripper.strip("可以用 `search_songs` 查")).isEqualTo("可以用 search_songs 查");
    }

    @Test
    @DisplayName("S03 跨 chunk 的 ** 不会漏（逐字符过滤的意义）")
    void s03_splitAcrossChunks() {
        MarkdownStripper stripper = new MarkdownStripper();
        String out = stripper.push("1. *") + stripper.push("*id") + stripper.push("=19*") + stripper.push("*");
        assertThat(out).isEqualTo("1. id=19");
        assertThat(out).doesNotContain("*");
    }

    @Test
    @DisplayName("S04 不误伤：URL 下划线、曲序 #1、普通标点保留")
    void s04_keepNormal() {
        assertThat(MarkdownStripper.strip("https://example.com/a_b_c"))
                .isEqualTo("https://example.com/a_b_c");
        assertThat(MarkdownStripper.strip("MYGO #1")).isEqualTo("MYGO #1");
        assertThat(MarkdownStripper.strip("《迷星叫》——收录于《迷跡波》。"))
                .isEqualTo("《迷星叫》——收录于《迷跡波》。");
    }

    @Test
    @DisplayName("S05 换行后行首 # 仍被识别为标题标记")
    void s05_headingAfterNewline() {
        assertThat(MarkdownStripper.strip("列表：\n## 演出\n- 一场"))
                .isEqualTo("列表：\n演出\n- 一场");
    }
}
