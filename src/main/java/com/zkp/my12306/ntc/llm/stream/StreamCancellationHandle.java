package com.zkp.my12306.ntc.llm.stream;

public interface StreamCancellationHandle {

    void cancel();

    boolean isCancelled();
}
