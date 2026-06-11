package com.zkp.my12306.ntc.llm.service;

import com.zkp.my12306.ntc.llm.client.ChatClient;
import com.zkp.my12306.ntc.llm.service.ChatMessage;
import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import com.zkp.my12306.ntc.llm.routing.ModelHealthStore;
import com.zkp.my12306.ntc.llm.routing.ModelRoutingExecutor;
import com.zkp.my12306.ntc.llm.routing.ModelSelector;
import com.zkp.my12306.ntc.llm.routing.ModelTarget;
import com.zkp.my12306.ntc.llm.stream.BufferedProbeCallback;
import com.zkp.my12306.ntc.llm.stream.ProbeStreamBridge;
import com.zkp.my12306.ntc.llm.stream.StreamAsyncExecutor;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandles;
import com.zkp.my12306.ntc.llm.trace.TraceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RoutingLLMService implements LLMService {
    private static final Logger log = LoggerFactory.getLogger(RoutingLLMService.class);

    private final ModelSelector modelSelector;
    private final ModelRoutingExecutor modelRoutingExecutor;
    private final ModelHealthStore healthStore;
    private final AIModelProperties aiModelProperties;
    private final Map<String, ChatClient> chatClientMap;
    private final StreamAsyncExecutor streamAsyncExecutor;

    public RoutingLLMService(
            ModelSelector modelSelector,
            ModelRoutingExecutor modelRoutingExecutor,
            ModelHealthStore healthStore,
            AIModelProperties aiModelProperties,
            List<ChatClient> chatClients,
            StreamAsyncExecutor streamAsyncExecutor) {
        this.modelSelector = modelSelector;
        this.modelRoutingExecutor = modelRoutingExecutor;
        this.healthStore = healthStore;
        this.aiModelProperties = aiModelProperties;
        this.streamAsyncExecutor = streamAsyncExecutor;
        this.chatClientMap = new ConcurrentHashMap<>();
        for (ChatClient chatClient : chatClients) {
            this.chatClientMap.put(chatClient.provider(), chatClient);
        }
    }

    @Override
    @TraceNode(name = "llmSyncChat", type = "LLM")
    public ChatResult chat(String prompt) {
        return chat(List.of(new ChatMessage("user", prompt)));
    }

    @Override
    @TraceNode(name = "llmSyncChat", type = "LLM")
    public ChatResult chat(List<ChatMessage> messages) {
        List<ModelTarget> targets = modelSelector.selectChatCandidates();
        if (targets.isEmpty()) {
            throw new IllegalStateException("未找到可用的大模型配置");
        }
        return modelRoutingExecutor.executeChat(messages, targets, chatClientMap);
    }

    @Override
    @TraceNode(name = "llmStreamChat", type = "LLM")
    public StreamCancellationHandle streamChat(String prompt, StreamCallback callback) {
        return streamChat(List.of(new ChatMessage("user", prompt)), callback);
    }

    @Override
    @TraceNode(name = "llmStreamChat", type = "LLM")
    public StreamCancellationHandle streamChat(List<ChatMessage> messages, StreamCallback callback) {
        List<ModelTarget> targets = modelSelector.selectChatCandidates();
        if (targets.isEmpty()) {
            throw new IllegalStateException("未找到可用的大模型配置");
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<StreamCancellationHandle> activeDelegate = new AtomicReference<>(StreamCancellationHandles.noOp());
        streamAsyncExecutor.execute(() -> {
            RuntimeException lastException = null;
            for (ModelTarget target : targets) {
                if (cancelled.get()) {
                    return;
                }
                ChatClient chatClient = chatClientMap.get(target.candidate().getProvider());
                if (chatClient == null) {
                    continue;
                }
                if (!healthStore.allowCall(target.id())) {
                    log.warn("模型熔断中，跳过流式调用: modelId={}", target.id());
                    continue;
                }
                long timeoutMs = aiModelProperties.getSelection().getFirstTokenTimeoutMs();
                log.info("开始流式调用大模型: modelId={}, provider={}, 首包超时={}ms",
                        target.id(), target.candidate().getProvider(), timeoutMs);
                AtomicBoolean attemptActive = new AtomicBoolean(true);
                BufferedProbeCallback bufferedCallback = null;
                try {
                    StreamCallback guardedCallback = createGuardedCallback(callback, cancelled, attemptActive);
                    bufferedCallback = new BufferedProbeCallback(guardedCallback);
                    ProbeStreamBridge bridge = new ProbeStreamBridge(bufferedCallback);
                    StreamCancellationHandle delegate = chatClient.streamChat(messages, target, bridge);
                    activeDelegate.set(delegate);
                    ProbeStreamBridge.FirstEvent firstEvent = bridge.awaitFirstEvent(timeoutMs);
                    if (firstEvent.type() == ProbeStreamBridge.FirstEventType.TOKEN) {
                        log.info("流式首包已返回，开始推送: modelId={}", target.id());
                        bufferedCallback.promoteAndFlush();
                        healthStore.markSuccess(target.id());
                        return;
                    }
                    if (firstEvent.type() == ProbeStreamBridge.FirstEventType.COMPLETE) {
                        attemptActive.set(false);
                        delegate.cancel();
                        bufferedCallback.clearBuffer();
                        healthStore.markFailure(target.id());
                        lastException = new IllegalStateException("流式模型返回空内容：" + target.id());
                        log.warn("流式模型未返回有效内容，尝试下一个: modelId={}", target.id());
                        continue;
                    }
                    attemptActive.set(false);
                    delegate.cancel();
                    bufferedCallback.clearBuffer();
                    healthStore.markFailure(target.id());
                    if (firstEvent.type() == ProbeStreamBridge.FirstEventType.ERROR) {
                        Throwable error = bufferedCallback.getErrorBeforePromote();
                        Throwable cause = error == null ? firstEvent.throwable() : error;
                        lastException = new IllegalStateException("流式模型调用失败：" + target.id(), cause);
                    } else {
                        lastException = new IllegalStateException("首包超时，已切换备用模型：" + target.id());
                    }
                    log.warn("流式模型调用失败，尝试下一个: modelId={}", target.id(), lastException);
                } catch (Exception ex) {
                    attemptActive.set(false);
                    if (bufferedCallback != null) {
                        bufferedCallback.clearBuffer();
                    }
                    healthStore.markFailure(target.id());
                    lastException = new IllegalStateException("流式模型调用失败：" + target.id(), ex);
                    log.warn("流式模型调用异常，尝试下一个: modelId={}", target.id(), ex);
                }
            }
            if (!cancelled.get()) {
                callback.onError(lastException == null ? new IllegalStateException("流式调用无可用模型") : lastException);
            }
        });
        return StreamCancellationHandles.of(() -> {
            cancelled.set(true);
            StreamCancellationHandle delegate = activeDelegate.get();
            if (delegate != null) {
                delegate.cancel();
            }
        });
    }

    private StreamCallback createGuardedCallback(
            StreamCallback callback,
            AtomicBoolean cancelled,
            AtomicBoolean attemptActive) {
        return new StreamCallback() {
            @Override
            public void onOpen(String modelName) {
                if (!cancelled.get() && attemptActive.get()) {
                    callback.onOpen(modelName);
                }
            }

            @Override
            public void onToken(String token) {
                if (!cancelled.get() && attemptActive.get()) {
                    callback.onToken(token);
                }
            }

            @Override
            public void onComplete() {
                if (!cancelled.get() && attemptActive.get()) {
                    callback.onComplete();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (!cancelled.get() && attemptActive.get()) {
                    callback.onError(throwable);
                }
            }
        };
    }
}
