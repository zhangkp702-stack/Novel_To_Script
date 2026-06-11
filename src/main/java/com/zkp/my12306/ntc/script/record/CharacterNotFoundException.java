package com.zkp.my12306.ntc.script.record;

public class CharacterNotFoundException extends RuntimeException {

    public CharacterNotFoundException(String characterId) {
        super("人物不存在：" + characterId);
    }
}
