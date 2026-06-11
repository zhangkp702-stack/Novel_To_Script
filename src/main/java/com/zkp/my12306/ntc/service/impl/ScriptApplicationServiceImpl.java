package com.zkp.my12306.ntc.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.ScriptGenerateRequestDto;
import com.zkp.my12306.ntc.dto.ScriptGenerateResponseDto;
import com.zkp.my12306.ntc.llm.service.ChatResult;
import com.zkp.my12306.ntc.llm.service.LLMService;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;
import com.zkp.my12306.ntc.llm.trace.LlmTraceContext;
import com.zkp.my12306.ntc.llm.trace.TraceRoot;
import com.zkp.my12306.ntc.script.input.ScriptInputValidator;
import com.zkp.my12306.ntc.script.model.ScriptDocument;
import com.zkp.my12306.ntc.script.parse.ScriptOutputException;
import com.zkp.my12306.ntc.script.parse.ScriptOutputParser;
import com.zkp.my12306.ntc.script.validate.ScriptSchemaValidationException;
import com.zkp.my12306.ntc.script.prompt.CharacterPromptItem;
import com.zkp.my12306.ntc.script.prompt.ScriptPromptBuilder;
import com.zkp.my12306.ntc.script.stream.StreamDegenerationGuard;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.validate.ScriptSchemaValidator;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.service.CharacterService;
import com.zkp.my12306.ntc.service.ScriptApplicationService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ScriptApplicationServiceImpl implements ScriptApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ScriptApplicationServiceImpl.class);
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final LLMService llmService;
    private final ScriptInputValidator inputValidator;
    private final ScriptPromptBuilder promptBuilder;
    private final ScriptOutputParser outputParser;
    private final ScriptSchemaValidator schemaValidator;
    private final CharacterService characterService;
    private final ScriptWorkService scriptWorkService;
    private final ObjectMapper objectMapper;

    public ScriptApplicationServiceImpl(
            LLMService llmService,
            ScriptInputValidator inputValidator,
            ScriptPromptBuilder promptBuilder,
            ScriptOutputParser outputParser,
            ScriptSchemaValidator schemaValidator,
            CharacterService characterService,
            ScriptWorkService scriptWorkService,
            ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.inputValidator = inputValidator;
        this.promptBuilder = promptBuilder;
        this.outputParser = outputParser;
        this.schemaValidator = schemaValidator;
        this.characterService = characterService;
        this.scriptWorkService = scriptWorkService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void validateGenerateRequest(ScriptGenerateRequestDto request) {
        validateChapterRequest(request);
    }

    @Override
    @TraceRoot(name = "scriptGenerate", conversationIdArg = "workId", taskIdArg = "generationId")
    public ScriptGenerateResponseDto generateScript(
            ScriptGenerateRequestDto request,
            String workId,
            String generationId,
            String currentUser) {
        ChapterRequest chapterRequest = validateChapterRequest(request);
        String workTitle = resolveWorkTitle(currentUser, workId);
        List<CharacterPromptItem> characters = characterService.listForPrompt(currentUser, workId);
        String prompt = promptBuilder.build(
                workTitle,
                chapterRequest.chapterNumber(),
                chapterRequest.chapterContent(),
                characters);
        ChatResult llmResponse = llmService.chat(prompt);
        ScriptDocument document = outputParser.parse(llmResponse.content());
        schemaValidator.validate(document);
        return new ScriptGenerateResponseDto(
                llmResponse.modelName(),
                document.toMap(),
                llmResponse.content(),
                LlmTraceContext.getTraceId(),
                generationId,
                workId);
    }

    @Override
    @TraceRoot(name = "scriptGenerateStream", conversationIdArg = "workId", taskIdArg = "generationId")
    public void streamGenerateScript(
            ScriptGenerateRequestDto request,
            String workId,
            String generationId,
            String currentUser,
            SseEmitter emitter) {
        ChapterRequest chapterRequest = validateChapterRequest(request);
        String workTitle = resolveWorkTitle(currentUser, workId);
        List<CharacterPromptItem> characters = characterService.listForPrompt(currentUser, workId);
        String prompt = promptBuilder.build(
                workTitle,
                chapterRequest.chapterNumber(),
                chapterRequest.chapterContent(),
                characters);
        log.info("开始流式生成剧本: user={}, workId={}, chapter={}, title={}, contentLength={}, characterCount={}",
                currentUser,
                workId,
                chapterRequest.chapterNumber(),
                workTitle,
                chapterRequest.chapterContent().length(),
                characters.size());

        AtomicReference<StreamCancellationHandle> handleRef = new AtomicReference<>();
        AtomicBoolean streamFinished = new AtomicBoolean(false);
        StringBuilder accumulated = new StringBuilder();
        String traceId = LlmTraceContext.getTraceId();

        StreamCallback emitterCallback = new StreamCallback() {
            @Override
            public void onOpen(String modelName) {
                log.info("流式生成已建立连接: user={}, workId={}, chapter={}, model={}",
                        currentUser, workId, chapterRequest.chapterNumber(), modelName);
                sendSseEvent(emitter, "open", modelName == null ? "" : modelName);
                sendMetaEvent(emitter, workId, generationId, traceId, modelName);
            }

            @Override
            public void onToken(String token) {
                if (token != null) {
                    accumulated.append(token);
                }
                sendSseEvent(emitter, "token", token == null ? "" : token);
            }

            @Override
            public void onWarn(String message) {
                if (message != null && !message.isBlank()) {
                    sendSseEvent(emitter, "warn", message);
                }
            }

            @Override
            public void onComplete() {
                if (!streamFinished.compareAndSet(false, true)) {
                    return;
                }
                log.info("流式生成完成: user={}, workId={}, chapter={}, outputLength={}",
                        currentUser,
                        workId,
                        chapterRequest.chapterNumber(),
                        accumulated.length());
                emitStreamCompletionFeedback(emitter, accumulated.toString());
                sendSseEvent(emitter, "done", "");
                emitter.complete();
            }

            @Override
            public void onError(Throwable throwable) {
                if (!streamFinished.compareAndSet(false, true)) {
                    return;
                }
                sendSseEvent(emitter, "error", resolveStreamErrorMessage(throwable));
                emitter.completeWithError(throwable);
            }
        };
        StreamDegenerationGuard guardedCallback = new StreamDegenerationGuard(
                emitterCallback,
                () -> cancelStream(handleRef.get(), null));
        StreamCancellationHandle handle = llmService.streamChat(prompt, guardedCallback);
        handleRef.set(handle);

        emitter.onTimeout(() -> {
            if (streamFinished.compareAndSet(false, true)) {
                cancelStream(handleRef.get(), emitter);
            }
        });
        emitter.onCompletion(() -> {
            if (!streamFinished.get()) {
                cancelStream(handleRef.get(), null);
            }
        });
    }

    private void sendMetaEvent(SseEmitter emitter, String workId, String generationId, String traceId, String modelName) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("workId", workId);
        meta.put("generationId", generationId);
        meta.put("traceId", traceId);
        meta.put("modelName", modelName == null ? "" : modelName);
        try {
            sendSseEvent(emitter, "meta", objectMapper.writeValueAsString(meta));
        } catch (JsonProcessingException ex) {
            log.warn("发送 meta 事件失败", ex);
        }
    }

    private void cancelStream(StreamCancellationHandle handle, SseEmitter emitter) {
        if (handle != null && !handle.isCancelled()) {
            handle.cancel();
        }
        if (emitter != null) {
            emitter.complete();
        }
    }

    private void emitStreamCompletionFeedback(SseEmitter emitter, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            ScriptDocument document = outputParser.parse(content);
            if (document == null || document.root() == null) {
                sendSseEvent(emitter, "warn", "输出无法解析为合法 YAML，请换用更强模型后重试");
                return;
            }
            if (isNaturalScriptDocument(document)) {
                sendSseEvent(emitter, "warn", "输出未采用 YAML 格式，请换用更强模型后重试");
                return;
            }
            schemaValidator.validateChapterFragment(document);
            sendSseEvent(emitter, "artifact", document.toYaml());
        } catch (ScriptSchemaValidationException ex) {
            sendSseEvent(emitter, "warn", "YAML 结构校验未通过：" + ex.getMessage());
        } catch (ScriptOutputException ex) {
            sendSseEvent(emitter, "warn", "输出无法解析为合法 YAML，请换用更强模型后重试");
        } catch (RuntimeException ex) {
            log.warn("流式完成后的 YAML 处理失败", ex);
            sendSseEvent(emitter, "warn", "输出无法解析为合法 YAML，请换用更强模型后重试");
        }
    }

    private boolean isNaturalScriptDocument(ScriptDocument document) {
        return document != null
                && document.root() != null
                && "natural_script".equals(document.root().path("format").asText());
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, TEXT_PLAIN_UTF8));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private String resolveStreamErrorMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? "生成失败，请稍后重试" : message;
    }

    private ChapterRequest validateChapterRequest(ScriptGenerateRequestDto request) {
        int chapterNumber = request == null || request.chapterNumber() == null ? 1 : request.chapterNumber();
        String content = request == null || request.chapterContent() == null ? "" : request.chapterContent().trim();
        inputValidator.validate(chapterNumber, content);
        return new ChapterRequest(chapterNumber, content);
    }

    private String resolveWorkTitle(String currentUser, String workId) {
        ScriptWorkDO work = scriptWorkService.requireOwnedWork(currentUser, workId);
        String title = work.getTitle();
        if (title == null || title.isBlank()) {
            return "未命名作品";
        }
        return title.trim();
    }

    private record ChapterRequest(int chapterNumber, String chapterContent) {
    }
}
