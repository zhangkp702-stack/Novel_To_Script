package com.zkp.my12306.ntc.script.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class WorkTitlePromptBuilder {

    private static final String PROMPT_RESOURCE = "prompt/work_title.md";
    private static final String EXCERPT_PLACEHOLDER = "{{小说节选}}";

    private final String templateText;

    public WorkTitlePromptBuilder() {
        this.templateText = loadResource(PROMPT_RESOURCE);
    }

    public String build(String novelExcerpt) {
        String normalized = novelExcerpt == null ? "" : novelExcerpt.trim();
        return templateText.replace(EXCERPT_PLACEHOLDER, normalized);
    }

    private String loadResource(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("无法加载作品标题 prompt 资源: " + path, ex);
        }
    }
}
