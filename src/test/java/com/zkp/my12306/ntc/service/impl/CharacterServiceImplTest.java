package com.zkp.my12306.ntc.service.impl;

import com.zkp.my12306.ntc.dto.CharacterCreateRequestDto;
import com.zkp.my12306.ntc.dto.CharacterResponseDto;
import com.zkp.my12306.ntc.dto.CharacterUpdateRequestDto;
import com.zkp.my12306.ntc.script.dao.entity.CharacterDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.dao.mapper.CharacterMapper;
import com.zkp.my12306.ntc.script.record.CharacterNotFoundException;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.record.ScriptWorkNotFoundException;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterServiceImplTest {

    @Mock
    private CharacterMapper characterMapper;
    @Mock
    private ScriptWorkService scriptWorkService;

    @InjectMocks
    private CharacterServiceImpl service;

    @Test
    void create_success_insertsCharacter() {
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId("work-1");
        work.setUserId("user1");
        when(scriptWorkService.requireOwnedWork("user1", "work-1")).thenReturn(work);
        when(characterMapper.selectList(any())).thenReturn(List.of());

        CharacterResponseDto response = service.create(
                "user1",
                "work-1",
                new CharacterCreateRequestDto("林澈", "小林", "档案管理员", "冷静"));

        ArgumentCaptor<CharacterDO> captor = ArgumentCaptor.forClass(CharacterDO.class);
        verify(characterMapper).insert(captor.capture());
        assertEquals("work-1", captor.getValue().getWorkId());
        assertEquals("林澈", captor.getValue().getName());
        assertEquals(0, captor.getValue().getSortOrder());
        assertEquals("林澈", response.name());
        verify(scriptWorkService).touchWork("work-1");
    }

    @Test
    void create_blankName_throwsValidationError() {
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId("work-1");
        when(scriptWorkService.requireOwnedWork("user1", "work-1")).thenReturn(work);

        assertThrows(ScriptRecordValidationException.class,
                () -> service.create("user1", "work-1", new CharacterCreateRequestDto("  ", null, null, null)));
    }

    @Test
    void update_notOwnedCharacter_throwsNotFound() {
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId("work-1");
        when(scriptWorkService.requireOwnedWork("user1", "work-1")).thenReturn(work);
        CharacterDO existing = new CharacterDO();
        existing.setId("char-1");
        existing.setWorkId("other-work");
        when(characterMapper.selectById("char-1")).thenReturn(existing);

        assertThrows(CharacterNotFoundException.class,
                () -> service.update("user1", "work-1", "char-1", new CharacterUpdateRequestDto("林澈", null, null, null)));
    }

    @Test
    void listByWorkId_workMissing_throwsNotFound() {
        doThrow(new ScriptWorkNotFoundException("work-1"))
                .when(scriptWorkService).requireOwnedWork("user1", "work-1");

        assertThrows(ScriptWorkNotFoundException.class,
                () -> service.listByWorkId("user1", "work-1"));
    }

    @Test
    void listByWorkId_returnsSortedCharacters() {
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId("work-1");
        when(scriptWorkService.requireOwnedWork("user1", "work-1")).thenReturn(work);

        CharacterDO first = new CharacterDO();
        first.setId("char-1");
        first.setWorkId("work-1");
        first.setName("甲");
        first.setSortOrder(0);
        first.setCreateTime(LocalDateTime.parse("2026-01-01T10:00:00"));

        CharacterDO second = new CharacterDO();
        second.setId("char-2");
        second.setWorkId("work-1");
        second.setName("乙");
        second.setSortOrder(1);
        second.setCreateTime(LocalDateTime.parse("2026-01-02T10:00:00"));

        when(characterMapper.selectList(any())).thenReturn(List.of(second, first));

        List<CharacterResponseDto> responses = service.listByWorkId("user1", "work-1");

        assertEquals(2, responses.size());
        assertEquals("甲", responses.get(0).name());
        assertEquals("乙", responses.get(1).name());
    }
}
