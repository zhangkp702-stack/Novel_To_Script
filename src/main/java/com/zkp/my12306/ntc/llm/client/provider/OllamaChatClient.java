package com.zkp.my12306.ntc.llm.client.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.llm.client.AbstractOpenAIStyleChatClient;
import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import com.zkp.my12306.ntc.llm.stream.StreamAsyncExecutor;
import com.zkp.my12306.ntc.llm.stream.sse.OpenAIStyleSseParser;
import org.springframework.stereotype.Component;

@Component
public class OllamaChatClient extends AbstractOpenAIStyleChatClient {
    public OllamaChatClient(
            ObjectMapper objectMapper,
            OpenAIStyleSseParser openAIStyleSseParser,
            StreamAsyncExecutor streamAsyncExecutor,
            AIModelProperties aiModelProperties) {
        super(objectMapper, openAIStyleSseParser, streamAsyncExecutor, aiModelProperties);
    }

    @Override
    public String provider() {
        return "ollama";
    }
}
