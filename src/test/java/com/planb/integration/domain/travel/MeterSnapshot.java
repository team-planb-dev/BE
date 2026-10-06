package com.planb.integration.domain.travel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 실행 전후 meter 값의 태그별 차이 계산
 *
 * 태그를 합산하지 않고 태그 조합마다 따로 기록
 * (gen_ai.token.type의 input·output·total을 더해 2배로 세는 오류 방지)
 */
public final class MeterSnapshot {

    // meter 이름 → 태그 조합 → [count, totalMillis 또는 counter 값]
    private final Map<String, Map<String, double[]>> values;

    private MeterSnapshot(Map<String, Map<String, double[]>> values) {

        this.values = values;
    }

    public static MeterSnapshot take(
            MeterRegistry meterRegistry,
            List<String> names
    ) {

        Map<String, Map<String, double[]>> values = new LinkedHashMap<>();

        for (String name : names) {
            Map<String, double[]> byTags = new TreeMap<>();

            for (Meter meter : meterRegistry
                    .find(name)
                    .meters()) {
                byTags.put(
                        tagKey(meter),
                        read(meter)
                );
            }

            values.put(
                    name,
                    byTags
            );
        }

        return new MeterSnapshot(values);
    }

    // after - before, 변화 없는 태그 조합 제외
    public Map<String, Map<String, Map<String, Double>>> deltaSince(MeterSnapshot before) {

        Map<String, Map<String, Map<String, Double>>> delta = new LinkedHashMap<>();

        values.forEach((name, byTags) -> {
            Map<String, Map<String, Double>> changed = new TreeMap<>();

            byTags.forEach((tags, after) -> {
                double[] previous = before.values
                        .getOrDefault(
                                name,
                                Map.of()
                        )
                        .getOrDefault(
                                tags,
                                new double[]{0, 0}
                        );

                double count = after[0] - previous[0];
                double amount = after[1] - previous[1];

                if (count != 0 || amount != 0) {
                    Map<String, Double> entry = new LinkedHashMap<>();
                    entry.put(
                            "count",
                            count
                    );
                    entry.put(
                            "amount",
                            amount
                    );
                    changed.put(
                            tags,
                            entry
                    );
                }
            });

            delta.put(
                    name,
                    changed
            );
        });

        return delta;
    }

    private static String tagKey(Meter meter) {

        return meter
                .getId()
                .getTags()
                .stream()
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .sorted()
                .collect(Collectors.joining(","));
    }

    // timer는 [횟수, 총 밀리초], counter는 [값, 값]
    private static double[] read(Meter meter) {

        if (meter instanceof Timer timer) {
            return new double[]{
                    timer.count(),
                    timer.totalTime(TimeUnit.MILLISECONDS)
            };
        }

        if (meter instanceof Counter counter) {
            return new double[]{
                    counter.count(),
                    counter.count()
            };
        }

        return new double[]{0, 0};
    }
}
