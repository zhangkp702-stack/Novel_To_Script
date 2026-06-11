package com.zkp.my12306.ntc.controller;

import com.zkp.my12306.ntc.dto.ErrorResponseDto;
import com.zkp.my12306.ntc.dto.LlmTraceRunResponseDto;
import com.zkp.my12306.ntc.dto.ScriptRecordResponseDto;
import com.zkp.my12306.ntc.dto.ScriptSaveRequestDto;
import com.zkp.my12306.ntc.dto.ScriptWorkSummaryDto;
import com.zkp.my12306.ntc.script.record.ScriptRecordAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptRecordNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.LlmTraceQueryService;
import com.zkp.my12306.ntc.service.ScriptRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scripts")
public class ScriptRecordController {

    private final ScriptRecordService scriptRecordService;
    private final LlmTraceQueryService llmTraceQueryService;

    public ScriptRecordController(ScriptRecordService scriptRecordService, LlmTraceQueryService llmTraceQueryService) {
        this.scriptRecordService = scriptRecordService;
        this.llmTraceQueryService = llmTraceQueryService;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody ScriptSaveRequestDto request, Authentication authentication) {
        try {
            ScriptRecordResponseDto response = scriptRecordService.save(authentication.getName(), request);
            return ResponseEntity.ok(response);
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @GetMapping("/works")
    public ResponseEntity<?> listWorks(Authentication authentication) {
        try {
            List<ScriptWorkSummaryDto> works = scriptRecordService.listWorks(authentication.getName());
            return ResponseEntity.ok(works);
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @DeleteMapping("/works")
    public ResponseEntity<?> deleteWork(
            @RequestParam("workId") String workId,
            Authentication authentication) {
        try {
            scriptRecordService.deleteWork(authentication.getName(), workId);
            return ResponseEntity.noContent().build();
        } catch (ScriptRecordNotFoundException | ScriptWorkNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam("workId") String workId,
            Authentication authentication) {
        try {
            List<ScriptRecordResponseDto> records = scriptRecordService.listByWorkId(
                    authentication.getName(),
                    workId);
            return ResponseEntity.ok(records);
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication authentication) {
        try {
            ScriptRecordResponseDto response = scriptRecordService.getById(authentication.getName(), id);
            return ResponseEntity.ok(response);
        } catch (ScriptRecordNotFoundException | ScriptRecordAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @GetMapping("/{id}/trace")
    public ResponseEntity<?> getTraceByRecordId(@PathVariable Long id, Authentication authentication) {
        try {
            LlmTraceRunResponseDto response = llmTraceQueryService.getByRecordId(authentication.getName(), id);
            return ResponseEntity.ok(response);
        } catch (ScriptRecordNotFoundException | ScriptRecordAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }
}
