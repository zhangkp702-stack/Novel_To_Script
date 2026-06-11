package com.zkp.my12306.ntc.script.record;

public class ScriptWorkNotFoundException extends RuntimeException {

    public ScriptWorkNotFoundException(String workId) {
        super("作品不存在：" + workId);
    }
}
