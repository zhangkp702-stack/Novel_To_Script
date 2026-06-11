package com.zkp.my12306.ntc.llm.trace;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 生成 trace 主键与链路标识。
 */
public final class TraceIdGenerator {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    private TraceIdGenerator() {
    }

    public static long nextId() {
        return SEQUENCE.incrementAndGet();
    }

    public static String nextTraceId() {
        return String.valueOf(nextId());
    }

    public static String nextNodeId() {
        return String.valueOf(nextId());
    }
}
