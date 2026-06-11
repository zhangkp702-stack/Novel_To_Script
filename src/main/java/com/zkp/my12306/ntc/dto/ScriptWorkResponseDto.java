package com.zkp.my12306.ntc.dto;

public record ScriptWorkResponseDto(
        String workId,
        String title,
        String displayTitle,
        String createdAt,
        String updatedAt) {
}
