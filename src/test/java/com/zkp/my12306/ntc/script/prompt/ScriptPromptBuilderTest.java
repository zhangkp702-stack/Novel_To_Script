package com.zkp.my12306.ntc.script.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptPromptBuilderTest {

    private final ScriptPromptBuilder builder = new ScriptPromptBuilder();

    @Test
    void build_includesOriginalAdaptationRulesAndYamlSample() {
        String prompt = builder.build("旧城雨夜", 2, "第二章小说内容");

        assertTrue(prompt.contains("不得只挑选几个高潮片段"));
        assertTrue(prompt.contains("旁白必须以"));
        assertTrue(prompt.contains("文档类型: 按章剧本片段"));
        assertTrue(prompt.contains("剧本正文"));
        assertTrue(prompt.contains("文档类型: \"按章剧本片段\""));
        assertTrue(prompt.contains("作品标题：旧城雨夜"));
        assertTrue(prompt.contains("【第 2 章】"));
    }

    @Test
    void build_scalesMinimumScriptLengthWithSourceText() {
        String longChapter = "章".repeat(2000);
        String prompt = builder.build("长篇", 1, longChapter);

        assertTrue(prompt.contains("剧本正文最少：1300 字"));
    }

    @Test
    void buildWholeBook_embedsSameYamlSample() {
        String prompt = builder.buildWholeBook("北湾秘密", List.of(
                new ChapterPromptItem(1, "第一章", "第一章正文")),
                List.of());

        assertTrue(prompt.contains("文档类型: \"按章剧本片段\""));
        assertTrue(prompt.contains("章节总数：1"));
    }
}
