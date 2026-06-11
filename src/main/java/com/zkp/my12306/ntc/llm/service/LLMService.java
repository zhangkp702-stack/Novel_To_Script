package com.zkp.my12306.ntc.llm.service;

import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;

import java.util.List;

public interface LLMService {

    ChatResult chat(String prompt);

    ChatResult chat(List<ChatMessage> messages);

    StreamCancellationHandle streamChat(String prompt, StreamCallback callback);

    StreamCancellationHandle streamChat(List<ChatMessage> messages, StreamCallback callback);
}
