package com.zkp.my12306.ntc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zkp.my12306.ntc.dto.CharacterCreateRequestDto;
import com.zkp.my12306.ntc.dto.CharacterResponseDto;
import com.zkp.my12306.ntc.dto.CharacterUpdateRequestDto;
import com.zkp.my12306.ntc.script.dao.entity.CharacterDO;
import com.zkp.my12306.ntc.script.dao.mapper.CharacterMapper;
import com.zkp.my12306.ntc.script.prompt.CharacterPromptItem;
import com.zkp.my12306.ntc.script.record.CharacterNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.service.CharacterService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CharacterServiceImpl implements CharacterService {

    private static final int NAME_MAX_LENGTH = 128;
    private static final int DISPLAY_NAME_MAX_LENGTH = 128;
    private static final int DESCRIPTION_MAX_LENGTH = 1000;
    private static final int PERSONALITY_MAX_LENGTH = 1000;

    private final CharacterMapper characterMapper;
    private final ScriptWorkService scriptWorkService;

    public CharacterServiceImpl(CharacterMapper characterMapper, ScriptWorkService scriptWorkService) {
        this.characterMapper = characterMapper;
        this.scriptWorkService = scriptWorkService;
    }

    @Override
    public List<CharacterResponseDto> listByWorkId(String currentUser, String workId) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        return loadCharacters(workId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CharacterPromptItem> listForPrompt(String currentUser, String workId) {
        return listByWorkId(currentUser, workId).stream()
                .map(item -> new CharacterPromptItem(
                        item.name(),
                        item.displayName(),
                        item.description(),
                        item.personality()))
                .toList();
    }

    @Override
    public CharacterResponseDto create(String currentUser, String workId, CharacterCreateRequestDto request) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        String name = requireName(request == null ? null : request.name());
        LocalDateTime now = LocalDateTime.now();
        CharacterDO character = new CharacterDO();
        character.setId(UUID.randomUUID().toString());
        character.setWorkId(workId.trim());
        character.setName(name);
        character.setDisplayName(normalizeOptional(request == null ? null : request.displayName(), DISPLAY_NAME_MAX_LENGTH));
        character.setDescription(normalizeOptional(request == null ? null : request.description(), DESCRIPTION_MAX_LENGTH));
        character.setPersonality(normalizeOptional(request == null ? null : request.personality(), PERSONALITY_MAX_LENGTH));
        character.setSortOrder(nextSortOrder(workId));
        character.setCreateTime(now);
        character.setUpdateTime(now);
        character.setDeleted(0);
        characterMapper.insert(character);
        scriptWorkService.touchWork(workId);
        return toResponse(character);
    }

    @Override
    public CharacterResponseDto update(
            String currentUser,
            String workId,
            String characterId,
            CharacterUpdateRequestDto request) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        CharacterDO character = requireOwnedCharacter(workId, characterId);
        String name = requireName(request == null ? null : request.name());
        character.setName(name);
        character.setDisplayName(normalizeOptional(request == null ? null : request.displayName(), DISPLAY_NAME_MAX_LENGTH));
        character.setDescription(normalizeOptional(request == null ? null : request.description(), DESCRIPTION_MAX_LENGTH));
        character.setPersonality(normalizeOptional(request == null ? null : request.personality(), PERSONALITY_MAX_LENGTH));
        character.setUpdateTime(LocalDateTime.now());
        characterMapper.updateById(character);
        scriptWorkService.touchWork(workId);
        return toResponse(character);
    }

    @Override
    public void delete(String currentUser, String workId, String characterId) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        CharacterDO character = requireOwnedCharacter(workId, characterId);
        characterMapper.deleteById(character.getId());
        scriptWorkService.touchWork(workId);
    }

    @Override
    public void deleteByWorkId(String workId) {
        if (workId == null || workId.isBlank()) {
            return;
        }
        characterMapper.delete(Wrappers.lambdaQuery(CharacterDO.class)
                .eq(CharacterDO::getWorkId, workId.trim()));
    }

    private List<CharacterDO> loadCharacters(String workId) {
        return characterMapper.selectList(Wrappers.lambdaQuery(CharacterDO.class)
                        .eq(CharacterDO::getWorkId, workId.trim())
                        .orderByAsc(CharacterDO::getSortOrder)
                        .orderByAsc(CharacterDO::getCreateTime))
                .stream()
                .sorted(Comparator.comparing(CharacterDO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CharacterDO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private CharacterDO requireOwnedCharacter(String workId, String characterId) {
        if (characterId == null || characterId.isBlank()) {
            throw new CharacterNotFoundException(characterId);
        }
        CharacterDO character = characterMapper.selectById(characterId.trim());
        if (character == null || !workId.trim().equals(character.getWorkId())) {
            throw new CharacterNotFoundException(characterId);
        }
        return character;
    }

    private int nextSortOrder(String workId) {
        List<CharacterDO> characters = loadCharacters(workId);
        return characters.stream()
                .map(CharacterDO::getSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ScriptRecordValidationException("人物名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new ScriptRecordValidationException("人物名称不能超过 " + NAME_MAX_LENGTH + " 字");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ScriptRecordValidationException("字段长度不能超过 " + maxLength + " 字");
        }
        return normalized;
    }

    private CharacterResponseDto toResponse(CharacterDO character) {
        return new CharacterResponseDto(
                character.getId(),
                character.getWorkId(),
                character.getName(),
                character.getDisplayName(),
                character.getDescription(),
                character.getPersonality(),
                character.getSortOrder() == null ? 0 : character.getSortOrder(),
                formatDateTime(character.getCreateTime()),
                formatDateTime(character.getUpdateTime()));
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
