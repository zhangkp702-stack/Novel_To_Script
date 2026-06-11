package com.zkp.my12306.ntc.script.record;

public class ScriptRecordNotFoundException extends RuntimeException {

    private static final String UNTITLED_WORK_LABEL = "未命名作品";

    public ScriptRecordNotFoundException(Long id) {
        super("剧本记录不存在：" + id);
    }

    public ScriptRecordNotFoundException(String workTitle) {
        super("作品不存在：" + toDisplayTitle(workTitle));
    }

    private static String toDisplayTitle(String workTitle) {
        if (workTitle == null || workTitle.isBlank()) {
            return UNTITLED_WORK_LABEL;
        }
        return workTitle;
    }
}
