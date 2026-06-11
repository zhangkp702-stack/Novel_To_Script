package com.zkp.my12306.ntc.script.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkTitlePromptBuilderTest {

    private final WorkTitlePromptBuilder builder = new WorkTitlePromptBuilder();

    @Test
    void build_includesNovelExcerpt() {
        String prompt = builder.build("雨夜中的旧城，林川回到了十年前离开的家。");

        assertTrue(prompt.contains("中文小说编辑"));
        assertTrue(prompt.contains("雨夜中的旧城"));
        assertTrue(prompt.contains("只输出一个书名"));
    }
}
