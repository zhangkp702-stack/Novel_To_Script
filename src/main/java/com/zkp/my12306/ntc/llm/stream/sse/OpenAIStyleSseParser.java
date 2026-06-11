package com.zkp.my12306.ntc.llm.stream.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class OpenAIStyleSseParser {
    private static final String DONE_MARKER = "[DONE]";

    private final ObjectMapper objectMapper;

    public OpenAIStyleSseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void parse(InputStream inputStream, StreamCallback callback) throws IOException {
        boolean[] receivedDone = {false};
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder eventData = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (eventData.length() > 0) {
                        if (processEventData(eventData.toString(), callback, receivedDone)) {
                            return;
                        }
                        eventData.setLength(0);
                    }
                    continue;
                }
                if (line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String dataLine = line.substring(5).trim();
                if (eventData.length() > 0) {
                    eventData.append('\n');
                }
                eventData.append(dataLine);
            }
            if (eventData.length() > 0) {
                if (processEventData(eventData.toString(), callback, receivedDone)) {
                    return;
                }
            }
        }
        if (!receivedDone[0]) {
            callback.onError(new IllegalStateException("模型流式连接提前结束，未收到完整响应"));
            return;
        }
        callback.onComplete();
    }

    private boolean processEventData(String data, StreamCallback callback, boolean[] receivedDone) throws IOException {
        if (data.isEmpty()) {
            return false;
        }
        if (DONE_MARKER.equals(data)) {
            receivedDone[0] = true;
            callback.onComplete();
            return true;
        }
        JsonNode root = objectMapper.readTree(data);
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText("模型返回错误");
            throw new IOException(message);
        }
        JsonNode choices0 = root.path("choices").path(0);
        JsonNode content = choices0.path("delta").path("content");
        if (content.isMissingNode() || content.isNull()) {
            content = choices0.path("message").path("content");
        }
        if (content.isMissingNode() || content.isNull()) {
            content = choices0.path("text");
        }
        boolean emitted = false;
        if (!content.isMissingNode() && !content.isNull()) {
            String token = content.asText("");
            if (!token.isEmpty()) {
                callback.onToken(token);
                emitted = true;
            }
        }
        if (!emitted) {
            JsonNode reasoning = choices0.path("delta").path("reasoning_content");
            if (!reasoning.isMissingNode() && !reasoning.isNull() && !reasoning.asText("").isEmpty()) {
                callback.onStreamActivity();
            }
        }
        return false;
    }
}
