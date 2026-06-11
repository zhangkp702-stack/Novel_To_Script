package com.zkp.my12306.ntc.llm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceNodeDO;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceRunDO;
import com.zkp.my12306.ntc.llm.dao.mapper.LlmTraceNodeMapper;
import com.zkp.my12306.ntc.llm.dao.mapper.LlmTraceRunMapper;
import com.zkp.my12306.ntc.llm.service.LlmTraceRecordService;
import com.zkp.my12306.ntc.llm.trace.TraceIdGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LlmTraceRecordServiceImpl implements LlmTraceRecordService {

    private final LlmTraceRunMapper runMapper;
    private final LlmTraceNodeMapper nodeMapper;

    public LlmTraceRecordServiceImpl(LlmTraceRunMapper runMapper, LlmTraceNodeMapper nodeMapper) {
        this.runMapper = runMapper;
        this.nodeMapper = nodeMapper;
    }

    @Override
    public void startRun(LlmTraceRunDO run) {
        if (run.getId() == null) {
            run.setId(TraceIdGenerator.nextId());
        }
        if (run.getDeleted() == null) {
            run.setDeleted(0);
        }
        runMapper.insert(run);
    }

    @Override
    public void finishRun(String traceId, String status, String errorMessage, LocalDateTime endTime, long durationMs) {
        LlmTraceRunDO update = new LlmTraceRunDO();
        update.setStatus(status);
        update.setErrorMessage(errorMessage);
        update.setEndTime(endTime);
        update.setDurationMs(durationMs);
        runMapper.update(update, Wrappers.lambdaUpdate(LlmTraceRunDO.class)
                .eq(LlmTraceRunDO::getTraceId, traceId));
    }

    @Override
    public void startNode(LlmTraceNodeDO node) {
        if (node.getId() == null) {
            node.setId(TraceIdGenerator.nextId());
        }
        if (node.getDeleted() == null) {
            node.setDeleted(0);
        }
        nodeMapper.insert(node);
    }

    @Override
    public void finishNode(
            String traceId,
            String nodeId,
            String status,
            String errorMessage,
            LocalDateTime endTime,
            long durationMs) {
        LlmTraceNodeDO update = new LlmTraceNodeDO();
        update.setStatus(status);
        update.setErrorMessage(errorMessage);
        update.setEndTime(endTime);
        update.setDurationMs(durationMs);
        nodeMapper.update(update, Wrappers.lambdaUpdate(LlmTraceNodeDO.class)
                .eq(LlmTraceNodeDO::getTraceId, traceId)
                .eq(LlmTraceNodeDO::getNodeId, nodeId));
    }
}
