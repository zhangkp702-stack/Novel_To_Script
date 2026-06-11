package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.ScriptMessageResponseDto;
import com.zkp.my12306.ntc.dto.ScriptRefineRequestDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ScriptRefineService {

    void validateRefineRequest(ScriptRefineRequestDto request);

    List<ScriptMessageResponseDto> listMessages(String currentUser, String workId, int chapterNumber);

    void streamRefineChapter(
            ScriptRefineRequestDto request,
            String workId,
            String generationId,
            String currentUser,
            SseEmitter emitter);
}
