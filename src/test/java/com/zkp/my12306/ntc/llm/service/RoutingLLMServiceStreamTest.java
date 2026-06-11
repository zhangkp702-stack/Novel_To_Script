package com.zkp.my12306.ntc.llm.service;

import com.zkp.my12306.ntc.llm.client.ChatClient;
import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import com.zkp.my12306.ntc.llm.routing.ModelHealthStore;
import com.zkp.my12306.ntc.llm.routing.ModelRoutingExecutor;
import com.zkp.my12306.ntc.llm.routing.ModelSelector;
import com.zkp.my12306.ntc.llm.routing.ModelTarget;
import com.zkp.my12306.ntc.llm.stream.StreamAsyncExecutor;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandles;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingLLMServiceStreamTest {

    @Test
    void streamChat_timeoutThenFallback_onlyPushesBackupOutput() throws Exception {
        ModelTarget a = model("model-a", "provider-a", 1);
        ModelTarget b = model("model-b", "provider-b", 2);

        ChatClient clientA = new ChatClient() {
            @Override
            public String provider() {
                return "provider-a";
            }

            @Override
            public ChatResult chat(String prompt, ModelTarget modelTarget) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback) {
                callback.onOpen(modelTarget.id());
                return StreamCancellationHandles.noOp();
            }
        };

        ChatClient clientB = new ChatClient() {
            @Override
            public String provider() {
                return "provider-b";
            }

            @Override
            public ChatResult chat(String prompt, ModelTarget modelTarget) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback) {
                callback.onOpen(modelTarget.id());
                callback.onToken("backup-token");
                callback.onComplete();
                return StreamCancellationHandles.noOp();
            }
        };

        RoutingLLMService service = service(List.of(a, b), List.of(clientA, clientB), 50);
        CallbackCollector collector = new CallbackCollector();
        service.streamChat("prompt", collector);

        assertTrue(collector.awaitComplete(2, TimeUnit.SECONDS));
        assertTrue(collector.events.contains("open:model-b"));
        assertTrue(collector.events.contains("token:backup-token"));
        assertFalse(collector.events.contains("open:model-a"));
        assertFalse(collector.containsPrefix("error:"));
    }

    @Test
    void streamChat_firstModelErrorThenFallback_noIntermediateErrorExposed() throws Exception {
        ModelTarget a = model("model-a", "provider-a", 1);
        ModelTarget b = model("model-b", "provider-b", 2);

        ChatClient clientA = new ChatClient() {
            @Override
            public String provider() {
                return "provider-a";
            }

            @Override
            public ChatResult chat(String prompt, ModelTarget modelTarget) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback) {
                callback.onOpen(modelTarget.id());
                callback.onError(new IllegalStateException("A-error"));
                return StreamCancellationHandles.noOp();
            }
        };

        ChatClient clientB = new ChatClient() {
            @Override
            public String provider() {
                return "provider-b";
            }

            @Override
            public ChatResult chat(String prompt, ModelTarget modelTarget) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback) {
                callback.onOpen(modelTarget.id());
                callback.onToken("b-token");
                callback.onComplete();
                return StreamCancellationHandles.noOp();
            }
        };

        RoutingLLMService service = service(List.of(a, b), List.of(clientA, clientB), 100);
        CallbackCollector collector = new CallbackCollector();
        service.streamChat("prompt", collector);

        assertTrue(collector.awaitComplete(2, TimeUnit.SECONDS));
        assertTrue(collector.events.contains("open:model-b"));
        assertTrue(collector.events.contains("token:b-token"));
        assertFalse(collector.containsPrefix("error:"));
    }

    @Test
    void streamChat_cancelled_shouldNotPushAnyToken() throws Exception {
        ModelTarget a = model("model-a", "provider-a", 1);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ChatClient clientA = new ChatClient() {
            @Override
            public String provider() {
                return "provider-a";
            }

            @Override
            public ChatResult chat(String prompt, ModelTarget modelTarget) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback) {
                callback.onOpen(modelTarget.id());
                Thread worker = new Thread(() -> {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    if (!cancelled.get()) {
                        callback.onToken("late-token");
                        callback.onComplete();
                    }
                });
                worker.start();
                return StreamCancellationHandles.of(() -> cancelled.set(true));
            }
        };

        RoutingLLMService service = service(List.of(a), List.of(clientA), 500);
        CallbackCollector collector = new CallbackCollector();
        StreamCancellationHandle handle = service.streamChat("prompt", collector);
        handle.cancel();

        Thread.sleep(300);
        assertFalse(collector.events.stream().anyMatch(item -> item.startsWith("token:")));
        assertFalse(collector.containsPrefix("error:"));
    }

    private static ModelTarget model(String id, String provider, int priority) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel("mock-model");
        candidate.setPriority(priority);
        candidate.setEnabled(true);

        AIModelProperties.ProviderConfig providerConfig = new AIModelProperties.ProviderConfig();
        providerConfig.setUrl("http://127.0.0.1");
        providerConfig.setApiKey("mock-key");
        providerConfig.setEndpoints(new HashMap<>(Map.of("chat", "/v1/chat/completions")));

        return new ModelTarget(id, candidate, providerConfig);
    }

    private static RoutingLLMService service(List<ModelTarget> targets, List<ChatClient> clients, int firstTokenTimeoutMs) {
        AIModelProperties properties = new AIModelProperties();
        AIModelProperties.Selection selection = new AIModelProperties.Selection();
        selection.setFirstTokenTimeoutMs(firstTokenTimeoutMs);
        selection.setConnectTimeoutMs(1000);
        selection.setRequestTimeoutMs(120000);
        properties.setSelection(selection);

        ModelSelector selector = mock(ModelSelector.class);
        when(selector.selectChatCandidates()).thenReturn(targets);
        ModelHealthStore healthStore = new ModelHealthStore(properties);

        return new RoutingLLMService(selector, new ModelRoutingExecutor(healthStore), healthStore, properties, clients,
                new StreamAsyncExecutor(properties));
    }

    private static class CallbackCollector implements StreamCallback {
        private final CountDownLatch completeLatch = new CountDownLatch(1);
        private final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void onOpen(String modelName) {
            events.add("open:" + modelName);
        }

        @Override
        public void onToken(String token) {
            events.add("token:" + token);
        }

        @Override
        public void onComplete() {
            events.add("complete");
            completeLatch.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            events.add("error:" + throwable.getMessage());
        }

        private boolean awaitComplete(long timeout, TimeUnit unit) throws InterruptedException {
            return completeLatch.await(timeout, unit);
        }

        private boolean containsPrefix(String prefix) {
            return events.stream().anyMatch(item -> item.startsWith(prefix));
        }
    }
}
