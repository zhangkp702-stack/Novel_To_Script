package com.zkp.my12306.ntc.script.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.ScriptMessageResponseDto;
import com.zkp.my12306.ntc.dto.ScriptWorkResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WebAppConfiguration
class ScriptRefineIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private String createWork(String title) throws Exception {
        String workResponse = mockMvc.perform(post("/api/scripts/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ScriptWorkResponseDto work = objectMapper.readValue(workResponse, ScriptWorkResponseDto.class);
        return work.workId();
    }

    @Test
    @WithMockUser(username = "tester")
    void listMessages_empty_returnsEmptyList() throws Exception {
        String workId = createWork("改编测试");

        String response = mockMvc.perform(get("/api/scripts/messages")
                        .param("workId", workId)
                        .param("chapterNumber", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<ScriptMessageResponseDto> messages = objectMapper.readValue(
                response,
                new TypeReference<List<ScriptMessageResponseDto>>() {
                });
        assertTrue(messages.isEmpty());
    }

    @Test
    @WithMockUser(username = "tester")
    void refineStream_blankInstruction_returnsBadRequest() throws Exception {
        String workId = createWork("改编校验");
        String body = """
                {
                  "workId": "%s",
                  "chapterNumber": 1,
                  "instruction": "   ",
                  "currentScriptContent": "文档类型: 按章剧本片段\\n元信息:\\n  标题: 测试"
                }
                """.formatted(workId);

        mockMvc.perform(post("/api/scripts/refine/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "tester")
    void refineStream_blankScriptContent_returnsBadRequest() throws Exception {
        String workId = createWork("改编内容校验");
        String body = """
                {
                  "workId": "%s",
                  "chapterNumber": 1,
                  "instruction": "加强冲突",
                  "currentScriptContent": "   "
                }
                """.formatted(workId);

        mockMvc.perform(post("/api/scripts/refine/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "tester")
    void listMessages_missingWorkId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/scripts/messages")
                        .param("chapterNumber", "1"))
                .andExpect(status().isBadRequest());
    }
}
