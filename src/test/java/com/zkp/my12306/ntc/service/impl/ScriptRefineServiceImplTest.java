package com.zkp.my12306.ntc.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.ScriptRefineRequestDto;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptMessageMapper;
import com.zkp.my12306.ntc.script.parse.ScriptOutputParser;
import com.zkp.my12306.ntc.script.prompt.ScriptPromptBuilder;
import com.zkp.my12306.ntc.script.prompt.ScriptRefinePromptBuilder;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.validate.ScriptSchemaValidator;
import com.zkp.my12306.ntc.service.CharacterService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import com.zkp.my12306.ntc.llm.service.LLMService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScriptRefineServiceImplTest {

    @Mock
    private ScriptMessageMapper scriptMessageMapper;
    @Mock
    private ScriptWorkService scriptWorkService;
    @Mock
    private CharacterService characterService;
    @Mock
    private LLMService llmService;

    private final ScriptRefinePromptBuilder refinePromptBuilder =
            new ScriptRefinePromptBuilder(new ScriptPromptBuilder());
    private final ScriptOutputParser outputParser = new ScriptOutputParser();
    private final ScriptSchemaValidator schemaValidator = new ScriptSchemaValidator();

    private ScriptRefineServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new ScriptRefineServiceImpl(
                scriptMessageMapper,
                scriptWorkService,
                characterService,
                refinePromptBuilder,
                outputParser,
                schemaValidator,
                llmService,
                new ObjectMapper());
    }

    @Test
    void validateRefineRequest_blankInstruction_throws() {
        ScriptRefineRequestDto request = new ScriptRefineRequestDto(
                "work-1",
                "gen-1",
                1,
                "  ",
                "剧本内容");
        assertThrows(ScriptRecordValidationException.class, () -> service.validateRefineRequest(request));
    }

    @Test
    void listMessages_invalidChapter_throws() {
        ScriptWorkDO work = new ScriptWorkDO();
        work.setId("work-1");
        work.setUserId("user1");
        when(scriptWorkService.requireOwnedWork("user1", "work-1")).thenReturn(work);

        assertThrows(ScriptRecordValidationException.class,
                () -> service.listMessages("user1", "work-1", 0));
    }

    @Test
    void validateRefineRequest_blankScriptContent_throws() {
        ScriptRefineRequestDto request = new ScriptRefineRequestDto(
                "work-1",
                "gen-1",
                1,
                "加强冲突",
                "  ");
        assertThrows(ScriptRecordValidationException.class, () -> service.validateRefineRequest(request));
    }

    @Test
    void emitStreamCompletionFeedback_invalidYaml_returnsEmpty() throws Exception {
        Method method = ScriptRefineServiceImpl.class.getDeclaredMethod(
                "emitStreamCompletionFeedback", SseEmitter.class, String.class);
        method.setAccessible(true);
        String persisted = (String) method.invoke(service, new SseEmitter(), "这不是合法 YAML");
        assertEquals("", persisted);
    }
}
