package com.zkp.my12306.ntc.dto;

public record ScriptMessageResponseDto(
        String id,
        String workId,
        Integer chapterNumber,
        String role,
        String content,
        String traceId,
        String createdAt) {
}
