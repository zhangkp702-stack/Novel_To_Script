package com.zkp.my12306.ntc.controller;

import com.zkp.my12306.ntc.dto.ErrorResponseDto;
import com.zkp.my12306.ntc.dto.LlmTraceRunResponseDto;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.LlmTraceQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/traces")
public class LlmTraceController {

    private final LlmTraceQueryService llmTraceQueryService;

    public LlmTraceController(LlmTraceQueryService llmTraceQueryService) {
        this.llmTraceQueryService = llmTraceQueryService;
    }

    @GetMapping
    public ResponseEntity<?> listByWorkId(
            @RequestParam("workId") String workId,
            Authentication authentication) {
        try {
            List<LlmTraceRunResponseDto> traces = llmTraceQueryService.listByWorkId(authentication.getName(), workId);
            return ResponseEntity.ok(traces);
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }
}
