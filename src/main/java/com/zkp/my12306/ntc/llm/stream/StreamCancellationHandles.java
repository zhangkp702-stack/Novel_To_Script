package com.zkp.my12306.ntc.llm.stream;

import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamCancellationHandles {
    private StreamCancellationHandles() {
    }

    public static StreamCancellationHandle noOp() {
        return new StreamCancellationHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    public static StreamCancellationHandle of(Runnable cancelAction) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        return new StreamCancellationHandle() {
            @Override
            public void cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    cancelAction.run();
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }
}
