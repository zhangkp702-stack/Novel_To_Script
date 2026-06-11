package com.zkp.my12306.ntc.dto;

public record ScriptRecordResponseDto(
        Long id,
        String workId,
        String workTitle,
        Integer chapterNumber,
        String chapterContent,
        String scriptContent,
        String modelName,
        String traceId,
        String generationId,
        String createdAt,
        String updatedAt) {
}
