package com.zkp.my12306.ntc.script.parse;

import java.util.regex.Pattern;

public final class NaturalScriptFormat {

    private static final Pattern SCENE_HEADING = Pattern.compile("(?m)^场景[一二三四五六七八九十0-9]+[：:]");

    private NaturalScriptFormat() {
    }

    public static boolean looksLikeNaturalScript(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        String normalized = payload.stripLeading();
        return startsWithScriptTitle(normalized)
                || startsWithLegacyInfo(normalized)
                || payload.contains("\n剧本标题")
                || payload.contains("\n剧本信息");
    }

    static boolean startsWithScriptTitle(String normalized) {
        return normalized.startsWith("剧本标题:") || normalized.startsWith("剧本标题：");
    }

    static boolean startsWithLegacyInfo(String normalized) {
        return normalized.startsWith("剧本信息:") || normalized.startsWith("剧本信息：");
    }

    static boolean hasSceneHeading(String content) {
        return SCENE_HEADING.matcher(content).find();
    }

    public static String validateStructure(String content) {
        if (content == null || content.isBlank()) {
            return "剧本内容为空";
        }
        String normalized = content.stripLeading();
        if (!startsWithScriptTitle(normalized) && !startsWithLegacyInfo(normalized)) {
            return "剧本必须以「剧本标题：」开头";
        }
        if (!hasSceneHeading(content)) {
            return "剧本缺少场景标题（如「场景一：」）";
        }
        return null;
    }
}
