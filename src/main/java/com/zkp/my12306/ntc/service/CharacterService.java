package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.CharacterCreateRequestDto;
import com.zkp.my12306.ntc.dto.CharacterResponseDto;
import com.zkp.my12306.ntc.dto.CharacterUpdateRequestDto;
import com.zkp.my12306.ntc.script.prompt.CharacterPromptItem;

import java.util.List;

public interface CharacterService {

    List<CharacterResponseDto> listByWorkId(String currentUser, String workId);

    List<CharacterPromptItem> listForPrompt(String currentUser, String workId);

    CharacterResponseDto create(String currentUser, String workId, CharacterCreateRequestDto request);

    CharacterResponseDto update(String currentUser, String workId, String characterId, CharacterUpdateRequestDto request);

    void delete(String currentUser, String workId, String characterId);

    void deleteByWorkId(String workId);
}
