package com.zkp.my12306.ntc.llm.aop;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceNodeDO;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceRunDO;
import com.zkp.my12306.ntc.llm.dao.mapper.LlmTraceNodeMapper;
import com.zkp.my12306.ntc.llm.dao.mapper.LlmTraceRunMapper;
import com.zkp.my12306.ntc.llm.service.LlmTraceSampleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class LlmTraceAspectIntegrationTest {

    @Autowired
    private LlmTraceSampleService sampleService;

    @Autowired
    private LlmTraceRunMapper runMapper;

    @Autowired
    private LlmTraceNodeMapper nodeMapper;

    @Test
    void traceRootAndNode_persistedToDatabase() {
        String result = sampleService.runSample("conv-integration", "task-integration");
        assertEquals("ok", result);

        List<LlmTraceRunDO> runs = runMapper.selectList(Wrappers.lambdaQuery(LlmTraceRunDO.class)
                .eq(LlmTraceRunDO::getTraceName, "sampleLlmTrace"));
        assertEquals(1, runs.size());

        LlmTraceRunDO run = runs.get(0);
        assertEquals("SUCCESS", run.getStatus());
        assertEquals("conv-integration", run.getConversationId());
        assertEquals("task-integration", run.getTaskId());
        assertNotNull(run.getStartTime());
        assertNotNull(run.getEndTime());
        assertNotNull(run.getDurationMs());

        List<LlmTraceNodeDO> nodes = nodeMapper.selectList(Wrappers.lambdaQuery(LlmTraceNodeDO.class)
                .eq(LlmTraceNodeDO::getTraceId, run.getTraceId()));
        assertEquals(1, nodes.size());

        LlmTraceNodeDO node = nodes.get(0);
        assertEquals("SUCCESS", node.getStatus());
        assertEquals("sampleInnerStep", node.getNodeName());
        assertEquals("METHOD", node.getNodeType());
        assertEquals(0, node.getDepth());
        assertNotNull(node.getDurationMs());
    }
}
