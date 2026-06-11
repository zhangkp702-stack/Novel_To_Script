package com.zkp.my12306.ntc.dto;

public record ScriptGenerateRequestDto(
        String workId,
        String generationId,
        Integer chapterNumber,
        String chapterContent) {
}
