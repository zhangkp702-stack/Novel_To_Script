package com.zkp.my12306.ntc.script.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.ValidationErrorResponseDto;
import com.zkp.my12306.ntc.llm.service.LLMService;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WebAppConfiguration
class ScriptGenerationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LLMService llmService;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "tester")
    void generate_withEmptyChapter_returnsValidationError() throws Exception {
        String body = """
                {
                  "title": "测试",
                  "chapterNumber": 1,
                  "chapterContent": "   "
                }
                """;

        String response = mockMvc.perform(post("/api/scripts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ValidationErrorResponseDto error = objectMapper.readValue(response, ValidationErrorResponseDto.class);
        assertEquals("EMPTY_CHAPTER", error.code());
        assertEquals(1, error.chapterNumber());
        assertEquals(1, error.minChapters());
        assertEquals(0, error.filledCount());
    }

    @Test
    @WithMockUser(username = "tester")
    void generate_withInvalidChapterNumber_returnsValidationError() throws Exception {
        String body = """
                {
                  "title": "测试",
                  "chapterNumber": 0,
                  "chapterContent": "第一章"
                }
                """;

        String response = mockMvc.perform(post("/api/scripts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ValidationErrorResponseDto error = objectMapper.readValue(response, ValidationErrorResponseDto.class);
        assertEquals("INVALID_CHAPTER_NUMBER", error.code());
    }

    @Test
    @WithMockUser(username = "tester")
    void streamGenerate_withEmptyChapter_returnsValidationError() throws Exception {
        String body = """
                {
                  "title": "测试",
                  "chapterNumber": 2,
                  "chapterContent": ""
                }
                """;

        String response = mockMvc.perform(post("/api/scripts/generate/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ValidationErrorResponseDto error = objectMapper.readValue(response, ValidationErrorResponseDto.class);
        assertEquals("EMPTY_CHAPTER", error.code());
        assertEquals(2, error.chapterNumber());
    }

    @Test
    @WithMockUser(username = "tester")
    void streamGenerate_withValidRequest_returnsSseEvents() throws Exception {
        when(llmService.streamChat(anyString(), any(StreamCallback.class))).thenAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(1);
            callback.onOpen("mock-model");
            callback.onToken("剧本标题：《测试》\n");
            callback.onToken("场景一：开场\n");
            callback.onComplete();
            return mock(StreamCancellationHandle.class);
        });

        String body = """
                {
                  "title": "测试",
                  "chapterNumber": 1,
                  "chapterContent": "第一章内容"
                }
                """;

        MvcResult asyncResult = mockMvc.perform(post("/api/scripts/generate/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("text/event-stream")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("event:open"));
        assertTrue(content.contains("event:token"));
        assertTrue(content.contains("event:done"));
    }
}
