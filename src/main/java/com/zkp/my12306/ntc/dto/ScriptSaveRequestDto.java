package com.zkp.my12306.ntc.dto;

public record ScriptSaveRequestDto(
        String workId,
        Integer chapterNumber,
        String chapterContent,
        String scriptContent,
        String modelName,
        String traceId,
        String generationId) {
}
