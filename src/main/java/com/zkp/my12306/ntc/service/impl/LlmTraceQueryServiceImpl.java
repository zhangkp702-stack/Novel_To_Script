package com.zkp.my12306.ntc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zkp.my12306.ntc.dto.LlmTraceNodeResponseDto;
import com.zkp.my12306.ntc.dto.LlmTraceRunResponseDto;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceNodeDO;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceRunDO;
import com.zkp.my12306.ntc.llm.dao.mapper.LlmTraceNodeMapper;
import com.zkp.my12306.ntc.llm.dao.mapper.LlmTraceRunMapper;
import com.zkp.my12306.ntc.script.dao.entity.ScriptRecordDO;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptRecordMapper;
import com.zkp.my12306.ntc.script.record.ScriptRecordAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptRecordNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.service.LlmTraceQueryService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class LlmTraceQueryServiceImpl implements LlmTraceQueryService {

    private final LlmTraceRunMapper traceRunMapper;
    private final LlmTraceNodeMapper traceNodeMapper;
    private final ScriptRecordMapper scriptRecordMapper;
    private final ScriptWorkService scriptWorkService;

    public LlmTraceQueryServiceImpl(
            LlmTraceRunMapper traceRunMapper,
            LlmTraceNodeMapper traceNodeMapper,
            ScriptRecordMapper scriptRecordMapper,
            ScriptWorkService scriptWorkService) {
        this.traceRunMapper = traceRunMapper;
        this.traceNodeMapper = traceNodeMapper;
        this.scriptRecordMapper = scriptRecordMapper;
        this.scriptWorkService = scriptWorkService;
    }

    @Override
    public LlmTraceRunResponseDto getByTraceId(String currentUser, String traceId) {
        validateUser(currentUser);
        if (traceId == null || traceId.isBlank()) {
            throw new ScriptRecordValidationException("traceId 不能为空");
        }
        LlmTraceRunDO run = traceRunMapper.selectOne(Wrappers.lambdaQuery(LlmTraceRunDO.class)
                .eq(LlmTraceRunDO::getTraceId, traceId.trim())
                .last("LIMIT 1"));
        if (run == null) {
            throw new ScriptRecordNotFoundException(traceId);
        }
        if (run.getUserId() != null && !run.getUserId().isBlank() && !currentUser.trim().equals(run.getUserId())) {
            throw new ScriptRecordValidationException("无权查看该链路");
        }
        return toResponse(run);
    }

    @Override
    public List<LlmTraceRunResponseDto> listByWorkId(String currentUser, String workId) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        List<LlmTraceRunDO> runs = traceRunMapper.selectList(Wrappers.lambdaQuery(LlmTraceRunDO.class)
                .eq(LlmTraceRunDO::getConversationId, workId.trim())
                .orderByDesc(LlmTraceRunDO::getStartTime));
        return runs.stream().map(this::toResponse).toList();
    }

    @Override
    public LlmTraceRunResponseDto getByRecordId(String currentUser, Long recordId) {
        validateUser(currentUser);
        if (recordId == null || recordId < 1) {
            throw new ScriptRecordValidationException("记录 ID 无效");
        }
        ScriptRecordDO record = scriptRecordMapper.selectById(recordId);
        if (record == null) {
            throw new ScriptRecordNotFoundException(recordId);
        }
        if (!currentUser.trim().equals(record.getUserId())) {
            throw new ScriptRecordAccessDeniedException(recordId);
        }
        if (record.getTraceId() == null || record.getTraceId().isBlank()) {
            throw new ScriptRecordNotFoundException(recordId);
        }
        return getByTraceId(currentUser, record.getTraceId());
    }

    private LlmTraceRunResponseDto toResponse(LlmTraceRunDO run) {
        List<LlmTraceNodeDO> nodes = traceNodeMapper.selectList(Wrappers.lambdaQuery(LlmTraceNodeDO.class)
                .eq(LlmTraceNodeDO::getTraceId, run.getTraceId())
                .orderByAsc(LlmTraceNodeDO::getStartTime));
        nodes.sort(Comparator.comparing(LlmTraceNodeDO::getDepth).thenComparing(LlmTraceNodeDO::getStartTime));
        return new LlmTraceRunResponseDto(
                run.getTraceId(),
                run.getTraceName(),
                run.getConversationId(),
                run.getTaskId(),
                run.getUserId(),
                run.getStatus(),
                run.getErrorMessage(),
                formatDateTime(run.getStartTime()),
                formatDateTime(run.getEndTime()),
                run.getDurationMs(),
                nodes.stream().map(this::toNodeResponse).toList());
    }

    private LlmTraceNodeResponseDto toNodeResponse(LlmTraceNodeDO node) {
        return new LlmTraceNodeResponseDto(
                node.getNodeId(),
                node.getParentNodeId(),
                node.getDepth(),
                node.getNodeType(),
                node.getNodeName(),
                node.getStatus(),
                node.getErrorMessage(),
                formatDateTime(node.getStartTime()),
                formatDateTime(node.getEndTime()),
                node.getDurationMs());
    }

    private void validateUser(String currentUser) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new ScriptRecordValidationException("用户未登录");
        }
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
