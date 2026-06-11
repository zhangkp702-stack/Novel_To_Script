package com.zkp.my12306.ntc.script.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptInputValidatorTest {

    private final ScriptInputValidator validator = new ScriptInputValidator();

    @Test
    void validate_singleChapter_passes() {
        assertDoesNotThrow(() -> validator.validate(1, "第一章内容"));
    }

    @Test
    void validate_emptyChapter_throwsEmptyChapter() {
        ScriptValidationException ex = assertThrows(
                ScriptValidationException.class,
                () -> validator.validate(2, "  "));
        assertEquals(ValidationErrorCode.EMPTY_CHAPTER, ex.getCode());
        assertEquals(2, ex.getChapterNumber());
        assertEquals(2, ex.getInvalidIndexes().get(0));
    }

    @Test
    void validate_invalidChapterNumber_throwsInvalidNumber() {
        ScriptValidationException ex = assertThrows(
                ScriptValidationException.class,
                () -> validator.validate(0, "内容"));
        assertEquals(ValidationErrorCode.INVALID_CHAPTER_NUMBER, ex.getCode());
    }

    @Test
    void validate_tooLongChapter_throwsChapterTooLong() {
        String content = "a".repeat(ScriptInputValidator.MAX_CHAPTER_CONTENT_LENGTH + 1);
        ScriptValidationException ex = assertThrows(
                ScriptValidationException.class,
                () -> validator.validate(3, content));
        assertEquals(ValidationErrorCode.CHAPTER_TOO_LONG, ex.getCode());
        assertEquals(3, ex.getChapterNumber());
    }
}
