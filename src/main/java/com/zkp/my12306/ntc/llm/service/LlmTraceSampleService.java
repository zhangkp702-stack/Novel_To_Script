package com.zkp.my12306.ntc.llm.service;

import com.zkp.my12306.ntc.llm.trace.TraceRoot;
import org.springframework.stereotype.Service;

/**
 * Trace 注解接入示例，供集成测试与后续 LLM 业务埋点参考。
 */
@Service
public class LlmTraceSampleService {

    private final LlmTraceSampleNodeService nodeService;

    public LlmTraceSampleService(LlmTraceSampleNodeService nodeService) {
        this.nodeService = nodeService;
    }

    @TraceRoot(name = "sampleLlmTrace", conversationIdArg = "conversationId", taskIdArg = "taskId")
    public String runSample(String conversationId, String taskId) {
        return nodeService.innerStep();
    }
}
