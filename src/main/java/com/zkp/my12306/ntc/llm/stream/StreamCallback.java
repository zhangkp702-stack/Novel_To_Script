package com.zkp.my12306.ntc.llm.stream;

public interface StreamCallback {

    default void onOpen(String modelName) {
    }

    default void onToken(String token) {
    }

    /**
     * 流已建立但尚未产出正文 token（如 reasoning 阶段）时调用，用于首包探测。
     */
    default void onStreamActivity() {
    }

    default void onComplete() {
    }

    default void onWarn(String message) {
    }

    default void onError(Throwable throwable) {
    }
}
