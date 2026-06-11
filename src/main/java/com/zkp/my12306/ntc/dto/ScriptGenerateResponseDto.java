package com.zkp.my12306.ntc.dto;

import java.util.Map;

public record ScriptGenerateResponseDto(
        String modelName,
        Map<String, Object> script,
        String rawContent,
        String traceId,
        String generationId,
        String workId) {
}
