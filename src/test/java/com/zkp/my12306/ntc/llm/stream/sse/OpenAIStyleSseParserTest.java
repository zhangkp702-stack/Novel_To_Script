package com.zkp.my12306.ntc.llm.stream.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIStyleSseParserTest {

    @Test
    void parse_extractsDeltaContentTokensInOrder() throws Exception {
        String upstream = """
                data: {"choices":[{"delta":{"content":"metadata:"}}]}

                data: {"choices":[{"delta":{"content":"\\n  title: \\"旧城雨夜\\""}}]}

                data: {"choices":[{"delta":{"content":"\\n"}}]}

                data: [DONE]

                """;
        List<String> tokens = new ArrayList<>();
        OpenAIStyleSseParser parser = new OpenAIStyleSseParser(new ObjectMapper());
        parser.parse(new ByteArrayInputStream(upstream.getBytes(StandardCharsets.UTF_8)), collecting(tokens));

        assertEquals("metadata:\n  title: \"旧城雨夜\"\n", String.join("", tokens));
    }

    @Test
    void parse_skipsEmptyContentTokens() throws Exception {
        String upstream = """
                data: {"choices":[{"delta":{"content":""}}]}

                data: {"choices":[{"delta":{"content":"x"}}]}

                data: [DONE]

                """;
        List<String> tokens = new ArrayList<>();
        OpenAIStyleSseParser parser = new OpenAIStyleSseParser(new ObjectMapper());
        parser.parse(new ByteArrayInputStream(upstream.getBytes(StandardCharsets.UTF_8)), collecting(tokens));

        assertEquals(List.of("x"), tokens);
    }

    @Test
    void parse_eofWithoutDone_reportsError() throws Exception {
        String upstream = """
                data: {"choices":[{"delta":{"content":"片段"}}]}

                """;
        List<String> tokens = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        OpenAIStyleSseParser parser = new OpenAIStyleSseParser(new ObjectMapper());
        parser.parse(new ByteArrayInputStream(upstream.getBytes(StandardCharsets.UTF_8)), new StreamCallback() {
            @Override
            public void onOpen(String modelName) {
            }

            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
                errors.add(throwable);
            }
        });

        assertEquals(List.of("片段"), tokens);
        assertEquals(1, errors.size());
        assertInstanceOf(IllegalStateException.class, errors.get(0));
    }

    @Test
    void parse_reasoningContentTriggersStreamActivityWithoutToken() throws Exception {
        String upstream = """
                data: {"choices":[{"delta":{"reasoning_content":"思考中"}}]}

                data: {"choices":[{"delta":{"content":"正文"}}]}

                data: [DONE]

                """;
        List<String> tokens = new ArrayList<>();
        List<Integer> activities = new ArrayList<>();
        OpenAIStyleSseParser parser = new OpenAIStyleSseParser(new ObjectMapper());
        parser.parse(new ByteArrayInputStream(upstream.getBytes(StandardCharsets.UTF_8)), new StreamCallback() {
            @Override
            public void onStreamActivity() {
                activities.add(1);
            }

            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }
        });

        assertEquals(1, activities.size());
        assertEquals(List.of("正文"), tokens);
    }

    @Test
    void parse_skipsChunksWithoutContent() throws Exception {
        String upstream = """
                data: {"choices":[{"delta":{"role":"assistant"}}]}

                data: {"choices":[{"delta":{"content":"ok"}}]}

                data: [DONE]

                """;
        List<String> tokens = new ArrayList<>();
        OpenAIStyleSseParser parser = new OpenAIStyleSseParser(new ObjectMapper());
        parser.parse(new ByteArrayInputStream(upstream.getBytes(StandardCharsets.UTF_8)), collecting(tokens));

        assertEquals(List.of("ok"), tokens);
    }

    private StreamCallback collecting(List<String> tokens) {
        return new StreamCallback() {
            @Override
            public void onOpen(String modelName) {
            }

            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }
        };
    }
}
