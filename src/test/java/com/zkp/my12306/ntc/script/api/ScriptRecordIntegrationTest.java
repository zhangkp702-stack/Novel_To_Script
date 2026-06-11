package com.zkp.my12306.ntc.script.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.ScriptRecordResponseDto;
import com.zkp.my12306.ntc.dto.ScriptWorkResponseDto;
import com.zkp.my12306.ntc.dto.ScriptWorkSummaryDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WebAppConfiguration
class ScriptRecordIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
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
    void saveAndListScriptRecord_success() throws Exception {
        String workId = createWork("雨夜归来");
        String saveBody = """
                {
                  "workId": "%s",
                  "chapterNumber": 1,
                  "chapterContent": "第一章小说内容",
                  "scriptContent": "剧本标题：《雨夜归来》\\n场景一：开场\\n旁白：雨夜。",
                  "modelName": "mock-model"
                }
                """.formatted(workId);

        String saveResponse = mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ScriptRecordResponseDto saved = objectMapper.readValue(saveResponse, ScriptRecordResponseDto.class);
        assertEquals("雨夜归来", saved.workTitle());
        assertEquals(workId, saved.workId());
        assertEquals(1, saved.chapterNumber());

        String listResponse = mockMvc.perform(get("/api/scripts")
                        .param("workId", workId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<ScriptRecordResponseDto> records = objectMapper.readValue(
                listResponse,
                new TypeReference<List<ScriptRecordResponseDto>>() {
                });
        assertEquals(1, records.size());
        assertEquals(saved.id(), records.get(0).id());

        mockMvc.perform(get("/api/scripts/{id}", saved.id()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "tester")
    void save_withEmptyScriptContent_returnsBadRequest() throws Exception {
        String workId = createWork("测试");
        String saveBody = """
                {
                  "workId": "%s",
                  "chapterNumber": 1,
                  "chapterContent": "内容",
                  "scriptContent": "   "
                }
                """.formatted(workId);

        mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "other-user")
    void getById_notOwner_returnsNotFound() throws Exception {
        String workId = createWork("隔离测试");
        String saveBody = """
                {
                  "workId": "%s",
                  "chapterNumber": 1,
                  "chapterContent": "内容",
                  "scriptContent": "剧本内容"
                }
                """.formatted(workId);

        String saveResponse = mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ScriptRecordResponseDto saved = objectMapper.readValue(saveResponse, ScriptRecordResponseDto.class);

        mockMvc.perform(get("/api/scripts/{id}", saved.id())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("intruder")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "tester")
    void save_sameChapter_updatesExistingRecord() throws Exception {
        String workId = createWork("更新测试");
        String firstSave = """
                {
                  "workId": "%s",
                  "chapterNumber": 2,
                  "chapterContent": "旧内容",
                  "scriptContent": "旧剧本"
                }
                """.formatted(workId);
        String secondSave = """
                {
                  "workId": "%s",
                  "chapterNumber": 2,
                  "chapterContent": "新内容",
                  "scriptContent": "新剧本"
                }
                """.formatted(workId);

        String firstResponse = mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstSave))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ScriptRecordResponseDto first = objectMapper.readValue(firstResponse, ScriptRecordResponseDto.class);

        String secondResponse = mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondSave))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ScriptRecordResponseDto second = objectMapper.readValue(secondResponse, ScriptRecordResponseDto.class);

        assertEquals(first.id(), second.id());
        assertEquals("新剧本", second.scriptContent());
        assertEquals("新内容", second.chapterContent());
        assertTrue(second.updatedAt() != null && !second.updatedAt().isBlank());
    }

    @Test
    @WithMockUser(username = "tester")
    void listWorks_returnsSummariesAndDeleteWork_removesRecords() throws Exception {
        String workIdA = createWork("作品管理A");
        String workIdB = createWork("作品管理B");

        mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workId": "%s",
                                  "chapterNumber": 1,
                                  "chapterContent": "第一章",
                                  "scriptContent": "剧本A1"
                                }
                                """.formatted(workIdA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workId": "%s",
                                  "chapterNumber": 2,
                                  "chapterContent": "第二章",
                                  "scriptContent": "剧本A2"
                                }
                                """.formatted(workIdA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workId": "%s",
                                  "chapterNumber": 1,
                                  "chapterContent": "B章",
                                  "scriptContent": "剧本B1"
                                }
                                """.formatted(workIdB)))
                .andExpect(status().isOk());

        String worksResponse = mockMvc.perform(get("/api/scripts/works"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<ScriptWorkSummaryDto> works = objectMapper.readValue(
                worksResponse,
                new TypeReference<List<ScriptWorkSummaryDto>>() {
                });
        assertTrue(works.stream().anyMatch(work -> workIdA.equals(work.workId())
                && "作品管理A".equals(work.workTitle())
                && work.chapterCount() == 2));
        assertTrue(works.stream().anyMatch(work -> workIdB.equals(work.workId())
                && "作品管理B".equals(work.workTitle())
                && work.chapterCount() == 1));

        mockMvc.perform(delete("/api/scripts/works").param("workId", workIdA))
                .andExpect(status().isNoContent());

        String worksAfterDelete = mockMvc.perform(get("/api/scripts/works"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<ScriptWorkSummaryDto> remaining = objectMapper.readValue(
                worksAfterDelete,
                new TypeReference<List<ScriptWorkSummaryDto>>() {
                });
        assertTrue(remaining.stream().noneMatch(work -> workIdA.equals(work.workId())));
        assertTrue(remaining.stream().anyMatch(work -> workIdB.equals(work.workId())));

        String deletedListResponse = mockMvc.perform(get("/api/scripts").param("workId", workIdA))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(deletedListResponse.contains("作品不存在") || deletedListResponse.contains("不存在"));
    }
}
