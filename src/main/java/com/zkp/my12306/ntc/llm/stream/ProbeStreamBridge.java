package com.zkp.my12306.ntc.llm.stream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ProbeStreamBridge implements StreamCallback {
    public enum FirstEventType {
        TOKEN, COMPLETE, ERROR, TIMEOUT
    }

    public record FirstEvent(FirstEventType type, Throwable throwable) {
    }

    private final StreamCallback delegate;
    private final CountDownLatch firstEventLatch = new CountDownLatch(1);
    private final AtomicReference<FirstEvent> firstEventRef = new AtomicReference<>();

    public ProbeStreamBridge(StreamCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onOpen(String modelName) {
        delegate.onOpen(modelName);
    }

    @Override
    public void onToken(String token) {
        markFirstEvent(FirstEventType.TOKEN, null);
        delegate.onToken(token);
    }

    @Override
    public void onStreamActivity() {
        markFirstEvent(FirstEventType.TOKEN, null);
        delegate.onStreamActivity();
    }

    @Override
    public void onComplete() {
        markFirstEvent(FirstEventType.COMPLETE, null);
        delegate.onComplete();
    }

    @Override
    public void onError(Throwable throwable) {
        markFirstEvent(FirstEventType.ERROR, throwable);
        delegate.onError(throwable);
    }

    public FirstEvent awaitFirstEvent(long timeoutMs) {
        try {
            boolean signalled = firstEventLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!signalled) {
                return new FirstEvent(FirstEventType.TIMEOUT, null);
            }
            FirstEvent event = firstEventRef.get();
            return event == null ? new FirstEvent(FirstEventType.TIMEOUT, null) : event;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new FirstEvent(FirstEventType.TIMEOUT, ex);
        }
    }

    private void markFirstEvent(FirstEventType eventType, Throwable throwable) {
        if (firstEventRef.compareAndSet(null, new FirstEvent(eventType, throwable))) {
            firstEventLatch.countDown();
        }
    }
}
