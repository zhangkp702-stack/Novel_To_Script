package com.zkp.my12306.ntc.script.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.util.Map;

final class ScriptDocumentMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(
            YAMLFactory.builder()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                    .disable(YAMLGenerator.Feature.SPLIT_LINES)
                    .build());

    private ScriptDocumentMapper() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(JsonNode root) {
        return JSON.convertValue(root, Map.class);
    }

    static String toJson(JsonNode root) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize script document to JSON", ex);
        }
    }

    static String toYaml(JsonNode root) {
        try {
            return YAML.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize script document to YAML", ex);
        }
    }
}
