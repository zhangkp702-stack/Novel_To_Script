package com.zkp.my12306.ntc.script.validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zkp.my12306.ntc.script.model.ScriptDocument;
import com.zkp.my12306.ntc.script.parse.NaturalScriptFormat;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScriptSchemaValidator {

    private static final String NATURAL_SCRIPT_FORMAT = "natural_script";

    public void validate(ScriptDocument document) {
        validate(document.root());
    }

    public void validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new ScriptSchemaValidationException("根节点必须是对象");
        }
        if (isNaturalScript(root)) {
            validateNaturalScript(root);
            return;
        }
        failIfPresent(collectChapterFragmentErrors(root));
    }

    public void validateChapterFragment(ScriptDocument document) {
        failIfPresent(collectChapterFragmentErrors(document.root()));
    }

    private boolean isNaturalScript(JsonNode root) {
        return NATURAL_SCRIPT_FORMAT.equals(root.path("format").asText());
    }

    private void validateNaturalScript(JsonNode root) {
        String content = root.path("content").asText("");
        String error = NaturalScriptFormat.validateStructure(content);
        if (error != null) {
            throw new ScriptSchemaValidationException(error);
        }
    }

    List<String> collectChapterFragmentErrors(JsonNode root) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            errors.add("根节点必须是对象");
            return errors;
        }
        requireText(root, ScriptSchemaFields.DOC_TYPE, errors);
        if (!ScriptSchemaRules.DOCUMENT_TYPE_CHAPTER_FRAGMENT.equals(textAt(root, ScriptSchemaFields.DOC_TYPE))) {
            errors.add("「文档类型」必须为「按章剧本片段」");
        }
        collectMetadataErrors(root.get(ScriptSchemaFields.META), errors);
        collectChapterInfoErrors(root.get(ScriptSchemaFields.CHAPTER_INFO), errors);
        collectSourceOverviewErrors(root.get(ScriptSchemaFields.SOURCE_OVERVIEW), errors);
        collectCharactersErrors(root.get(ScriptSchemaFields.CHARACTERS), errors);
        collectScenesErrors(root.get(ScriptSchemaFields.SCENES), errors);
        collectNotesErrors(root.get(ScriptSchemaFields.NOTES), errors);
        return errors;
    }

    private void collectMetadataErrors(JsonNode metadata, List<String> errors) {
        if (metadata == null || metadata.isNull()) {
            errors.add("缺少必填字段「元信息」");
            return;
        }
        if (!metadata.isObject()) {
            errors.add("「元信息」必须是对象");
            return;
        }
        requireText(metadata, ScriptSchemaFields.TITLE, errors, ScriptSchemaFields.META);
        requireText(metadata, ScriptSchemaFields.SOURCE_TYPE, errors, ScriptSchemaFields.META);
        requireText(metadata, ScriptSchemaFields.LANGUAGE, errors, ScriptSchemaFields.META);
    }

    private void collectChapterInfoErrors(JsonNode chapterInfo, List<String> errors) {
        if (chapterInfo == null || chapterInfo.isNull()) {
            errors.add("缺少必填字段「章节信息」");
            return;
        }
        if (!chapterInfo.isObject()) {
            errors.add("「章节信息」必须是对象");
            return;
        }
        requireText(chapterInfo, ScriptSchemaFields.CHAPTER_NUMBER, errors, ScriptSchemaFields.CHAPTER_INFO);
        requireText(chapterInfo, ScriptSchemaFields.SOURCE_TITLE, errors, ScriptSchemaFields.CHAPTER_INFO);
    }

    private void collectSourceOverviewErrors(JsonNode overview, List<String> errors) {
        if (overview == null || overview.isNull()) {
            errors.add("缺少必填字段「原文概述」");
            return;
        }
        if (!overview.isObject()) {
            errors.add("「原文概述」必须是对象");
            return;
        }
        requireText(overview, ScriptSchemaFields.SOURCE_SUMMARY, errors, ScriptSchemaFields.SOURCE_OVERVIEW);
        requireText(overview, ScriptSchemaFields.MAIN_CONFLICT, errors, ScriptSchemaFields.SOURCE_OVERVIEW);
        requireText(overview, ScriptSchemaFields.KEY_EVENTS, errors, ScriptSchemaFields.SOURCE_OVERVIEW);
    }

    private void collectCharactersErrors(JsonNode characters, List<String> errors) {
        if (characters == null || characters.isNull()) {
            errors.add("缺少必填字段「人物列表」");
            return;
        }
        if (!characters.isArray()) {
            errors.add("「人物列表」必须是数组");
            return;
        }
        if (characters.isEmpty()) {
            errors.add("「人物列表」不能为空");
            return;
        }
        for (int i = 0; i < characters.size(); i++) {
            JsonNode item = characters.get(i);
            String prefix = "人物列表[" + i + "]";
            if (!item.isObject()) {
                errors.add(prefix + " 必须是对象");
                continue;
            }
            requireText(item, ScriptSchemaFields.CHAR_ID, errors, prefix);
            requireText(item, ScriptSchemaFields.CHAR_NAME, errors, prefix);
            requireText(item, ScriptSchemaFields.CHAR_ROLE, errors, prefix);
            requireText(item, ScriptSchemaFields.CHAR_DESC, errors, prefix);
        }
    }

    private void collectScenesErrors(JsonNode scenes, List<String> errors) {
        if (scenes == null || scenes.isNull()) {
            errors.add("缺少必填字段「场景列表」");
            return;
        }
        if (!scenes.isArray()) {
            errors.add("「场景列表」必须是数组");
            return;
        }
        if (scenes.isEmpty()) {
            errors.add("「场景列表」不能为空");
            return;
        }
        for (int i = 0; i < scenes.size(); i++) {
            JsonNode item = scenes.get(i);
            String prefix = "场景列表[" + i + "]";
            if (!item.isObject()) {
                errors.add(prefix + " 必须是对象");
                continue;
            }
            requireText(item, ScriptSchemaFields.SCENE_ID, errors, prefix);
            requireText(item, ScriptSchemaFields.SCENE_TITLE, errors, prefix);
            requireText(item, ScriptSchemaFields.SCENE_HEADING, errors, prefix);
            requireText(item, ScriptSchemaFields.LOCATION, errors, prefix);
            requireText(item, ScriptSchemaFields.TIME, errors, prefix);
            requireText(item, ScriptSchemaFields.CAST, errors, prefix);
            requireText(item, ScriptSchemaFields.SCRIPT_BODY, errors, prefix);
        }
    }

    private void collectNotesErrors(JsonNode notes, List<String> errors) {
        if (notes == null || notes.isNull()) {
            errors.add("缺少必填字段「说明」");
            return;
        }
        if (!notes.isObject()) {
            errors.add("「说明」必须是对象");
            return;
        }
        requireText(notes, ScriptSchemaFields.ADAPTATION, errors, ScriptSchemaFields.NOTES);
        requireText(notes, ScriptSchemaFields.SUGGESTIONS, errors, ScriptSchemaFields.NOTES);
    }

    private void failIfPresent(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ScriptSchemaValidationException(String.join("; ", errors));
        }
    }

    private String textAt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private void requireText(JsonNode parent, String field, List<String> errors) {
        requireText(parent, field, errors, null);
    }

    private void requireText(JsonNode parent, String field, List<String> errors, String prefix) {
        JsonNode node = parent.get(field);
        String label = prefix == null ? field : prefix + "." + field;
        if (node == null || node.isNull()) {
            errors.add("缺少必填字段「" + label + "」");
            return;
        }
        if (node.isTextual()) {
            if (node.asText().isBlank()) {
                errors.add("缺少必填字段「" + label + "」");
            }
            return;
        }
        if (node.isNumber() || node.isBoolean()) {
            return;
        }
        errors.add("「" + label + "」必须是文本");
    }
}
