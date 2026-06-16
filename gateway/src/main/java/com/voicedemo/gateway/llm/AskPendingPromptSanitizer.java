package com.voicedemo.gateway.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class AskPendingPromptSanitizer {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final List<CategoryRule> CATEGORY_RULES = List.of(
            new CategoryRule("a factual geography question", List.of(
                    "capital", "cap of", "country", "city", "continent", "currency", "population", "located",
                    "where is", "map", "border", "river", "mountain"
            )),
            new CategoryRule("a medical or health question", List.of(
                    "medicine", "medical", "health", "dose", "dosage", "side effect", "symptom", "safe together",
                    "drug", "pill", "fever", "pain", "doctor", "pregnant", "allergy"
            )),
            new CategoryRule("a legal or rules question", List.of(
                    "legal", "law", "illegal", "allowed", "permitted", "regulation", "rights", "complaint",
                    "court", "contract", "policy requires"
            )),
            new CategoryRule("a finance or money question", List.of(
                    "finance", "money", "tax", "stock", "investment", "interest", "return", "loan", "mortgage",
                    "crypto", "salary", "price", "cost", "budget"
            )),
            new CategoryRule("a travel rules question", List.of(
                    "visa", "passport", "entry requirement", "travel", "flight", "airline", "airport",
                    "hotel", "immigration", "customs"
            )),
            new CategoryRule("a current events question", List.of(
                    "latest", "current", "news", "today", "yesterday", "election", "policy change",
                    "president", "prime minister", "war", "market today"
            )),
            new CategoryRule("a technical or coding question", List.of(
                    "code", "coding", "program", "java", "python", "javascript", "api", "server", "bug",
                    "error", "stack trace", "machine learning", "ai", "llm", "model"
            )),
            new CategoryRule("a calculation or math question", List.of(
                    "calculate", "math", "formula", "equation", "percent", "percentage", "average",
                    "sum", "divide", "multiply", "how many"
            )),
            new CategoryRule("a comparison question", List.of(
                    "compare", "comparison", "versus", " vs ", "better", "stronger", "difference between",
                    "which is better"
            )),
            new CategoryRule("a recommendation question", List.of(
                    "best", "recommend", "suggest", "which should", "what should", "pick", "choose",
                    "worth buying"
            )),
            new CategoryRule("a product facts question", List.of(
                    "spec", "specs", "release date", "model number", "phone", "laptop", "camera",
                    "battery", "screen"
            )),
            new CategoryRule("a person or biography question", List.of(
                    "who is", "born", "age", "net worth", "biography", "founder", "ceo", "actor",
                    "singer", "player"
            ))
    );

    public SanitizedAskPrompt sanitize(String pendingQuestion, String latestUserText, int maxWords) {
        String pending = normalize(pendingQuestion);
        String latest = normalize(latestUserText);
        String combined = (pending + " " + latest).trim();
        String state = pending.isBlank() || pending.equals(latest) ? "ASK_PENDING" : "ASK_PENDING_USER_CONTINUES";
        String category = categoryFor(combined);
        String context = contextFor(state, category);
        String intent = intentFor(state, combined);
        String rules = "STALL_ONLY=true; NO_FACTS=true; NO_NAMED_ENTITIES=true; "
                + "NO_NUMBERS_DATES_PLACES=true; STAY_WARM=true; UNDER_%d_WORDS=true".formatted(maxWords);
        return new SanitizedAskPrompt(state, context, intent, rules, maxWords);
    }

    private String contextFor(String state, String category) {
        if ("ASK_PENDING_USER_CONTINUES".equals(state)) {
            return "The user previously asked " + category
                    + "; the verified answer is still pending and the user is continuing.";
        }
        return "The user asked " + category + ".";
    }

    private String intentFor(String state, String text) {
        if ("ASK_PENDING_USER_CONTINUES".equals(state)) {
            if (containsAny(text, "hurry", "quick", "fast", "now", "again", "exact", "sure")) {
                return "User is checking progress while the verified answer is still pending.";
            }
            if (containsAny(text, "not sure", "confused", "i mean", "actually", "sorry")) {
                return "User is clarifying their need while the verified answer is pending.";
            }
            return "User is continuing naturally while the verified answer is pending.";
        }
        if (containsAny(text, "best", "recommend", "suggest", "pick", "choose", "worth buying")) {
            return "Wants a well-judged recommendation.";
        }
        if (containsAny(text, "compare", "versus", " vs ", "better", "stronger", "difference")) {
            return "Looking for an even-handed comparison.";
        }
        if (containsAny(text, "why", "how does", "explain", "meaning", "definition")) {
            return "Looking for a clear explanation.";
        }
        if (containsAny(text, "legal", "law", "medical", "medicine", "safe", "allowed", "should i")) {
            return "Wants careful guidance without guessing.";
        }
        if (containsAny(text, "what is", "who is", "when", "where", "capital", "cap of", "how many", "exact")) {
            return "Looking for an exact factual answer.";
        }
        return "Looking for a reliable answer.";
    }

    private String categoryFor(String text) {
        for (CategoryRule rule : CATEGORY_RULES) {
            if (containsAny(text, rule.terms().toArray(String[]::new))) {
                return rule.context();
            }
        }
        return "a factual question";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    public record SanitizedAskPrompt(
            String state,
            String sanitizedContext,
            String latestUserIntent,
            String rules,
            int maxWords) {
        public String promptText() {
            return """
                    STATE=%s
                    SANITIZED_CONTEXT=%s
                    LATEST_USER_INTENT=%s
                    RULES=%s

                    Reply as the fast conversational layer. Do not answer the pending factual question.
                    Keep it under %d words.
                    """.formatted(state, sanitizedContext, latestUserIntent, rules, maxWords);
        }
    }

    private record CategoryRule(String context, List<String> terms) {
    }
}
