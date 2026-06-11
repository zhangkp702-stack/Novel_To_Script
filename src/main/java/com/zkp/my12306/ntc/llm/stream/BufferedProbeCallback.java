package com.zkp.my12306.ntc.llm.stream;

/**
 * 首包探测期间的流式回调：token 立即下发，避免等探测完成或流结束后一次性刷出。
 * promoteAndFlush 仅用于探测成功时补发 onOpen / onComplete（若流在探测前已结束）。
 */
public class BufferedProbeCallback implements StreamCallback {
    private final StreamCallback downstream;
    private boolean promoted = false;
    private boolean openSent = false;
    private String modelName;
    private boolean completedBeforePromote = false;
    private Throwable errorBeforePromote;

    public BufferedProbeCallback(StreamCallback downstream) {
        this.downstream = downstream;
    }

    @Override
    public synchronized void onOpen(String modelName) {
        this.modelName = modelName;
        if (!openSent) {
            downstream.onOpen(modelName);
            openSent = true;
        }
    }

    @Override
    public synchronized void onToken(String token) {
        if (!promoted) {
            promoted = true;
            ensureOpenSent();
        }
        downstream.onToken(token);
    }

    @Override
    public synchronized void onComplete() {
        if (!promoted) {
            completedBeforePromote = true;
            return;
        }
        ensureOpenSent();
        downstream.onComplete();
    }

    @Override
    public synchronized void onError(Throwable throwable) {
        if (!promoted) {
            errorBeforePromote = throwable;
            return;
        }
        ensureOpenSent();
        downstream.onError(throwable);
    }

    public synchronized void promoteAndFlush() {
        if (promoted) {
            if (completedBeforePromote) {
                downstream.onComplete();
                completedBeforePromote = false;
            }
            return;
        }
        promoted = true;
        ensureOpenSent();
        if (completedBeforePromote) {
            downstream.onComplete();
            completedBeforePromote = false;
        }
    }

    public synchronized void clearBuffer() {
        completedBeforePromote = false;
        errorBeforePromote = null;
    }

    public synchronized Throwable getErrorBeforePromote() {
        return errorBeforePromote;
    }

    private void ensureOpenSent() {
        if (!openSent && modelName != null && !modelName.isBlank()) {
            downstream.onOpen(modelName);
            openSent = true;
        }
    }
}
