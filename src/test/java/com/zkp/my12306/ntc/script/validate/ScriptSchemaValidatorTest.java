package com.zkp.my12306.ntc.script.validate;

import com.zkp.my12306.ntc.script.model.ScriptDocument;
import com.zkp.my12306.ntc.script.parse.ScriptOutputParser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptSchemaValidatorTest {

    private final ScriptOutputParser parser = new ScriptOutputParser();
    private final ScriptSchemaValidator validator = new ScriptSchemaValidator();

    @Test
    void validate_chapterFragmentSample_passes() throws Exception {
        String yaml = new ClassPathResource("script/sample_chapter_fragment.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        ScriptDocument document = parser.parse(yaml);

        assertDoesNotThrow(() -> validator.validateChapterFragment(document));
    }

    @Test
    void validate_naturalScript_passes() {
        String script = """
                剧本标题：《雨夜归来》
                原章节标题：第一章

                场景一：凌晨来信
                场景头：内景，卧室，凌晨

                剧本正文：
                旁白：凌晨三点，林川被手机惊醒。
                """;
        ScriptDocument document = parser.parse(script);

        assertDoesNotThrow(() -> validator.validate(document));
    }

    @Test
    void validate_chapterFragmentMissingDocumentType_fails() {
        String yaml = """
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
                """;
        ScriptDocument document = parser.parse(yaml);

        ScriptSchemaValidationException ex = assertThrows(
                ScriptSchemaValidationException.class,
                () -> validator.validateChapterFragment(document));
        assertTrue(ex.getMessage().contains("文档类型"));
    }

    @Test
    void validate_missingMetadataTitle_fails() {
        String yaml = """
                文档类型: 按章剧本片段
                元信息:
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
                """;
        ScriptDocument document = parser.parse(yaml);

        ScriptSchemaValidationException ex = assertThrows(
                ScriptSchemaValidationException.class,
                () -> validator.validate(document));
        assertTrue(ex.getMessage().contains("标题"));
    }
}
