package com.zkp.my12306.ntc.script.input;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScriptInputValidator {

    public static final int MAX_CHAPTER_CONTENT_LENGTH = 50_000;

    public void validate(Integer chapterNumber, String chapterContent) {
        if (chapterNumber == null || chapterNumber < 1) {
            throw invalidChapterNumber(chapterNumber);
        }
        if (isBlank(chapterContent)) {
            throw emptyChapter(chapterNumber);
        }
        if (chapterContent.length() > MAX_CHAPTER_CONTENT_LENGTH) {
            throw chapterTooLong(chapterNumber);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ScriptValidationException emptyChapter(int chapterNumber) {
        return new ScriptValidationException(
                ValidationErrorCode.EMPTY_CHAPTER,
                "请先填写第 " + chapterNumber + " 章内容",
                chapterNumber,
                1,
                0,
                List.of(chapterNumber));
    }

    private ScriptValidationException invalidChapterNumber(Integer chapterNumber) {
        int index = chapterNumber == null ? 0 : chapterNumber;
        return new ScriptValidationException(
                ValidationErrorCode.INVALID_CHAPTER_NUMBER,
                "章节编号无效",
                chapterNumber,
                1,
                0,
                List.of(index));
    }

    private ScriptValidationException chapterTooLong(int chapterNumber) {
        return new ScriptValidationException(
                ValidationErrorCode.CHAPTER_TOO_LONG,
                "第 " + chapterNumber + " 章内容超过 " + MAX_CHAPTER_CONTENT_LENGTH + " 字上限",
                chapterNumber,
                1,
                1,
                List.of(chapterNumber));
    }
}
