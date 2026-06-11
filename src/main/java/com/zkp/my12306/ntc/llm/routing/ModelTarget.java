package com.zkp.my12306.ntc.llm.routing;

import com.zkp.my12306.ntc.llm.config.AIModelProperties;

public record ModelTarget(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider
) {
}
