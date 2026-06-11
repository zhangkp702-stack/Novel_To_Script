package com.zkp.my12306.ntc.script.input;

import java.util.List;

public class ScriptValidationException extends RuntimeException {

    private final ValidationErrorCode code;
    private final Integer chapterNumber;
    private final int minChapters;
    private final int filledCount;
    private final List<Integer> invalidIndexes;

    public ScriptValidationException(
            ValidationErrorCode code,
            String message,
            Integer chapterNumber,
            int minChapters,
            int filledCount,
            List<Integer> invalidIndexes) {
        super(message);
        this.code = code;
        this.chapterNumber = chapterNumber;
        this.minChapters = minChapters;
        this.filledCount = filledCount;
        this.invalidIndexes = List.copyOf(invalidIndexes);
    }

    public Integer getChapterNumber() {
        return chapterNumber;
    }

    public ValidationErrorCode getCode() {
        return code;
    }

    public int getMinChapters() {
        return minChapters;
    }

    public int getFilledCount() {
        return filledCount;
    }

    public List<Integer> getInvalidIndexes() {
        return invalidIndexes;
    }
}
