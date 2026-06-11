package com.zkp.my12306.ntc.script.record;

public class ScriptWorkAccessDeniedException extends RuntimeException {

    public ScriptWorkAccessDeniedException(String workId) {
        super("无权访问作品：" + workId);
    }
}
