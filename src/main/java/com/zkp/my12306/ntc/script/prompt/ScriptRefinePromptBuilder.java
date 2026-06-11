package com.zkp.my12306.ntc.script.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ScriptRefinePromptBuilder {

    private static final String PROMPT_RESOURCE = "prompt/script_refine.md";
    private static final String TITLE_PLACEHOLDER = "{{作品标题}}";
    private static final String CHAPTER_NUMBER_PLACEHOLDER = "{{章节编号}}";
    private static final String CHARACTER_SETTINGS_PLACEHOLDER = "{{人物设定}}";
    private static final String YAML_SAMPLE_PLACEHOLDER = "{{YAML样例}}";

    private final String templateText;
    private final ScriptPromptBuilder scriptPromptBuilder;

    public ScriptRefinePromptBuilder(ScriptPromptBuilder scriptPromptBuilder) {
        this.scriptPromptBuilder = scriptPromptBuilder;
        this.templateText = loadResource(PROMPT_RESOURCE);
    }

    public String buildSystemPrompt(String title, int chapterNumber, List<CharacterPromptItem> characters) {
        String resolvedTitle = title == null || title.isBlank() ? "未命名作品" : title.trim();
        return templateText
                .replace(YAML_SAMPLE_PLACEHOLDER, scriptPromptBuilder.yamlSampleText())
                .replace(TITLE_PLACEHOLDER, resolvedTitle)
                .replace(CHAPTER_NUMBER_PLACEHOLDER, String.valueOf(chapterNumber))
                .replace(CHARACTER_SETTINGS_PLACEHOLDER, scriptPromptBuilder.formatCharacterSettings(characters));
    }

    public String buildFirstUserMessage(String currentScriptContent, String instruction) {
        return "当前剧本 YAML 如下：\n"
                + currentScriptContent.trim()
                + "\n\n修改要求：\n"
                + instruction.trim()
                + "\n\n请输出修订后的完整中文 YAML。";
    }

    private String loadResource(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("无法加载剧本改编 prompt 资源: " + path, ex);
        }
    }
}
