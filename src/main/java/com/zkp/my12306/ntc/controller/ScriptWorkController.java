package com.zkp.my12306.ntc.controller;

import com.zkp.my12306.ntc.dto.ErrorResponseDto;
import com.zkp.my12306.ntc.dto.ScriptWorkCreateRequestDto;
import com.zkp.my12306.ntc.dto.ScriptWorkResponseDto;
import com.zkp.my12306.ntc.dto.WorkTitleGenerateRequestDto;
import com.zkp.my12306.ntc.dto.WorkTitleGenerateResponseDto;
import com.zkp.my12306.ntc.dto.WorkTitleUpdateRequestDto;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import com.zkp.my12306.ntc.service.ScriptWorkTitleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scripts/works")
public class ScriptWorkController {

    private final ScriptWorkService scriptWorkService;
    private final ScriptWorkTitleService scriptWorkTitleService;

    public ScriptWorkController(ScriptWorkService scriptWorkService, ScriptWorkTitleService scriptWorkTitleService) {
        this.scriptWorkService = scriptWorkService;
        this.scriptWorkTitleService = scriptWorkTitleService;
    }

    @PostMapping
    public ResponseEntity<?> createWork(@RequestBody ScriptWorkCreateRequestDto request, Authentication authentication) {
        try {
            ScriptWorkResponseDto response = scriptWorkService.createWork(authentication.getName(), request);
            return ResponseEntity.ok(response);
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @PutMapping("/{workId}/title")
    public ResponseEntity<?> updateTitle(
            @PathVariable String workId,
            @RequestBody WorkTitleUpdateRequestDto request,
            Authentication authentication) {
        try {
            String title = request == null ? "" : request.title();
            ScriptWorkResponseDto response = scriptWorkService.updateTitle(
                    authentication.getName(),
                    workId,
                    title);
            return ResponseEntity.ok(response);
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/{workId}/title/generate")
    public ResponseEntity<?> generateTitle(
            @PathVariable String workId,
            @RequestBody WorkTitleGenerateRequestDto request,
            Authentication authentication) {
        try {
            WorkTitleGenerateResponseDto response = scriptWorkTitleService.generateTitle(
                    authentication.getName(),
                    workId,
                    request == null ? null : request.novelExcerpt());
            return ResponseEntity.ok(response);
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponseDto(ex.getMessage()));
        }
    }
}
