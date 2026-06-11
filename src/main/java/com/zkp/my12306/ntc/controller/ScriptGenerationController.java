package com.zkp.my12306.ntc.controller;

import com.zkp.my12306.ntc.dto.ErrorResponseDto;
import com.zkp.my12306.ntc.dto.ScriptGenerateRequestDto;
import com.zkp.my12306.ntc.dto.ScriptGenerateResponseDto;
import com.zkp.my12306.ntc.dto.ValidationErrorResponseDto;
import com.zkp.my12306.ntc.script.input.ScriptValidationException;
import com.zkp.my12306.ntc.script.parse.ScriptOutputException;
import com.zkp.my12306.ntc.script.validate.ScriptSchemaValidationException;
import com.zkp.my12306.ntc.service.ScriptApplicationService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/scripts")
public class ScriptGenerationController {

    private static final MediaType SSE_UTF8 = MediaType.parseMediaType("text/event-stream;charset=UTF-8");

    private final ScriptApplicationService scriptApplicationService;
    private final ScriptWorkService scriptWorkService;

    public ScriptGenerationController(
            ScriptApplicationService scriptApplicationService,
            ScriptWorkService scriptWorkService) {
        this.scriptApplicationService = scriptApplicationService;
        this.scriptWorkService = scriptWorkService;
    }

    @PostMapping(value = "/generate/stream", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<?> streamGenerate(@RequestBody ScriptGenerateRequestDto request, Authentication authentication) {
        try {
            scriptApplicationService.validateGenerateRequest(request);
        } catch (ScriptValidationException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ValidationErrorResponseDto(
                            ex.getCode().name(),
                            ex.getMessage(),
                            ex.getChapterNumber(),
                            ex.getMinChapters(),
                            ex.getFilledCount(),
                            ex.getInvalidIndexes()));
        }

        String currentUser = authentication.getName();
        String generationId = resolveGenerationId(request);
        String workId = scriptWorkService.requireWorkId(currentUser, request.workId());
        SseEmitter emitter = new SseEmitter(300_000L);
        scriptApplicationService.streamGenerateScript(request, workId, generationId, currentUser, emitter);
        return ResponseEntity.ok().contentType(SSE_UTF8).body(emitter);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody ScriptGenerateRequestDto request, Authentication authentication) {
        try {
            String currentUser = authentication.getName();
            String generationId = resolveGenerationId(request);
            String workId = scriptWorkService.requireWorkId(currentUser, request.workId());
            ScriptGenerateResponseDto response = scriptApplicationService.generateScript(
                    request, workId, generationId, currentUser);
            return ResponseEntity.ok(response);
        } catch (ScriptValidationException ex) {
            return ResponseEntity.badRequest().body(new ValidationErrorResponseDto(
                    ex.getCode().name(),
                    ex.getMessage(),
                    ex.getChapterNumber(),
                    ex.getMinChapters(),
                    ex.getFilledCount(),
                    ex.getInvalidIndexes()));
        } catch (ScriptOutputException | ScriptSchemaValidationException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponseDto(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponseDto(resolveErrorMessage(ex)));
        }
    }

    private String resolveGenerationId(ScriptGenerateRequestDto request) {
        if (request != null && request.generationId() != null && !request.generationId().isBlank()) {
            return request.generationId().trim();
        }
        return UUID.randomUUID().toString();
    }

    private String resolveErrorMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root == ex) {
            return ex.getMessage();
        }
        return ex.getMessage() + "：" + root.getMessage();
    }
}
