package com.zkp.my12306.ntc.script.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record ScriptDocument(JsonNode root) {

    public Map<String, Object> toMap() {
        return ScriptDocumentMapper.toMap(root);
    }

    public String toJson() {
        return ScriptDocumentMapper.toJson(root);
    }

    public String toYaml() {
        return ScriptDocumentMapper.toYaml(root);
    }
}
