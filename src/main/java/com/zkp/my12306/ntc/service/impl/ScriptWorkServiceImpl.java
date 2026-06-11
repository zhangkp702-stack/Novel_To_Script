package com.zkp.my12306.ntc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zkp.my12306.ntc.dto.ScriptWorkCreateRequestDto;
import com.zkp.my12306.ntc.dto.ScriptWorkResponseDto;
import com.zkp.my12306.ntc.dto.ScriptWorkSummaryDto;
import com.zkp.my12306.ntc.script.dao.entity.CharacterDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptMessageDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptRecordDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.dao.mapper.CharacterMapper;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptMessageMapper;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptRecordMapper;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptWorkMapper;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkAccessDeniedException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScriptWorkServiceImpl implements ScriptWorkService {

    private static final String UNTITLED_WORK_LABEL = "未命名作品";

    private final ScriptWorkMapper scriptWorkMapper;
    private final ScriptRecordMapper scriptRecordMapper;
    private final CharacterMapper characterMapper;
    private final ScriptMessageMapper scriptMessageMapper;

    public ScriptWorkServiceImpl(
            ScriptWorkMapper scriptWorkMapper,
            ScriptRecordMapper scriptRecordMapper,
            CharacterMapper characterMapper,
            ScriptMessageMapper scriptMessageMapper) {
        this.scriptWorkMapper = scriptWorkMapper;
        this.scriptRecordMapper = scriptRecordMapper;
        this.characterMapper = characterMapper;
        this.scriptMessageMapper = scriptMessageMapper;
    }

    @Override
    public ScriptWorkResponseDto createWork(String currentUser, ScriptWorkCreateRequestDto request) {
        validateUser(currentUser);
        String userId = currentUser.trim();
        String title = normalizeTitle(request == null ? null : request.title());
        LocalDateTime now = LocalDateTime.now();
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId(UUID.randomUUID().toString());
        work.setUserId(userId);
        work.setTitle(title);
        work.setCreateTime(now);
        work.setUpdateTime(now);
        work.setDeleted(0);
        scriptWorkMapper.insert(work);
        return toResponse(work);
    }

    @Override
    public String requireWorkId(String currentUser, String workId) {
        validateUser(currentUser);
        if (workId == null || workId.isBlank()) {
            throw new ScriptRecordValidationException("作品ID不能为空");
        }
        requireOwnedWork(currentUser, workId.trim());
        return workId.trim();
    }

    @Override
    public void backfillLegacyRecords(String currentUser) {
        validateUser(currentUser);
        String userId = currentUser.trim();
        List<ScriptRecordDO> orphans = scriptRecordMapper.selectList(Wrappers.lambdaQuery(ScriptRecordDO.class)
                .eq(ScriptRecordDO::getUserId, userId)
                .and(wrapper -> wrapper.isNull(ScriptRecordDO::getWorkId)
                        .or()
                        .eq(ScriptRecordDO::getWorkId, "")));
        if (orphans.isEmpty()) {
            return;
        }
        Map<String, String> titleToWorkId = new HashMap<>();
        for (ScriptRecordDO record : orphans) {
            String title = normalizeTitle(record.getWorkTitle());
            String resolvedWorkId = titleToWorkId.computeIfAbsent(
                    title,
                    key -> findOrCreateWorkIdByTitle(currentUser, key));
            ScriptRecordDO update = new ScriptRecordDO();
            update.setId(record.getId());
            update.setWorkId(resolvedWorkId);
            scriptRecordMapper.updateById(update);
        }
    }

    private String findOrCreateWorkIdByTitle(String currentUser, String title) {
        String userId = currentUser.trim();
        ScriptWorkDO existing = scriptWorkMapper.selectOne(Wrappers.lambdaQuery(ScriptWorkDO.class)
                .eq(ScriptWorkDO::getUserId, userId)
                .eq(ScriptWorkDO::getTitle, title)
                .orderByDesc(ScriptWorkDO::getUpdateTime)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        return createWork(currentUser, new ScriptWorkCreateRequestDto(title)).workId();
    }

    @Override
    public ScriptWorkDO requireOwnedWork(String currentUser, String workId) {
        validateUser(currentUser);
        if (workId == null || workId.isBlank()) {
            throw new ScriptWorkNotFoundException(workId);
        }
        ScriptWorkDO work = scriptWorkMapper.selectById(workId.trim());
        if (work == null) {
            throw new ScriptWorkNotFoundException(workId);
        }
        if (!currentUser.trim().equals(work.getUserId())) {
            throw new ScriptWorkAccessDeniedException(workId);
        }
        return work;
    }

    @Override
    public List<ScriptWorkSummaryDto> listWorks(String currentUser) {
        validateUser(currentUser);
        backfillLegacyRecords(currentUser);
        String userId = currentUser.trim();
        List<ScriptWorkDO> works = scriptWorkMapper.selectList(Wrappers.lambdaQuery(ScriptWorkDO.class)
                .eq(ScriptWorkDO::getUserId, userId)
                .orderByDesc(ScriptWorkDO::getCreateTime));
        Map<String, WorkAggregate> aggregates = new HashMap<>();
        List<ScriptRecordDO> records = scriptRecordMapper.selectList(Wrappers.lambdaQuery(ScriptRecordDO.class)
                .eq(ScriptRecordDO::getUserId, userId));
        for (ScriptRecordDO record : records) {
            String key = record.getWorkId() != null && !record.getWorkId().isBlank()
                    ? record.getWorkId()
                    : normalizeTitle(record.getWorkTitle());
            WorkAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new WorkAggregate());
            aggregate.chapterCount++;
            LocalDateTime updatedAt = record.getUpdateTime();
            if (updatedAt != null && (aggregate.lastUpdatedAt == null || updatedAt.isAfter(aggregate.lastUpdatedAt))) {
                aggregate.lastUpdatedAt = updatedAt;
            }
        }
        List<ScriptWorkSummaryDto> summaries = new ArrayList<>();
        for (ScriptWorkDO work : works) {
            WorkAggregate aggregate = aggregates.get(work.getId());
            summaries.add(new ScriptWorkSummaryDto(
                    work.getId(),
                    work.getTitle(),
                    toDisplayTitle(work.getTitle()),
                    aggregate == null ? 0 : aggregate.chapterCount,
                    formatDateTime(work.getCreateTime()),
                    formatDateTime(aggregate == null ? work.getUpdateTime() : aggregate.lastUpdatedAt)));
        }
        summaries.sort(Comparator.comparing(ScriptWorkSummaryDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return summaries;
    }

    @Override
    public ScriptWorkResponseDto updateTitle(String currentUser, String workId, String title) {
        ScriptWorkDO work = requireOwnedWork(currentUser, workId);
        String normalizedTitle = normalizeTitle(title);
        if (normalizedTitle.length() > 256) {
            throw new ScriptRecordValidationException("作品标题不能超过 256 字");
        }
        ScriptWorkDO update = new ScriptWorkDO();
        update.setId(work.getId());
        update.setTitle(normalizedTitle);
        update.setUpdateTime(LocalDateTime.now());
        scriptWorkMapper.updateById(update);
        work.setTitle(normalizedTitle);
        work.setUpdateTime(update.getUpdateTime());
        return toResponse(work);
    }

    @Override
    public void deleteWork(String currentUser, String workId) {
        ScriptWorkDO work = requireOwnedWork(currentUser, workId);
        scriptRecordMapper.delete(Wrappers.lambdaQuery(ScriptRecordDO.class)
                .eq(ScriptRecordDO::getUserId, currentUser.trim())
                .eq(ScriptRecordDO::getWorkId, work.getId()));
        characterMapper.delete(Wrappers.lambdaQuery(CharacterDO.class)
                .eq(CharacterDO::getWorkId, work.getId()));
        scriptMessageMapper.delete(Wrappers.lambdaQuery(ScriptMessageDO.class)
                .eq(ScriptMessageDO::getWorkId, work.getId()));
        scriptWorkMapper.deleteById(work.getId());
    }

    @Override
    public void touchWork(String workId) {
        if (workId == null || workId.isBlank()) {
            return;
        }
        ScriptWorkDO update = new ScriptWorkDO();
        update.setId(workId);
        update.setUpdateTime(LocalDateTime.now());
        scriptWorkMapper.updateById(update);
    }

    private ScriptWorkResponseDto toResponse(ScriptWorkDO work) {
        return new ScriptWorkResponseDto(
                work.getId(),
                work.getTitle(),
                toDisplayTitle(work.getTitle()),
                formatDateTime(work.getCreateTime()),
                formatDateTime(work.getUpdateTime()));
    }

    private void validateUser(String currentUser) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new ScriptRecordValidationException("用户未登录");
        }
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim();
    }

    private String toDisplayTitle(String title) {
        if (title == null || title.isBlank()) {
            return UNTITLED_WORK_LABEL;
        }
        return title;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static final class WorkAggregate {
        private int chapterCount;
        private LocalDateTime lastUpdatedAt;
    }
}
