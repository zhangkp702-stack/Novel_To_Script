package com.zkp.my12306.ntc.dto;

public record ScriptRefineRequestDto(
        String workId,
        String generationId,
        Integer chapterNumber,
        String instruction,
        String currentScriptContent) {
}
