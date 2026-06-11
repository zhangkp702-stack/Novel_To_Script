package com.zkp.my12306.ntc.controller;

import com.zkp.my12306.ntc.dto.ErrorResponseDto;
import com.zkp.my12306.ntc.dto.ScriptRefineRequestDto;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.ScriptRefineService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/scripts")
public class ScriptRefineController {

    private static final MediaType SSE_UTF8 = MediaType.parseMediaType("text/event-stream;charset=UTF-8");

    private final ScriptRefineService scriptRefineService;
    private final ScriptWorkService scriptWorkService;

    public ScriptRefineController(ScriptRefineService scriptRefineService, ScriptWorkService scriptWorkService) {
        this.scriptRefineService = scriptRefineService;
        this.scriptWorkService = scriptWorkService;
    }

    @GetMapping("/messages")
    public ResponseEntity<?> listMessages(
            @RequestParam("workId") String workId,
            @RequestParam("chapterNumber") Integer chapterNumber,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(scriptRefineService.listMessages(
                    authentication.getName(),
                    workId,
                    chapterNumber));
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @PostMapping(value = "/refine/stream", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<?> streamRefine(@RequestBody ScriptRefineRequestDto request, Authentication authentication) {
        try {
            scriptRefineService.validateRefineRequest(request);
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponseDto(ex.getMessage()));
        }

        String currentUser = authentication.getName();
        String generationId = resolveGenerationId(request);
        String workId = scriptWorkService.requireWorkId(currentUser, request.workId());
        SseEmitter emitter = new SseEmitter(300_000L);
        scriptRefineService.streamRefineChapter(request, workId, generationId, currentUser, emitter);
        return ResponseEntity.ok().contentType(SSE_UTF8).body(emitter);
    }

    private String resolveGenerationId(ScriptRefineRequestDto request) {
        if (request != null && request.generationId() != null && !request.generationId().isBlank()) {
            return request.generationId().trim();
        }
        return UUID.randomUUID().toString();
    }
}
