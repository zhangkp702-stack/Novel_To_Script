package com.zkp.my12306.ntc.service.impl;

import com.zkp.my12306.ntc.dto.ScriptRecordResponseDto;
import com.zkp.my12306.ntc.dto.ScriptSaveRequestDto;
import com.zkp.my12306.ntc.dto.ScriptWorkSummaryDto;
import com.zkp.my12306.ntc.script.dao.entity.ScriptRecordDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptRecordMapper;
import com.zkp.my12306.ntc.script.record.ScriptRecordNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScriptRecordServiceImplTest {

    @Mock
    private ScriptRecordMapper scriptRecordMapper;
    @Mock
    private ScriptWorkService scriptWorkService;

    @InjectMocks
    private ScriptRecordServiceImpl service;

    @Test
    void save_newRecord_inserts() {
        when(scriptWorkService.requireWorkId("user1", "work-1")).thenReturn("work-1");
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId("work-1");
        work.setTitle("作品A");
        when(scriptWorkService.requireOwnedWork("user1", "work-1")).thenReturn(work);
        when(scriptRecordMapper.selectOne(any())).thenReturn(null);
        doNothing().when(scriptWorkService).touchWork("work-1");

        ScriptSaveRequestDto request = new ScriptSaveRequestDto(
                "work-1",
                1,
                "章节内容",
                "剧本内容",
                "model-x",
                "trace-1",
                "gen-1");
        ScriptRecordResponseDto response = service.save("user1", request);

        ArgumentCaptor<ScriptRecordDO> captor = ArgumentCaptor.forClass(ScriptRecordDO.class);
        verify(scriptRecordMapper).insert(captor.capture());
        verify(scriptRecordMapper, never()).updateById(any(ScriptRecordDO.class));
        assertEquals("user1", captor.getValue().getUserId());
        assertEquals("work-1", captor.getValue().getWorkId());
        assertEquals("trace-1", captor.getValue().getTraceId());
        assertEquals("作品A", response.workTitle());
        assertEquals("剧本内容", response.scriptContent());
    }

    @Test
    void getById_notFound_throws() {
        when(scriptRecordMapper.selectById(99L)).thenReturn(null);
        assertThrows(ScriptRecordNotFoundException.class, () -> service.getById("user1", 99L));
    }

    @Test
    void save_blankScriptContent_throws() {
        ScriptSaveRequestDto request = new ScriptSaveRequestDto("work-1", 1, "章节", "  ", null, null, null);
        assertThrows(ScriptRecordValidationException.class, () -> service.save("user1", request));
    }

    @Test
    void save_missingWorkId_throws() {
        ScriptSaveRequestDto request = new ScriptSaveRequestDto(null, 1, "章节", "剧本", null, null, null);
        assertThrows(ScriptRecordValidationException.class, () -> service.save("user1", request));
    }

    @Test
    void listWorks_delegatesToScriptWorkService() {
        when(scriptWorkService.listWorks("user1")).thenReturn(List.of(
                new ScriptWorkSummaryDto("work-1", "作品A", "作品A", 2, "2026-06-07T09:00", "2026-06-07T10:00")));

        List<ScriptWorkSummaryDto> works = service.listWorks("user1");

        assertEquals(1, works.size());
        assertEquals("work-1", works.get(0).workId());
    }

    @Test
    void deleteWork_notFound_throws() {
        doThrow(new ScriptWorkNotFoundException("work-missing"))
                .when(scriptWorkService).requireWorkId("user1", "work-missing");
        assertThrows(ScriptWorkNotFoundException.class, () -> service.deleteWork("user1", "work-missing"));
    }
}
