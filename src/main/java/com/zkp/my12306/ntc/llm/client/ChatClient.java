package com.zkp.my12306.ntc.llm.client;

import com.zkp.my12306.ntc.llm.routing.ModelTarget;
import com.zkp.my12306.ntc.llm.service.ChatMessage;
import com.zkp.my12306.ntc.llm.service.ChatResult;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;

import java.util.List;

public interface ChatClient {

    String provider();

    ChatResult chat(String prompt, ModelTarget modelTarget);

    StreamCancellationHandle streamChat(String prompt, ModelTarget modelTarget, StreamCallback callback);

    default ChatResult chat(List<ChatMessage> messages, ModelTarget modelTarget) {
        return chat(joinMessages(messages), modelTarget);
    }

    default StreamCancellationHandle streamChat(
            List<ChatMessage> messages,
            ModelTarget modelTarget,
            StreamCallback callback) {
        return streamChat(joinMessages(messages), modelTarget, callback);
    }

    static String joinMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        if (messages.size() == 1) {
            return messages.get(0).content() == null ? "" : messages.get(0).content();
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(message.role()).append(": ").append(message.content());
        }
        return builder.toString();
    }
}
