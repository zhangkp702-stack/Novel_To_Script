package com.zkp.my12306.ntc.llm.service;

import com.zkp.my12306.ntc.llm.trace.TraceNode;
import org.springframework.stereotype.Service;

@Service
public class LlmTraceSampleNodeService {

    @TraceNode(name = "sampleInnerStep", type = "METHOD")
    public String innerStep() {
        return "ok";
    }
}
