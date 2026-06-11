package com.zkp.my12306.ntc.script.stream;

import com.zkp.my12306.ntc.llm.stream.StreamCallback;

import java.util.concurrent.atomic.AtomicBoolean;

public class StreamDegenerationGuard implements StreamCallback {

    private static final char REPLACEMENT_CHAR = '\uFFFD';
    private static final int MIN_CHARS_BEFORE_REPEAT_CHECK = 2500;
    static final String DEGENERATION_WARN_MESSAGE =
            "检测到输出退化（乱码或重复），已自动停止。建议换用备用模型后重试。";

    private final StreamCallback downstream;
    private final Runnable cancelUpstream;
    private final StringBuilder accumulated = new StringBuilder();
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public StreamDegenerationGuard(StreamCallback downstream, Runnable cancelUpstream) {
        this.downstream = downstream;
        this.cancelUpstream = cancelUpstream;
    }

    @Override
    public void onOpen(String modelName) {
        downstream.onOpen(modelName);
    }

    @Override
    public void onToken(String token) {
        if (stopped.get() || token == null || token.isEmpty()) {
            return;
        }
        if (token.indexOf(REPLACEMENT_CHAR) >= 0) {
            stopEarly();
            return;
        }
        accumulated.append(token);
        if (detectDegeneration(accumulated)) {
            stopEarly();
            return;
        }
        downstream.onToken(token);
    }

    @Override
    public void onComplete() {
        if (stopped.get()) {
            return;
        }
        downstream.onComplete();
    }

    @Override
    public void onError(Throwable throwable) {
        if (stopped.get()) {
            return;
        }
        downstream.onError(throwable);
    }

    private void stopEarly() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (cancelUpstream != null) {
            cancelUpstream.run();
        }
        downstream.onWarn(DEGENERATION_WARN_MESSAGE);
        downstream.onComplete();
    }

    static boolean detectDegeneration(CharSequence text) {
        if (indexOfReplacement(text) >= 0) {
            return true;
        }
        if (looksLikeYamlOutput(text)) {
            return false;
        }
        int length = text.length();
        if (length < MIN_CHARS_BEFORE_REPEAT_CHECK) {
            return false;
        }
        String tail = text.subSequence(Math.max(0, length - 320), length).toString();
        if (hasConsecutiveRun(tail, 3)) {
            return true;
        }
        return hasRepeatedPhrase(tail);
    }

    private static int indexOfReplacement(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == REPLACEMENT_CHAR) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasConsecutiveRun(String text, int threshold) {
        int run = 1;
        for (int i = 1; i < text.length(); i++) {
            char current = text.charAt(i);
            char previous = text.charAt(i - 1);
            if (current == previous && !Character.isWhitespace(current)) {
                run++;
                if (run >= threshold) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    private static boolean hasRepeatedPhrase(String text) {
        for (int phraseLen = 2; phraseLen <= 8; phraseLen++) {
            for (int start = 0; start <= text.length() - phraseLen * 3; start++) {
                String phrase = text.substring(start, start + phraseLen);
                if (phrase.isBlank()) {
                    continue;
                }
                int repeats = 0;
                int pos = start;
                while (pos + phraseLen <= text.length()
                        && text.substring(pos, pos + phraseLen).equals(phrase)) {
                    repeats++;
                    pos += phraseLen;
                }
                if (repeats >= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean looksLikeYamlOutput(CharSequence text) {
        if (text == null) {
            return false;
        }
        return text.toString().contains("文档类型:");
    }
}
