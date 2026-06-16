package com.voicedemo.gateway.session;

import org.springframework.stereotype.Component;

@Component
public class SessionStateMachine {
    public SessionStatus transition(SessionStatus current, SessionEvent event) {
        if (event == SessionEvent.MOSHI_WS_DROP) {
            return SessionStatus.IDLE;
        }
        return switch (current) {
            case IDLE -> event == SessionEvent.CLIENT_AND_MOSHI_OPEN
                    ? SessionStatus.LISTENING
                    : current;
            case LISTENING -> switch (event) {
                case USER_UTTERANCE -> SessionStatus.ROUTING;
                case ROUTER_CHAT -> SessionStatus.FAST_THINKING;
                case ROUTER_ASK_PENDING -> SessionStatus.ASK_PENDING;
                case MOSHI_AUDIO_STARTS -> SessionStatus.MOSHI_TALKING;
                case ROUTER_ASK -> SessionStatus.ASK_IN_FLIGHT;
                default -> current;
            };
            case ROUTING -> switch (event) {
                case ROUTER_CHAT -> SessionStatus.FAST_THINKING;
                case ROUTER_ASK_PENDING -> SessionStatus.ASK_PENDING;
                case ROUTER_ASK -> SessionStatus.ASK_IN_FLIGHT;
                default -> current;
            };
            case FAST_THINKING -> event == SessionEvent.FAST_REPLY_READY
                    ? SessionStatus.FAST_SPEAKING
                    : current;
            case FAST_SPEAKING -> switch (event) {
                case TTS_STREAM_ENDS, USER_SPEECH -> SessionStatus.LISTENING;
                default -> current;
            };
            case ASK_PENDING -> switch (event) {
                case USER_UTTERANCE, FAST_REPLY_REQUESTED -> SessionStatus.ASK_PENDING_FAST_THINKING;
                case BACKEND_ANSWER_READY -> SessionStatus.ANSWER_READY;
                case ROUTER_ASK_PENDING -> SessionStatus.ASK_PENDING;
                default -> current;
            };
            case ASK_PENDING_FAST_THINKING -> switch (event) {
                case FAST_REPLY_READY -> SessionStatus.ASK_PENDING_FAST_SPEAKING;
                case BACKEND_ANSWER_READY -> SessionStatus.ANSWER_READY;
                default -> current;
            };
            case ASK_PENDING_FAST_SPEAKING -> switch (event) {
                case TTS_STREAM_ENDS, USER_SPEECH -> SessionStatus.ASK_PENDING;
                case BACKEND_ANSWER_READY -> SessionStatus.ANSWER_READY;
                default -> current;
            };
            case ANSWER_READY -> event == SessionEvent.BACKEND_INJECT_STARTED
                    ? SessionStatus.BACKEND_INJECTING
                    : current;
            case BACKEND_INJECTING -> switch (event) {
                case TTS_STREAM_ENDS, USER_SPEECH -> SessionStatus.LISTENING;
                default -> current;
            };
            case MOSHI_TALKING -> switch (event) {
                case MOSHI_AUDIO_IDLE -> SessionStatus.LISTENING;
                case ROUTER_ASK -> SessionStatus.ASK_IN_FLIGHT;
                default -> current;
            };
            case ASK_IN_FLIGHT -> switch (event) {
                case JOB_RESULT_FRESH, JOB_RESULT_STALE_REINTRODUCE, JOB_TIMEOUT -> SessionStatus.INJECTING;
                case JOB_RESULT_STALE_DROP -> SessionStatus.LISTENING;
                default -> current;
            };
            case INJECTING -> switch (event) {
                case TTS_STREAM_ENDS, USER_SPEECH -> SessionStatus.LISTENING;
                default -> current;
            };
        };
    }
}
