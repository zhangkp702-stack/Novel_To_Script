package com.zkp.my12306.ntc.dto;

public record CharacterResponseDto(
        String id,
        String workId,
        String name,
        String displayName,
        String description,
        String personality,
        int sortOrder,
        String createTime,
        String updateTime) {
}
