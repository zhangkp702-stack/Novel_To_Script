package com.zkp.my12306.ntc.script.parse;

public class ScriptOutputException extends RuntimeException {

    public ScriptOutputException(String message) {
        super(message);
    }

    public ScriptOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
