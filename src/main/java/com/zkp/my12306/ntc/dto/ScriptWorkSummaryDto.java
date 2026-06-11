package com.zkp.my12306.ntc.dto;

public record ScriptWorkSummaryDto(
        String workId,
        String workTitle,
        String displayTitle,
        Integer chapterCount,
        String createdAt,
        String lastUpdatedAt) {
}
