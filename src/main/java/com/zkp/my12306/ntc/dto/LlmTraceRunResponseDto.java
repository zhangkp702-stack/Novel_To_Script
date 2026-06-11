package com.zkp.my12306.ntc.dto;

import java.util.List;

public record LlmTraceRunResponseDto(
        String traceId,
        String traceName,
        String conversationId,
        String taskId,
        String userId,
        String status,
        String errorMessage,
        String startTime,
        String endTime,
        Long durationMs,
        List<LlmTraceNodeResponseDto> nodes) {
}
