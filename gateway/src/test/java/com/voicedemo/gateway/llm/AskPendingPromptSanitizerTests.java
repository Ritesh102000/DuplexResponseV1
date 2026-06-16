package com.voicedemo.gateway.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskPendingPromptSanitizerTests {
    private final AskPendingPromptSanitizer sanitizer = new AskPendingPromptSanitizer();

    @Test
    void removesRawGeographyQuestionFromInitialAskPrompt() {
        AskPendingPromptSanitizer.SanitizedAskPrompt prompt = sanitizer.sanitize(
                "what is the capital of australia",
                "what is the capital of australia",
                18
        );

        String text = prompt.promptText().toLowerCase();

        assertThat(prompt.state()).isEqualTo("ASK_PENDING");
        assertThat(text).contains("factual geography question");
        assertThat(text).contains("looking for an exact factual answer");
        assertThat(text).doesNotContain("australia");
        assertThat(text).doesNotContain("canberra");
        assertThat(text).doesNotContain("what is the capital");
    }

    @Test
    void abstractsSlangContinuationWithoutLeakingRawText() {
        AskPendingPromptSanitizer.SanitizedAskPrompt prompt = sanitizer.sanitize(
                "what is the capital of australia",
                "whats the cap of aus",
                18
        );

        String text = prompt.promptText().toLowerCase();

        assertThat(prompt.state()).isEqualTo("ASK_PENDING_USER_CONTINUES");
        assertThat(text).contains("verified answer is still pending");
        assertThat(text).contains("factual geography question");
        assertThat(text).doesNotContain("australia");
        assertThat(text).doesNotContain("aus");
        assertThat(text).doesNotContain("cap of");
    }

    @Test
    void categorizesHighStakesQuestionsWithoutSpecificFacts() {
        AskPendingPromptSanitizer.SanitizedAskPrompt prompt = sanitizer.sanitize(
                "can i take these two medicines together",
                "can i take these two medicines together",
                18
        );

        String text = prompt.promptText().toLowerCase();

        assertThat(text).contains("medical or health question");
        assertThat(text).contains("careful guidance without guessing");
        assertThat(text).doesNotContain("two medicines");
    }
}
