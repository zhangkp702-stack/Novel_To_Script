package com.zkp.my12306.ntc.script.record;

public class ScriptRecordAccessDeniedException extends RuntimeException {

    public ScriptRecordAccessDeniedException(Long id) {
        super("无权访问剧本记录：" + id);
    }
}
