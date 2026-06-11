package com.zkp.my12306.ntc.dto;

import java.util.List;

public record ValidationErrorResponseDto(
        String code,
        String message,
        Integer chapterNumber,
        Integer minChapters,
        Integer filledCount,
        List<Integer> invalidIndexes) {
}
