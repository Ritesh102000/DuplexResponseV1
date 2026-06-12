package com.voicedemo.gateway.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicRouterTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HeuristicRouter router = new HeuristicRouter();

    @Test
    void classifiesPhaseZeroLabelSet() throws Exception {
        List<String> mistakes = Files.readAllLines(Path.of("../docs/eval/router-labels.jsonl"))
                .stream()
                .map(this::classify)
                .filter(result -> !result.correct())
                .map(result -> result.id() + " expected=" + result.expected() + " actual=" + result.actual())
                .toList();

        assertThat(mistakes).isEmpty();
    }

    @Test
    void distinguishesAmbiguousChatAskAndActCases() {
        assertThat(router.classify(List.of(), "can you believe how expensive flights are").label())
                .isEqualTo(RouteLabel.CHAT);
        assertThat(router.classify(List.of(), "how expensive are flights to Goa").label())
                .isEqualTo(RouteLabel.ASK);
        assertThat(router.classify(List.of(), "send a message to Rahul").label())
                .isEqualTo(RouteLabel.ACT);
    }

    private Result classify(String line) {
        try {
            JsonNode json = objectMapper.readTree(line);
            RouteLabel expected = RouteLabel.valueOf(json.get("label").asText());
            RouteLabel actual = router.classify(List.of(), json.get("text").asText()).label();
            return new Result(json.get("id").asText(), expected, actual);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Result(String id, RouteLabel expected, RouteLabel actual) {
        boolean correct() {
            return expected == actual;
        }
    }
}

