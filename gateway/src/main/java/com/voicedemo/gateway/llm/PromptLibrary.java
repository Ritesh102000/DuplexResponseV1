package com.voicedemo.gateway.llm;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptLibrary {
    private final String answerStyle;
    private final String harmonizer;
    private final String fastChat;
    private final String fastAskPending;

    public PromptLibrary() {
        this.answerStyle = read("prompts/answer_style.txt");
        this.harmonizer = read("prompts/harmonizer.txt");
        this.fastChat = read("prompts/fast_chat.txt");
        this.fastAskPending = read("prompts/fast_ask_pending.txt");
    }

    public String answerStyle() {
        return answerStyle;
    }

    public String harmonizer() {
        return harmonizer;
    }

    public String fastChat() {
        return fastChat;
    }

    public String fastAskPending() {
        return fastAskPending;
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load prompt " + path, e);
        }
    }
}
