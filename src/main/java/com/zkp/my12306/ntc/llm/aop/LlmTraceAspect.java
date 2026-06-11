package com.zkp.my12306.ntc.llm.aop;

import com.zkp.my12306.ntc.llm.config.LlmTraceProperties;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceNodeDO;
import com.zkp.my12306.ntc.llm.dao.entity.LlmTraceRunDO;
import com.zkp.my12306.ntc.llm.service.LlmTraceRecordService;
import com.zkp.my12306.ntc.llm.trace.LlmTraceContext;
import com.zkp.my12306.ntc.llm.trace.TraceIdGenerator;
import com.zkp.my12306.ntc.llm.trace.TraceNode;
import com.zkp.my12306.ntc.llm.trace.TraceRoot;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LlmTraceAspect {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final LlmTraceRecordService traceRecordService;
    private final LlmTraceProperties traceProperties;

    public LlmTraceAspect(LlmTraceRecordService traceRecordService, LlmTraceProperties traceProperties) {
        this.traceRecordService = traceRecordService;
        this.traceProperties = traceProperties;
    }

    @Around("@annotation(traceRoot)")
    public Object aroundRoot(ProceedingJoinPoint joinPoint, TraceRoot traceRoot) throws Throwable {
        if (!traceProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        String existingTraceId = LlmTraceContext.getTraceId();
        if (isNotBlank(existingTraceId)) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String traceId = TraceIdGenerator.nextTraceId();
        String conversationId = resolveStringArg(signature, joinPoint.getArgs(), traceRoot.conversationIdArg());
        String taskId = resolveStringArg(signature, joinPoint.getArgs(), traceRoot.taskIdArg());
        String traceName = defaultIfBlank(traceRoot.name(), method.getName());
        LocalDateTime startTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();

        LlmTraceRunDO run = new LlmTraceRunDO();
        run.setTraceId(traceId);
        run.setTraceName(traceName);
        run.setEntryMethod(method.getDeclaringClass().getName() + "#" + method.getName());
        run.setConversationId(conversationId);
        run.setTaskId(taskId);
        run.setUserId(resolveUserId());
        run.setStatus(STATUS_RUNNING);
        run.setStartTime(startTime);
        traceRecordService.startRun(run);

        LlmTraceContext.setTraceId(traceId);
        if (isNotBlank(taskId)) {
            LlmTraceContext.setTaskId(taskId);
        }
        try {
            Object result = joinPoint.proceed();
            traceRecordService.finishRun(
                    traceId,
                    STATUS_SUCCESS,
                    null,
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis);
            return result;
        } catch (Throwable ex) {
            traceRecordService.finishRun(
                    traceId,
                    STATUS_ERROR,
                    truncateError(ex),
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis);
            throw ex;
        } finally {
            LlmTraceContext.clear();
        }
    }

    @Around("@annotation(traceNode)")
    public Object aroundNode(ProceedingJoinPoint joinPoint, TraceNode traceNode) throws Throwable {
        if (!traceProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        String traceId = LlmTraceContext.getTraceId();
        if (!isNotBlank(traceId)) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String nodeId = TraceIdGenerator.nextNodeId();
        String parentNodeId = LlmTraceContext.currentNodeId();
        int depth = LlmTraceContext.depth();
        LocalDateTime startTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();

        LlmTraceNodeDO node = new LlmTraceNodeDO();
        node.setTraceId(traceId);
        node.setNodeId(nodeId);
        node.setParentNodeId(parentNodeId);
        node.setDepth(depth);
        node.setNodeType(defaultIfBlank(traceNode.type(), "METHOD"));
        node.setNodeName(defaultIfBlank(traceNode.name(), method.getName()));
        node.setClassName(method.getDeclaringClass().getName());
        node.setMethodName(method.getName());
        node.setStatus(STATUS_RUNNING);
        node.setStartTime(startTime);
        traceRecordService.startNode(node);

        LlmTraceContext.pushNode(nodeId);
        try {
            Object result = joinPoint.proceed();
            traceRecordService.finishNode(
                    traceId,
                    nodeId,
                    STATUS_SUCCESS,
                    null,
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis);
            return result;
        } catch (Throwable ex) {
            traceRecordService.finishNode(
                    traceId,
                    nodeId,
                    STATUS_ERROR,
                    truncateError(ex),
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis);
            throw ex;
        } finally {
            LlmTraceContext.popNode();
        }
    }

    private String resolveStringArg(MethodSignature signature, Object[] args, String argName) {
        if (!isNotBlank(argName) || args == null || args.length == 0) {
            return null;
        }
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames == null || parameterNames.length != args.length) {
            return null;
        }
        for (int i = 0; i < parameterNames.length; i++) {
            if (!argName.equals(parameterNames[i])) {
                continue;
            }
            Object arg = args[i];
            if (arg == null) {
                return null;
            }
            return String.valueOf(arg);
        }
        return null;
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (!isNotBlank(name) || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": "
                + defaultIfBlank(throwable.getMessage(), "");
        int maxLength = traceProperties.getMaxErrorLength();
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isNotBlank(value) ? value : fallback;
    }
}
