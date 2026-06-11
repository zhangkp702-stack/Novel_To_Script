package com.zkp.my12306.ntc.dto;

public record LlmTraceNodeResponseDto(
        String nodeId,
        String parentNodeId,
        Integer depth,
        String nodeType,
        String nodeName,
        String status,
        String errorMessage,
        String startTime,
        String endTime,
        Long durationMs) {
}
