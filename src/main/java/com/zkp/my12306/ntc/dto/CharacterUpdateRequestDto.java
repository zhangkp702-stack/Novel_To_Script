package com.zkp.my12306.ntc.dto;

public record CharacterUpdateRequestDto(
        String name,
        String displayName,
        String description,
        String personality) {
}
