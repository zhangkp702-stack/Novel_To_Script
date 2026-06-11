package com.zkp.my12306.ntc.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatObjectMapperConfig {

    @Bean
    public ObjectMapper chatObjectMapper() {
        return new ObjectMapper();
    }
}
