package com.zkp.my12306.ntc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zkp.my12306.ntc.dto.ScriptRecordResponseDto;
import com.zkp.my12306.ntc.dto.ScriptSaveRequestDto;
import com.zkp.my12306.ntc.dto.ScriptWorkSummaryDto;
import com.zkp.my12306.ntc.llm.trace.TraceIdGenerator;
import com.zkp.my12306.ntc.script.dao.entity.ScriptRecordDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptRecordMapper;
import com.zkp.my12306.ntc.script.record.ScriptRecordAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptRecordNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.service.ScriptRecordService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class ScriptRecordServiceImpl implements ScriptRecordService {

    private final ScriptRecordMapper scriptRecordMapper;
    private final ScriptWorkService scriptWorkService;

    public ScriptRecordServiceImpl(ScriptRecordMapper scriptRecordMapper, ScriptWorkService scriptWorkService) {
        this.scriptRecordMapper = scriptRecordMapper;
        this.scriptWorkService = scriptWorkService;
    }

    @Override
    public ScriptRecordResponseDto save(String currentUser, ScriptSaveRequestDto request) {
        validateSaveRequest(currentUser, request);
        String userId = currentUser.trim();
        String workId = scriptWorkService.requireWorkId(currentUser, request.workId());
        ScriptWorkDO work = scriptWorkService.requireOwnedWork(currentUser, workId);
        String workTitle = work.getTitle();
        int chapterNumber = request.chapterNumber();
        String chapterContent = request.chapterContent().trim();
        String scriptContent = request.scriptContent().trim();
        LocalDateTime now = LocalDateTime.now();

        ScriptRecordDO existing = scriptRecordMapper.selectOne(Wrappers.lambdaQuery(ScriptRecordDO.class)
                .eq(ScriptRecordDO::getUserId, userId)
                .eq(ScriptRecordDO::getWorkId, workId)
                .eq(ScriptRecordDO::getChapterNumber, chapterNumber)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = scriptRecordMapper.selectOne(Wrappers.lambdaQuery(ScriptRecordDO.class)
                    .eq(ScriptRecordDO::getUserId, userId)
                    .eq(ScriptRecordDO::getWorkTitle, workTitle)
                    .eq(ScriptRecordDO::getChapterNumber, chapterNumber)
                    .last("LIMIT 1"));
        }

        if (existing == null) {
            ScriptRecordDO record = new ScriptRecordDO();
            record.setId(TraceIdGenerator.nextId());
            record.setUserId(userId);
            record.setWorkId(workId);
            record.setWorkTitle(workTitle);
            record.setChapterNumber(chapterNumber);
            record.setChapterContent(chapterContent);
            record.setChapterContentHash(hashContent(chapterContent));
            record.setScriptContent(scriptContent);
            record.setModelName(normalizeOptional(request.modelName()));
            record.setTraceId(normalizeOptional(request.traceId()));
            record.setGenerationId(normalizeOptional(request.generationId()));
            record.setCreateTime(now);
            record.setUpdateTime(now);
            record.setDeleted(0);
            scriptRecordMapper.insert(record);
            scriptWorkService.touchWork(workId);
            return toResponse(record);
        }

        existing.setWorkId(workId);
        existing.setWorkTitle(workTitle);
        existing.setChapterContent(chapterContent);
        existing.setChapterContentHash(hashContent(chapterContent));
        existing.setScriptContent(scriptContent);
        existing.setModelName(normalizeOptional(request.modelName()));
        existing.setTraceId(normalizeOptional(request.traceId()));
        existing.setGenerationId(normalizeOptional(request.generationId()));
        existing.setUpdateTime(now);
        scriptRecordMapper.updateById(existing);
        scriptWorkService.touchWork(workId);
        return toResponse(existing);
    }

    @Override
    public List<ScriptRecordResponseDto> listByWorkId(String currentUser, String workId) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        List<ScriptRecordDO> records = scriptRecordMapper.selectList(Wrappers.lambdaQuery(ScriptRecordDO.class)
                .eq(ScriptRecordDO::getUserId, currentUser.trim())
                .eq(ScriptRecordDO::getWorkId, workId.trim())
                .orderByAsc(ScriptRecordDO::getChapterNumber));
        return records.stream().map(this::toResponse).toList();
    }

    @Override
    public List<ScriptWorkSummaryDto> listWorks(String currentUser) {
        return scriptWorkService.listWorks(currentUser);
    }

    @Override
    public void deleteWork(String currentUser, String workId) {
        scriptWorkService.deleteWork(currentUser, scriptWorkService.requireWorkId(currentUser, workId));
    }

    @Override
    public ScriptRecordResponseDto getById(String currentUser, Long id) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new ScriptRecordValidationException("用户未登录");
        }
        if (id == null || id < 1) {
            throw new ScriptRecordValidationException("记录 ID 无效");
        }
        ScriptRecordDO record = scriptRecordMapper.selectById(id);
        if (record == null) {
            throw new ScriptRecordNotFoundException(id);
        }
        if (!currentUser.trim().equals(record.getUserId())) {
            throw new ScriptRecordAccessDeniedException(id);
        }
        return toResponse(record);
    }

    private void validateSaveRequest(String currentUser, ScriptSaveRequestDto request) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new ScriptRecordValidationException("用户未登录");
        }
        if (request == null) {
            throw new ScriptRecordValidationException("请求体不能为空");
        }
        if (request.chapterNumber() == null || request.chapterNumber() < 1) {
            throw new ScriptRecordValidationException("章节编号无效");
        }
        if (request.chapterContent() == null || request.chapterContent().isBlank()) {
            throw new ScriptRecordValidationException("章节内容不能为空");
        }
        if (request.scriptContent() == null || request.scriptContent().isBlank()) {
            throw new ScriptRecordValidationException("剧本内容不能为空");
        }
        if (request.workId() == null || request.workId().isBlank()) {
            throw new ScriptRecordValidationException("作品ID不能为空");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ScriptRecordResponseDto toResponse(ScriptRecordDO record) {
        return new ScriptRecordResponseDto(
                record.getId(),
                record.getWorkId(),
                record.getWorkTitle(),
                record.getChapterNumber(),
                record.getChapterContent(),
                record.getScriptContent(),
                record.getModelName(),
                record.getTraceId(),
                record.getGenerationId(),
                formatDateTime(record.getCreateTime()),
                formatDateTime(record.getUpdateTime()));
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }
}
