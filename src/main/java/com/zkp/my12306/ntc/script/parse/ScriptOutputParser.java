package com.zkp.my12306.ntc.script.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.zkp.my12306.ntc.script.model.ScriptDocument;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScriptOutputParser {

    private static final String NATURAL_SCRIPT_FORMAT = "natural_script";
    private static final Pattern FENCE_PATTERN = Pattern.compile(
            "```(?:yaml|yml|json|markdown|md)?\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_HINT_PATTERN = Pattern.compile(
            "\\n*\\[系统提示：[^\\]]+\\]\\s*");
    private static final Pattern YAML_START_PATTERN = Pattern.compile("(?m)^\\s*文档类型\\s*[:：]");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ScriptDocument parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new ScriptOutputException("LLM 返回内容为空");
        }
        String payload = extractPayload(rawContent.trim());
        if (looksLikeStructuredPayload(payload)) {
            return new ScriptDocument(parseStructuredPayload(payload));
        }
        if (NaturalScriptFormat.looksLikeNaturalScript(payload)) {
            return wrapNaturalScript(payload);
        }
        return new ScriptDocument(parseStructuredPayload(payload));
    }

    private String extractPayload(String rawContent) {
        String payload = STOP_HINT_PATTERN.matcher(rawContent).replaceAll("");
        Matcher matcher = FENCE_PATTERN.matcher(payload);
        if (matcher.find()) {
            payload = matcher.group(1);
        }
        payload = stripTrailingFence(payload);
        Matcher startMatcher = YAML_START_PATTERN.matcher(payload);
        if (startMatcher.find()) {
            payload = payload.substring(startMatcher.start());
        }
        return payload.trim();
    }

    private static String stripTrailingFence(String payload) {
        if (payload == null) {
            return "";
        }
        return payload.replaceAll("```\\s*$", "").trim();
    }

    static boolean looksLikeStructuredPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        String normalized = payload.stripLeading();
        return normalized.startsWith("文档类型:")
                || normalized.startsWith("元信息:")
                || normalized.startsWith("document_type:")
                || normalized.startsWith("metadata:")
                || normalized.startsWith("{");
    }

    private ScriptDocument wrapNaturalScript(String payload) {
        ObjectNode root = jsonMapper.createObjectNode();
        root.put("format", NATURAL_SCRIPT_FORMAT);
        root.put("content", payload);
        return new ScriptDocument(root);
    }

    private JsonNode parseStructuredPayload(String payload) {
        try {
            return yamlMapper.readTree(payload);
        } catch (Exception yamlError) {
            try {
                return jsonMapper.readTree(payload);
            } catch (Exception jsonError) {
                throw new ScriptOutputException("无法解析 LLM 输出为 YAML 或 JSON", yamlError);
            }
        }
    }
}
