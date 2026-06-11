package com.zkp.my12306.ntc.llm.service;

import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceNodeDO;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceRunDO;

import java.time.LocalDateTime;

/**
 * LLM Trace 记录服务。
 */
public interface LlmTraceRecordService {

    void startRun(LlmTraceRunDO run);

    void finishRun(String traceId, String status, String errorMessage, LocalDateTime endTime, long durationMs);

    void startNode(LlmTraceNodeDO node);

    void finishNode(
            String traceId,
            String nodeId,
            String status,
            String errorMessage,
            LocalDateTime endTime,
            long durationMs);
}
