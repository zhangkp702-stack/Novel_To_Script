package com.zkp.my12306.ntc.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import com.zkp.my12306.ntc.llm.enums.ModelCapability;
import com.zkp.my12306.ntc.llm.http.ModelUrlResolver;
import com.zkp.my12306.ntc.llm.routing.ModelTarget;
import com.zkp.my12306.ntc.llm.service.ChatMessage;
import com.zkp.my12306.ntc.llm.service.ChatResult;
import com.zkp.my12306.ntc.llm.stream.StreamAsyncExecutor;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandles;
import com.zkp.my12306.ntc.llm.stream.sse.OpenAIStyleSseParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractOpenAIStyleChatClient implements ChatClient {
    private final ObjectMapper objectMapper;
    private final OpenAIStyleSseParser openAIStyleSseParser;
    private final StreamAsyncExecutor streamAsyncExecutor;
    private final HttpClient httpClient;
    private final int requestTimeoutMs;
    private final AIModelProperties.Generation generation;

    protected AbstractOpenAIStyleChatClient(
            ObjectMapper objectMapper,
            OpenAIStyleSseParser openAIStyleSseParser,
            StreamAsyncExecutor streamAsyncExecutor,
            AIModelProperties aiModelProperties) {
        this.objectMapper = objectMapper;
        this.openAIStyleSseParser = openAIStyleSseParser;
        this.streamAsyncExecutor = streamAsyncExecutor;
        this.requestTimeoutMs = aiModelProperties.getSelection().getRequestTimeoutMs();
        this.generation = aiModelProperties.getGeneration();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(aiModelProperties.getSelection().getConnectTimeoutMs()))
                .build();
    }

    @Override
    public ChatResult chat(String prompt, ModelTarget modelTarget) {
        return chat(List.of(new ChatMessage("user", prompt)), modelTarget);
    }

    @Override
    public ChatResult chat(List<ChatMessage> messages, ModelTarget modelTarget) {
        try {
            String body = buildRequestBody(messages, modelTarget, false);
            HttpRequest request = buildRequest(modelTarget, body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "模型请求失败，provider=" + provider()
                                + ", status=" + response.statusCode()
                                + ", body=" + abbreviate(response.body()));
            }
            return parseChatResponse(response.body(), modelTarget.id());
        } catch (Exception ex) {
            throw new IllegalStateException("模型调用失败，provider=" + provider(), ex);
        }
    }

    @Override
    public StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback) {
        return streamChat(List.of(new ChatMessage("user", prompt)), modelTarget, callback);
    }

    @Override
    public StreamCancellationHandle streamChat(List<ChatMessage> messages, ModelTarget modelTarget, StreamCallback callback) {
        try {
            callback.onOpen(modelTarget.id());
            String body = buildRequestBody(messages, modelTarget, true);
            HttpRequest request = buildRequest(modelTarget, body);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            AtomicReference<CompletableFuture<?>> requestFutureRef = new AtomicReference<>();
            AtomicReference<CompletableFuture<?>> parseFutureRef = new AtomicReference<>();
            AtomicReference<InputStream> bodyStreamRef = new AtomicReference<>();
            CompletableFuture<HttpResponse<InputStream>> requestFuture =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            requestFutureRef.set(requestFuture);
            CompletableFuture<Void> parseFuture = requestFuture.thenCompose(response -> {
                if (cancelled.get()) {
                    return CompletableFuture.completedFuture(null);
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = readErrorBody(response.body());
                    callback.onError(new IllegalStateException(
                            "流式请求失败，status=" + response.statusCode() + ", body=" + abbreviate(errorBody)));
                    return CompletableFuture.completedFuture(null);
                }
                InputStream stream = response.body();
                bodyStreamRef.set(stream);
                CompletableFuture<Void> asyncParse = streamAsyncExecutor.execute(() -> parseStream(stream, callback));
                parseFutureRef.set(asyncParse);
                return asyncParse;
            });
            parseFutureRef.set(parseFuture);
            return StreamCancellationHandles.of(() -> {
                cancelled.set(true);
                CompletableFuture<?> rf = requestFutureRef.get();
                if (rf != null) {
                    rf.cancel(true);
                }
                CompletableFuture<?> pf = parseFutureRef.get();
                if (pf != null) {
                    pf.cancel(true);
                }
                InputStream stream = bodyStreamRef.getAndSet(null);
                if (stream != null) {
                    closeQuietly(stream);
                }
            });
        } catch (Exception ex) {
            callback.onError(ex);
            return StreamCancellationHandles.noOp();
        }
    }

    private HttpRequest buildRequest(ModelTarget modelTarget, String body) {
        String url = ModelUrlResolver.resolveUrl(modelTarget.provider(), modelTarget.candidate(), ModelCapability.CHAT);
        String apiKey = modelTarget.provider().getApiKey();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private String buildRequestBody(List<ChatMessage> messages, ModelTarget modelTarget, boolean stream) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelTarget.candidate().getModel());
        payload.put("stream", stream);
        payload.put("messages", toMessagePayload(messages));
        if (generation.getMaxTokens() != null) {
            payload.put("max_tokens", generation.getMaxTokens());
        }
        if (generation.getTemperature() != null) {
            payload.put("temperature", generation.getTemperature());
        }
        if (generation.getFrequencyPenalty() != null) {
            payload.put("frequency_penalty", generation.getFrequencyPenalty());
        }
        if (generation.getPresencePenalty() != null) {
            payload.put("presence_penalty", generation.getPresencePenalty());
        }
        applyProviderOptions(payload, modelTarget.candidate().getModel());
        return objectMapper.writeValueAsString(payload);
    }

    private void applyProviderOptions(Map<String, Object> payload, String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        if (requiresDisableThinking(model)) {
            payload.put("enable_thinking", false);
        }
    }

    private boolean requiresDisableThinking(String model) {
        return model.startsWith("deepseek-ai/DeepSeek-V3")
                || model.startsWith("deepseek-ai/DeepSeek-V4");
    }

    private List<Map<String, String>> toMessagePayload(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of(Map.of("role", "user", "content", ""));
        }
        return messages.stream()
                .map(message -> Map.of(
                        "role", normalizeRole(message.role()),
                        "content", message.content() == null ? "" : message.content()))
                .toList();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        return role.trim().toLowerCase();
    }

    private String readErrorBody(InputStream body) {
        if (body == null) {
            return "";
        }
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private ChatResult parseChatResponse(String responseBody, String modelName) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        return new ChatResult(content, modelName);
    }

    private void parseStream(InputStream body, StreamCallback callback) {
        try {
            openAIStyleSseParser.parse(body, callback);
        } catch (Exception ex) {
            callback.onError(ex);
        } finally {
            closeQuietly(body);
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private void closeQuietly(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException ignore) {
        }
    }
}
