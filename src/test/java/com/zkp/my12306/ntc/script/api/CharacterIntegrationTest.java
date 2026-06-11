package com.zkp.my12306.ntc.script.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.CharacterResponseDto;
import com.zkp.my12306.ntc.dto.ScriptWorkResponseDto;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WebAppConfiguration
class CharacterIntegrationTest {

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

    @Test
    @WithMockUser(username = "tester")
    void characterCrud_success() throws Exception {
        String workResponse = mockMvc.perform(post("/api/scripts/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"人物测试作品\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ScriptWorkResponseDto work = objectMapper.readValue(workResponse, ScriptWorkResponseDto.class);
        String workId = work.workId();

        String createBody = """
                {
                  "name": "林澈",
                  "displayName": "小林",
                  "description": "档案管理员",
                  "personality": "冷静克制"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/works/" + workId + "/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        CharacterResponseDto created = objectMapper.readValue(createResponse, CharacterResponseDto.class);
        assertEquals("林澈", created.name());
        assertEquals("小林", created.displayName());

        String listResponse = mockMvc.perform(get("/api/works/" + workId + "/characters"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<CharacterResponseDto> characters = objectMapper.readValue(
                listResponse,
                new TypeReference<List<CharacterResponseDto>>() {
                });
        assertEquals(1, characters.size());

        String updateBody = """
                {
                  "name": "林澈",
                  "displayName": "林老师",
                  "description": "资深档案管理员",
                  "personality": "冷静克制"
                }
                """;

        mockMvc.perform(put("/api/works/" + workId + "/characters/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/works/" + workId + "/characters/" + created.id()))
                .andExpect(status().isNoContent());

        String emptyListResponse = mockMvc.perform(get("/api/works/" + workId + "/characters"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<CharacterResponseDto> empty = objectMapper.readValue(
                emptyListResponse,
                new TypeReference<List<CharacterResponseDto>>() {
                });
        assertEquals(0, empty.size());
    }
}
