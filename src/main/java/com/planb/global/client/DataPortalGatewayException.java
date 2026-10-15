package com.planb.global.client;

import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공공데이터포털 게이트웨이 오류 본문(XML)의 반환 코드
 *
 * JSON 요청에도 게이트웨이 오류는 XML로만 출력(TourAPI 활용매뉴얼 v4.4, 같은 게이트웨이의 다른 API는 포털 공통 FAQ 기준 추론)
 * 22 일일 한도, 23 초당 한도 등의 구분 보존
 * 호출부의 기존 디코딩 실패 처리(CodecException)를 유지하도록 DecodingException 하위 타입
 */
public class DataPortalGatewayException
        extends DecodingException {

    private static final Pattern REASON_CODE = Pattern.compile(
            "<returnReasonCode>\\s*([0-9]{1,4})\\s*</returnReasonCode>"
    );

    // 상수명 형식만 보존, 그 밖의 본문 내용은 메시지에 넣지 않음
    private static final Pattern AUTH_MESSAGE = Pattern.compile(
            "<returnAuthMsg>\\s*([A-Z0-9_]{1,80})\\s*</returnAuthMsg>"
    );

    private final String reasonCode;

    private DataPortalGatewayException(
            String reasonCode,
            String authMessage
    ) {

        super("data.go.kr gateway error: returnReasonCode=" + reasonCode
                + (authMessage == null ? "" : ", returnAuthMsg=" + authMessage));

        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }

    // 게이트웨이 오류 XML의 반환 코드, 해당 형식이 아니면 null
    public static String reasonCode(String body) {

        if (body == null || !body
                .stripLeading()
                .startsWith("<")) {
            return null;
        }

        Matcher matcher = REASON_CODE.matcher(body);

        return matcher.find() ? matcher.group(1) : null;
    }

    // 2xx 본문이 게이트웨이 오류 XML이면 JSON 해석 전에 이 예외로 전환, 그 밖의 본문은 같은 바이트로 전달
    // 오류 상태 본문은 WebClientResponseException이 보존하므로 그대로 둠
    public static ExchangeFilterFunction detector(int maxBytes) {

        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (!response
                    .statusCode()
                    .is2xxSuccessful()) {
                return Mono.just(response);
            }

            return Mono.just(response
                    .mutate()
                    .body(body -> DataBufferUtils
                            .join(
                                    body,
                                    maxBytes
                            )
                            .flatMapMany(buffer -> {
                                DataPortalGatewayException gateway = from(buffer);

                                if (gateway == null) {
                                    return Flux.just(buffer);
                                }

                                DataBufferUtils.release(buffer);

                                return Flux.error(gateway);
                            }))
                    .build());
        });
    }

    private static DataPortalGatewayException from(DataBuffer buffer) {

        // JSON 본문은 문자열 변환 없이 통과
        if (!startsWithMarkup(buffer)) {
            return null;
        }

        String body = buffer.toString(
                buffer.readPosition(),
                buffer.readableByteCount(),
                StandardCharsets.UTF_8
        );

        String code = reasonCode(body);

        if (code == null) {
            return null;
        }

        Matcher authMessage = AUTH_MESSAGE.matcher(body);

        return new DataPortalGatewayException(
                code,
                authMessage.find() ? authMessage.group(1) : null
        );
    }

    private static boolean startsWithMarkup(DataBuffer buffer) {

        for (int index = buffer.readPosition(); index < buffer.writePosition(); index++) {
            byte value = buffer.getByte(index);

            if (!Character.isWhitespace(value)) {
                return value == '<';
            }
        }

        return false;
    }
}
