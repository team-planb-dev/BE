package com.planb.ai.client;

/**
 * 요청 단위 AI correction 호출 발생 여부 기록
 *
 * OpenAiClient의 correction 재시도와 일정 생성 지표를 연결
 * AI 호출이 요청 스레드에서 동기 실행되는 흐름 전제
 */
public final class AiCorrectionTracker {

    private static final ThreadLocal<Boolean> CORRECTED = new ThreadLocal<>();

    private AiCorrectionTracker() {
    }

    // 기록 시작 (일정 생성 진입 직후)
    public static void start() {

        CORRECTED.set(Boolean.FALSE);
    }

    // correction 호출 발생 표시, 기록 중이 아니면 무시
    public static void mark() {

        if (CORRECTED.get() != null) {
            CORRECTED.set(Boolean.TRUE);
        }
    }

    // 기록 종료, 발생 여부 반환 후 ThreadLocal 정리
    public static boolean finish() {

        boolean corrected = Boolean.TRUE.equals(CORRECTED.get());
        CORRECTED.remove();

        return corrected;
    }
}
