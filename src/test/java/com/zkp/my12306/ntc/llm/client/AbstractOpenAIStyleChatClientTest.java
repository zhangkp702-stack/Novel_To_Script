package com.zkp.my12306.ntc.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import com.zkp.my12306.ntc.llm.routing.ModelTarget;
import com.zkp.my12306.ntc.llm.service.ChatMessage;
import com.zkp.my12306.ntc.llm.stream.StreamAsyncExecutor;
import com.zkp.my12306.ntc.llm.stream.sse.OpenAIStyleSseParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractOpenAIStyleChatClientTest {

    @Test
    void buildRequestBody_includesGenerationParameters() throws Exception {
        AIModelProperties properties = new AIModelProperties();
        AIModelProperties.Generation generation = new AIModelProperties.Generation();
        generation.setMaxTokens(4096);
        generation.setTemperature(0.3);
        generation.setFrequencyPenalty(0.6);
        generation.setPresencePenalty(0.1);
        properties.setGeneration(generation);

        TestChatClient client = new TestChatClient(new ObjectMapper(), properties);
        String body = client.buildRequestBodyForTest("prompt", modelTarget(), false);

        JsonNode root = new ObjectMapper().readTree(body);
        assertEquals("mock-model", root.path("model").asText());
        assertEquals(false, root.path("stream").asBoolean());
        assertEquals(4096, root.path("max_tokens").asInt());
        assertEquals(0.3, root.path("temperature").asDouble(), 0.001);
        assertEquals(0.6, root.path("frequency_penalty").asDouble(), 0.001);
        assertEquals(0.1, root.path("presence_penalty").asDouble(), 0.001);
        assertTrue(root.path("messages").isArray());
    }

    private static ModelTarget modelTarget() {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("mock");
        candidate.setProvider("mock-provider");
        candidate.setModel("mock-model");

        AIModelProperties.ProviderConfig providerConfig = new AIModelProperties.ProviderConfig();
        providerConfig.setUrl("http://127.0.0.1");
        providerConfig.setEndpoints(new HashMap<>(Map.of("chat", "/v1/chat/completions")));
        return new ModelTarget("mock", candidate, providerConfig);
    }

    private static final class TestChatClient extends AbstractOpenAIStyleChatClient {
        TestChatClient(ObjectMapper objectMapper, AIModelProperties properties) {
            super(objectMapper, new OpenAIStyleSseParser(objectMapper),
                    new StreamAsyncExecutor(properties), properties);
        }

        @Override
        public String provider() {
            return "mock-provider";
        }

        String buildRequestBodyForTest(String prompt, ModelTarget modelTarget, boolean stream) throws Exception {
            Method method = AbstractOpenAIStyleChatClient.class.getDeclaredMethod(
                    "buildRequestBody", List.class, ModelTarget.class, boolean.class);
            method.setAccessible(true);
            return (String) method.invoke(
                    this,
                    List.of(new ChatMessage("user", prompt)),
                    modelTarget,
                    stream);
        }
    }
}
