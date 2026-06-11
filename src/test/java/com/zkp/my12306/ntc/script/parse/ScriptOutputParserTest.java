package com.zkp.my12306.ntc.script.parse;

import com.zkp.my12306.ntc.script.model.ScriptDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptOutputParserTest {

    private final ScriptOutputParser parser = new ScriptOutputParser();

    @Test
    void parse_chapterFragmentYaml_returnsStructuredDocument() throws Exception {
        String yaml = new ClassPathResource("script/sample_chapter_fragment.yaml")
                .getContentAsString(StandardCharsets.UTF_8);

        ScriptDocument document = parser.parse(yaml);

        assertEquals("按章剧本片段", document.root().path("文档类型").asText());
        assertEquals("旧城雨夜", document.root().path("元信息").path("标题").asText());
    }

    @Test
    void parse_currentPromptFormat_returnsWrappedDocument() {
        String script = """
                剧本标题：《雨夜归来》
                原章节标题：第一章

                场景一：凌晨来信
                场景头：内景，卧室，凌晨

                剧本正文：
                旁白：凌晨三点，林川被手机惊醒。
                """;

        ScriptDocument document = parser.parse(script);

        assertEquals("natural_script", document.root().path("format").asText());
    }

    @Test
    void parse_fencedYaml_stripsFence() {
        String raw = """
                ```yaml
                文档类型: 按章剧本片段
                元信息:
                  标题: 测试
                  来源类型: 小说
                  语言: 简体中文
                章节信息:
                  章节编号: "1"
                  原文标题: 第一章
                原文概述:
                  原文梗概: 测试
                  主要冲突: 测试
                  关键事件: 测试
                人物列表:
                  - 编号: 人物_001
                    姓名: 甲
                    角色类型: 主角
                    简介: 测试
                场景列表:
                  - 场景编号: 场景_001
                    场景标题: 测试
                    场景头: 内景，测试，夜
                    地点: 测试
                    时间: 夜
                    出场人物: 甲
                    剧本正文: 旁白：测试
                说明:
                  改编策略: 测试
                  修改建议: 测试
                ```
                """;

        ScriptDocument document = parser.parse(raw);

        assertEquals("按章剧本片段", document.root().path("文档类型").asText());
        assertEquals("测试", document.root().path("元信息").path("标题").asText());
    }

    @Test
    void parse_emptyContent_throws() {
        ScriptOutputException ex = assertThrows(ScriptOutputException.class, () -> parser.parse("  "));
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void looksLikeStructuredPayload_detectsChineseDocumentType() {
        assertTrue(ScriptOutputParser.looksLikeStructuredPayload("文档类型: 按章剧本片段\n元信息:\n  标题: x"));
    }

    @Test
    void parse_stripsPreambleAndStopHint() {
        String raw = """
                好的，以下是改编后的 YAML：
                ```yaml
                文档类型: 按章剧本片段
                元信息:
                  标题: 测试
                  来源类型: 小说
                  语言: 简体中文
                章节信息:
                  章节编号: "1"
                  原文标题: 第一章
                原文概述:
                  原文梗概: 测试
                  主要冲突: 测试
                  关键事件: 测试
                人物列表:
                  - 编号: 人物_001
                    姓名: 甲
                    角色类型: 主角
                    简介: 测试
                场景列表:
                  - 场景编号: 场景_001
                    场景标题: 测试
                    场景头: 内景，测试，夜
                    地点: 测试
                    时间: 夜
                    出场人物: 甲
                    剧本正文: 旁白：测试
                说明:
                  改编策略: 测试
                  修改建议: 测试
                ```
                [系统提示：检测到输出退化（内容重复），已自动停止。建议换用备用模型后重试。]
                """;

        ScriptDocument document = parser.parse(raw);

        assertEquals("按章剧本片段", document.root().path("文档类型").asText());
        assertEquals("测试", document.root().path("元信息").path("标题").asText());
    }
}
