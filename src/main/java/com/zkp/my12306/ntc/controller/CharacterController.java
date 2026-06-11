package com.zkp.my12306.ntc.controller;

import com.zkp.my12306.ntc.dto.CharacterCreateRequestDto;
import com.zkp.my12306.ntc.dto.CharacterUpdateRequestDto;
import com.zkp.my12306.ntc.dto.ErrorResponseDto;
import com.zkp.my12306.ntc.script.record.CharacterNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.CharacterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/works/{workId}/characters")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String workId, Authentication authentication) {
        try {
            return ResponseEntity.ok(characterService.listByWorkId(authentication.getName(), workId));
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException | CharacterNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String workId,
            @RequestBody CharacterCreateRequestDto request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(characterService.create(authentication.getName(), workId, request));
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @PutMapping("/{characterId}")
    public ResponseEntity<?> update(
            @PathVariable String workId,
            @PathVariable String characterId,
            @RequestBody CharacterUpdateRequestDto request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(characterService.update(authentication.getName(), workId, characterId, request));
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException | CharacterNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }

    @DeleteMapping("/{characterId}")
    public ResponseEntity<?> delete(
            @PathVariable String workId,
            @PathVariable String characterId,
            Authentication authentication) {
        try {
            characterService.delete(authentication.getName(), workId, characterId);
            return ResponseEntity.noContent().build();
        } catch (ScriptWorkNotFoundException | ScriptWorkAccessDeniedException | CharacterNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto(ex.getMessage()));
        } catch (ScriptRecordValidationException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }
}
