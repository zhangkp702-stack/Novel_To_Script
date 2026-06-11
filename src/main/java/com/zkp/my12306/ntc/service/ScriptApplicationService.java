package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.ScriptGenerateRequestDto;
import com.zkp.my12306.ntc.dto.ScriptGenerateResponseDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ScriptApplicationService {

    ScriptGenerateResponseDto generateScript(
            ScriptGenerateRequestDto request,
            String workId,
            String generationId,
            String currentUser);

    void validateGenerateRequest(ScriptGenerateRequestDto request);

    void streamGenerateScript(
            ScriptGenerateRequestDto request,
            String workId,
            String generationId,
            String currentUser,
            SseEmitter emitter);
}
