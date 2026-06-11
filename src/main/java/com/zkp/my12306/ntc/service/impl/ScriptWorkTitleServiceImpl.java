package com.zkp.my12306.ntc.service.impl;

import com.zkp.my12306.ntc.dto.WorkTitleGenerateResponseDto;
import com.zkp.my12306.ntc.llm.service.ChatResult;
import com.zkp.my12306.ntc.llm.service.LLMService;
import com.zkp.my12306.ntc.script.prompt.WorkTitlePromptBuilder;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import com.zkp.my12306.ntc.service.ScriptWorkTitleService;
import org.springframework.stereotype.Service;

@Service
public class ScriptWorkTitleServiceImpl implements ScriptWorkTitleService {

    private static final int MIN_EXCERPT_LENGTH = 20;
    private static final int MAX_EXCERPT_LENGTH = 2000;
    private static final int MAX_TITLE_LENGTH = 10;

    private final LLMService llmService;
    private final WorkTitlePromptBuilder promptBuilder;
    private final ScriptWorkService scriptWorkService;

    public ScriptWorkTitleServiceImpl(
            LLMService llmService,
            WorkTitlePromptBuilder promptBuilder,
            ScriptWorkService scriptWorkService) {
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
        this.scriptWorkService = scriptWorkService;
    }

    @Override
    public WorkTitleGenerateResponseDto generateTitle(String currentUser, String workId, String novelExcerpt) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        String excerpt = normalizeExcerpt(novelExcerpt);
        String prompt = promptBuilder.build(excerpt);
        ChatResult result = llmService.chat(prompt);
        String title = sanitizeTitle(result.content());
        scriptWorkService.updateTitle(currentUser, workId, title);
        return new WorkTitleGenerateResponseDto(
                workId,
                title,
                title,
                result.modelName());
    }

    private String normalizeExcerpt(String novelExcerpt) {
        if (novelExcerpt == null || novelExcerpt.isBlank()) {
            throw new ScriptRecordValidationException("小说节选不能为空");
        }
        String normalized = novelExcerpt.trim();
        if (normalized.length() < MIN_EXCERPT_LENGTH) {
            throw new ScriptRecordValidationException("小说节选至少需要 " + MIN_EXCERPT_LENGTH + " 字");
        }
        if (normalized.length() > MAX_EXCERPT_LENGTH) {
            return normalized.substring(0, MAX_EXCERPT_LENGTH);
        }
        return normalized;
    }

    private String sanitizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ScriptRecordValidationException("模型未返回有效书名");
        }
        String title = raw.trim()
                .replace("《", "")
                .replace("》", "")
                .replace("\"", "")
                .replace("“", "")
                .replace("”", "")
                .replace("'", "")
                .split("\\R")[0]
                .trim();
        if (title.isBlank()) {
            throw new ScriptRecordValidationException("模型未返回有效书名");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }
        return title;
    }
}
