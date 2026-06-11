package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.LlmTraceRunResponseDto;

import java.util.List;

public interface LlmTraceQueryService {

    LlmTraceRunResponseDto getByTraceId(String currentUser, String traceId);

    List<LlmTraceRunResponseDto> listByWorkId(String currentUser, String workId);

    LlmTraceRunResponseDto getByRecordId(String currentUser, Long recordId);
}
