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
                case MOSHI_AUDIO_STARTS -> SessionStatus.MOSHI_TALKING;
                case ROUTER_ASK -> SessionStatus.ASK_IN_FLIGHT;
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
