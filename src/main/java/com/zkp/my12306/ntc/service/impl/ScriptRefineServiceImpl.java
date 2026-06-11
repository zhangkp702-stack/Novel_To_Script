package com.zkp.my12306.ntc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkp.my12306.ntc.dto.ScriptMessageResponseDto;
import com.zkp.my12306.ntc.dto.ScriptRefineRequestDto;
import com.zkp.my12306.ntc.llm.service.ChatMessage;
import com.zkp.my12306.ntc.llm.service.LLMService;
import com.zkp.my12306.ntc.llm.stream.StreamCallback;
import com.zkp.my12306.ntc.llm.stream.StreamCancellationHandle;
import com.zkp.my12306.ntc.llm.trace.LlmTraceContext;
import com.zkp.my12306.ntc.llm.trace.TraceRoot;
import com.zkp.my12306.ntc.script.dao.entity.ScriptMessageDO;
import com.zkp.my12306.ntc.script.dao.entity.ScriptWorkDO;
import com.zkp.my12306.ntc.script.dao.mapper.ScriptMessageMapper;
import com.zkp.my12306.ntc.script.model.ScriptDocument;
import com.zkp.my12306.ntc.script.parse.ScriptOutputException;
import com.zkp.my12306.ntc.script.parse.ScriptOutputParser;
import com.zkp.my12306.ntc.script.prompt.CharacterPromptItem;
import com.zkp.my12306.ntc.script.prompt.ScriptRefinePromptBuilder;
import com.zkp.my12306.ntc.script.record.ScriptRecordValidationException;
import com.zkp.my12306.ntc.script.stream.StreamDegenerationGuard;
import com.zkp.my12306.ntc.script.validate.ScriptSchemaValidationException;
import com.zkp.my12306.ntc.script.validate.ScriptSchemaValidator;
import com.zkp.my12306.ntc.service.CharacterService;
import com.zkp.my12306.ntc.service.ScriptRefineService;
import com.zkp.my12306.ntc.service.ScriptWorkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ScriptRefineServiceImpl implements ScriptRefineService {

    private static final Logger log = LoggerFactory.getLogger(ScriptRefineServiceImpl.class);
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final ScriptMessageMapper scriptMessageMapper;
    private final ScriptWorkService scriptWorkService;
    private final CharacterService characterService;
    private final ScriptRefinePromptBuilder refinePromptBuilder;
    private final ScriptOutputParser outputParser;
    private final ScriptSchemaValidator schemaValidator;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public ScriptRefineServiceImpl(
            ScriptMessageMapper scriptMessageMapper,
            ScriptWorkService scriptWorkService,
            CharacterService characterService,
            ScriptRefinePromptBuilder refinePromptBuilder,
            ScriptOutputParser outputParser,
            ScriptSchemaValidator schemaValidator,
            LLMService llmService,
            ObjectMapper objectMapper) {
        this.scriptMessageMapper = scriptMessageMapper;
        this.scriptWorkService = scriptWorkService;
        this.characterService = characterService;
        this.refinePromptBuilder = refinePromptBuilder;
        this.outputParser = outputParser;
        this.schemaValidator = schemaValidator;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void validateRefineRequest(ScriptRefineRequestDto request) {
        if (request == null) {
            throw new ScriptRecordValidationException("请求体不能为空");
        }
        if (request.chapterNumber() == null || request.chapterNumber() < 1) {
            throw new ScriptRecordValidationException("章节编号无效");
        }
        if (request.instruction() == null || request.instruction().isBlank()) {
            throw new ScriptRecordValidationException("修改要求不能为空");
        }
        if (request.currentScriptContent() == null || request.currentScriptContent().isBlank()) {
            throw new ScriptRecordValidationException("当前剧本内容不能为空");
        }
    }

    @Override
    public List<ScriptMessageResponseDto> listMessages(String currentUser, String workId, int chapterNumber) {
        scriptWorkService.requireOwnedWork(currentUser, workId);
        if (chapterNumber < 1) {
            throw new ScriptRecordValidationException("章节编号无效");
        }
        List<ScriptMessageDO> messages = scriptMessageMapper.selectList(Wrappers.lambdaQuery(ScriptMessageDO.class)
                .eq(ScriptMessageDO::getWorkId, workId.trim())
                .eq(ScriptMessageDO::getChapterNumber, chapterNumber)
                .orderByAsc(ScriptMessageDO::getCreateTime));
        return messages.stream().map(this::toResponse).toList();
    }

    @Override
    @TraceRoot(name = "scriptRefineStream", conversationIdArg = "workId", taskIdArg = "generationId")
    public void streamRefineChapter(
            ScriptRefineRequestDto request,
            String workId,
            String generationId,
            String currentUser,
            SseEmitter emitter) {
        validateRefineRequest(request);
        ScriptWorkDO work = scriptWorkService.requireOwnedWork(currentUser, workId);
        int chapterNumber = request.chapterNumber();
        String instruction = request.instruction().trim();
        String currentScriptContent = request.currentScriptContent().trim();
        String workTitle = resolveWorkTitle(work.getTitle());
        List<CharacterPromptItem> characters = characterService.listForPrompt(currentUser, workId);

        List<ScriptMessageDO> history = scriptMessageMapper.selectList(Wrappers.lambdaQuery(ScriptMessageDO.class)
                .eq(ScriptMessageDO::getWorkId, workId.trim())
                .eq(ScriptMessageDO::getChapterNumber, chapterNumber)
                .orderByAsc(ScriptMessageDO::getCreateTime));

        String userMessageContent = history.isEmpty()
                ? refinePromptBuilder.buildFirstUserMessage(currentScriptContent, instruction)
                : instruction;
        String traceId = LlmTraceContext.getTraceId();

        List<ChatMessage> messages = buildChatMessages(
                workTitle,
                chapterNumber,
                characters,
                history,
                userMessageContent);
        saveMessage(workId, chapterNumber, "user", userMessageContent, traceId);

        log.info("开始流式改编剧本: user={}, workId={}, chapter={}, historyCount={}, instructionLength={}",
                currentUser,
                workId,
                chapterNumber,
                history.size(),
                instruction.length());

        AtomicReference<StreamCancellationHandle> handleRef = new AtomicReference<>();
        AtomicBoolean streamFinished = new AtomicBoolean(false);
        StringBuilder accumulated = new StringBuilder();

        StreamCallback emitterCallback = new StreamCallback() {
            @Override
            public void onOpen(String modelName) {
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
                String revisedScript = accumulated.toString().trim();
                String persistedScript = emitStreamCompletionFeedback(emitter, revisedScript);
                if (!persistedScript.isBlank()) {
                    saveMessage(workId, chapterNumber, "assistant", persistedScript, traceId);
                }
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
        StreamCancellationHandle handle = llmService.streamChat(messages, guardedCallback);
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

    private List<ChatMessage> buildChatMessages(
            String workTitle,
            int chapterNumber,
            List<CharacterPromptItem> characters,
            List<ScriptMessageDO> history,
            String latestUserMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(
                "system",
                refinePromptBuilder.buildSystemPrompt(workTitle, chapterNumber, characters)));

        List<ScriptMessageDO> contextHistory = trimHistory(history);
        for (ScriptMessageDO item : contextHistory) {
            if ("user".equalsIgnoreCase(item.getRole()) || "assistant".equalsIgnoreCase(item.getRole())) {
                messages.add(new ChatMessage(item.getRole(), item.getContent()));
            }
        }
        if (history.isEmpty() || !latestUserMessage.equals(history.get(history.size() - 1).getContent())) {
            messages.add(new ChatMessage("user", latestUserMessage));
        }
        return messages;
    }

    private List<ScriptMessageDO> trimHistory(List<ScriptMessageDO> history) {
        if (history.size() <= MAX_CONTEXT_MESSAGES) {
            return history;
        }
        return history.subList(history.size() - MAX_CONTEXT_MESSAGES, history.size());
    }

    private void saveMessage(String workId, int chapterNumber, String role, String content, String traceId) {
        ScriptMessageDO message = new ScriptMessageDO();
        message.setId(UUID.randomUUID().toString());
        message.setWorkId(workId.trim());
        message.setChapterNumber(chapterNumber);
        message.setRole(role);
        message.setContent(content);
        message.setTraceId(traceId);
        message.setCreateTime(LocalDateTime.now());
        message.setDeleted(0);
        scriptMessageMapper.insert(message);
    }

    private ScriptMessageResponseDto toResponse(ScriptMessageDO message) {
        return new ScriptMessageResponseDto(
                message.getId(),
                message.getWorkId(),
                message.getChapterNumber(),
                message.getRole(),
                message.getContent(),
                message.getTraceId(),
                formatDateTime(message.getCreateTime()));
    }

    private String resolveWorkTitle(String title) {
        if (title == null || title.isBlank()) {
            return "未命名作品";
        }
        return title.trim();
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

    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, TEXT_PLAIN_UTF8));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private String emitStreamCompletionFeedback(SseEmitter emitter, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            ScriptDocument document = outputParser.parse(content);
            if (document == null || document.root() == null) {
                sendSseEvent(emitter, "warn", "输出无法解析为合法 YAML，请换用更强模型后重试");
                return "";
            }
            if (isNaturalScriptDocument(document)) {
                sendSseEvent(emitter, "warn", "输出未采用 YAML 格式，请换用更强模型后重试");
                return "";
            }
            schemaValidator.validateChapterFragment(document);
            String yaml = document.toYaml();
            sendSseEvent(emitter, "artifact", yaml);
            return yaml;
        } catch (ScriptSchemaValidationException ex) {
            sendSseEvent(emitter, "warn", "YAML 结构校验未通过：" + ex.getMessage());
        } catch (ScriptOutputException ex) {
            sendSseEvent(emitter, "warn", "输出无法解析为合法 YAML，请换用更强模型后重试");
        } catch (RuntimeException ex) {
            log.warn("改编完成后的 YAML 处理失败", ex);
            sendSseEvent(emitter, "warn", "输出无法解析为合法 YAML，请换用更强模型后重试");
        }
        return "";
    }

    private boolean isNaturalScriptDocument(ScriptDocument document) {
        return document != null
                && document.root() != null
                && "natural_script".equals(document.root().path("format").asText());
    }

    private void cancelStream(StreamCancellationHandle handle, SseEmitter emitter) {
        if (handle != null && !handle.isCancelled()) {
            handle.cancel();
        }
        if (emitter != null) {
            emitter.complete();
        }
    }

    private String resolveStreamErrorMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? "改编失败，请稍后重试" : message;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
