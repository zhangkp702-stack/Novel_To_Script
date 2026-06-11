package com.zkp.my12306.ntc.llm.routing;

import com.zkp.my12306.ntc.llm.client.ChatClient;
import com.zkp.my12306.ntc.llm.service.ChatMessage;
import com.zkp.my12306.ntc.llm.service.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ModelRoutingExecutor {
    private static final Logger log = LoggerFactory.getLogger(ModelRoutingExecutor.class);

    private final ModelHealthStore healthStore;

    public ModelRoutingExecutor(ModelHealthStore healthStore) {
        this.healthStore = healthStore;
    }

    public ChatResult executeChat(
            String prompt,
            List<ModelTarget> targets,
            Map<String, ChatClient> clientMap) {
        return executeChat(List.of(new ChatMessage("user", prompt)), targets, clientMap);
    }

    public ChatResult executeChat(
            List<ChatMessage> messages,
            List<ModelTarget> targets,
            Map<String, ChatClient> clientMap) {
        IllegalStateException lastException = null;
        for (ModelTarget target : targets) {
            String provider = target.candidate().getProvider();
            ChatClient chatClient = clientMap.get(provider);
            if (chatClient == null) {
                lastException = new IllegalStateException(
                        "未找到 provider 对应客户端：" + provider + "（model=" + target.id() + "）");
                continue;
            }
            if (!healthStore.allowCall(target.id())) {
                log.warn("模型熔断中，跳过同步调用: modelId={}", target.id());
                continue;
            }
            try {
                log.info("开始调用大模型: modelId={}, provider={}", target.id(), provider);
                ChatResult result = chatClient.chat(messages, target);
                log.info("大模型调用成功: modelId={}", target.id());
                healthStore.markSuccess(target.id());
                return result;
            } catch (Exception ex) {
                healthStore.markFailure(target.id());
                lastException = new IllegalStateException("模型调用失败：" + target.id(), ex);
                log.warn("同步模型调用失败，尝试下一个: modelId={}", target.id(), ex);
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("没有可用模型可执行");
    }
}
