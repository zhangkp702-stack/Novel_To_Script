package com.zkp.my12306.ntc.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm.trace")
public class LlmTraceProperties {

    private boolean enabled = true;
    private int maxErrorLength = 1000;
}
