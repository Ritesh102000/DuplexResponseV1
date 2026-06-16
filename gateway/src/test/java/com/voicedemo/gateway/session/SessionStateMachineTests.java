package com.voicedemo.gateway.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class SessionStateMachineTests {
    private final SessionStateMachine stateMachine = new SessionStateMachine();

    @Test
    void implementsLegacyAndQwenStateTablesExhaustively() {
        Map<Transition, SessionStatus> expected = Map.ofEntries(
                entry(key(SessionStatus.IDLE, SessionEvent.CLIENT_AND_MOSHI_OPEN), SessionStatus.LISTENING),
                entry(key(SessionStatus.LISTENING, SessionEvent.USER_UTTERANCE), SessionStatus.ROUTING),
                entry(key(SessionStatus.LISTENING, SessionEvent.ROUTER_CHAT), SessionStatus.FAST_THINKING),
                entry(key(SessionStatus.LISTENING, SessionEvent.ROUTER_ASK_PENDING), SessionStatus.ASK_PENDING),
                entry(key(SessionStatus.ROUTING, SessionEvent.ROUTER_CHAT), SessionStatus.FAST_THINKING),
                entry(key(SessionStatus.ROUTING, SessionEvent.ROUTER_ASK_PENDING), SessionStatus.ASK_PENDING),
                entry(key(SessionStatus.ROUTING, SessionEvent.ROUTER_ASK), SessionStatus.ASK_IN_FLIGHT),
                entry(key(SessionStatus.FAST_THINKING, SessionEvent.FAST_REPLY_READY), SessionStatus.FAST_SPEAKING),
                entry(key(SessionStatus.FAST_SPEAKING, SessionEvent.TTS_STREAM_ENDS), SessionStatus.LISTENING),
                entry(key(SessionStatus.FAST_SPEAKING, SessionEvent.USER_SPEECH), SessionStatus.LISTENING),
                entry(key(SessionStatus.ASK_PENDING, SessionEvent.USER_UTTERANCE), SessionStatus.ASK_PENDING_FAST_THINKING),
                entry(key(SessionStatus.ASK_PENDING, SessionEvent.FAST_REPLY_REQUESTED), SessionStatus.ASK_PENDING_FAST_THINKING),
                entry(key(SessionStatus.ASK_PENDING, SessionEvent.BACKEND_ANSWER_READY), SessionStatus.ANSWER_READY),
                entry(key(SessionStatus.ASK_PENDING, SessionEvent.ROUTER_ASK_PENDING), SessionStatus.ASK_PENDING),
                entry(key(SessionStatus.ASK_PENDING_FAST_THINKING, SessionEvent.FAST_REPLY_READY), SessionStatus.ASK_PENDING_FAST_SPEAKING),
                entry(key(SessionStatus.ASK_PENDING_FAST_THINKING, SessionEvent.BACKEND_ANSWER_READY), SessionStatus.ANSWER_READY),
                entry(key(SessionStatus.ASK_PENDING_FAST_SPEAKING, SessionEvent.TTS_STREAM_ENDS), SessionStatus.ASK_PENDING),
                entry(key(SessionStatus.ASK_PENDING_FAST_SPEAKING, SessionEvent.USER_SPEECH), SessionStatus.ASK_PENDING),
                entry(key(SessionStatus.ASK_PENDING_FAST_SPEAKING, SessionEvent.BACKEND_ANSWER_READY), SessionStatus.ANSWER_READY),
                entry(key(SessionStatus.ANSWER_READY, SessionEvent.BACKEND_INJECT_STARTED), SessionStatus.BACKEND_INJECTING),
                entry(key(SessionStatus.BACKEND_INJECTING, SessionEvent.TTS_STREAM_ENDS), SessionStatus.LISTENING),
                entry(key(SessionStatus.BACKEND_INJECTING, SessionEvent.USER_SPEECH), SessionStatus.LISTENING),
                entry(key(SessionStatus.LISTENING, SessionEvent.MOSHI_AUDIO_STARTS), SessionStatus.MOSHI_TALKING),
                entry(key(SessionStatus.LISTENING, SessionEvent.ROUTER_ASK), SessionStatus.ASK_IN_FLIGHT),
                entry(key(SessionStatus.MOSHI_TALKING, SessionEvent.MOSHI_AUDIO_IDLE), SessionStatus.LISTENING),
                entry(key(SessionStatus.MOSHI_TALKING, SessionEvent.ROUTER_ASK), SessionStatus.ASK_IN_FLIGHT),
                entry(key(SessionStatus.ASK_IN_FLIGHT, SessionEvent.JOB_RESULT_FRESH), SessionStatus.INJECTING),
                entry(key(SessionStatus.ASK_IN_FLIGHT, SessionEvent.JOB_RESULT_STALE_REINTRODUCE), SessionStatus.INJECTING),
                entry(key(SessionStatus.ASK_IN_FLIGHT, SessionEvent.JOB_RESULT_STALE_DROP), SessionStatus.LISTENING),
                entry(key(SessionStatus.ASK_IN_FLIGHT, SessionEvent.JOB_TIMEOUT), SessionStatus.INJECTING),
                entry(key(SessionStatus.INJECTING, SessionEvent.TTS_STREAM_ENDS), SessionStatus.LISTENING),
                entry(key(SessionStatus.INJECTING, SessionEvent.USER_SPEECH), SessionStatus.LISTENING)
        );

        for (SessionStatus status : SessionStatus.values()) {
            for (SessionEvent event : SessionEvent.values()) {
                SessionStatus expectedStatus = event == SessionEvent.MOSHI_WS_DROP
                        ? SessionStatus.IDLE
                        : expected.getOrDefault(key(status, event), status);

                assertThat(stateMachine.transition(status, event))
                        .as("%s + %s", status, event)
                        .isEqualTo(expectedStatus);
            }
        }
    }

    private Transition key(SessionStatus status, SessionEvent event) {
        return new Transition(status, event);
    }

    private record Transition(SessionStatus status, SessionEvent event) {
    }
}
