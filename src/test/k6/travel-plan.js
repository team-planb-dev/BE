import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MANAGEMENT_URL = __ENV.MANAGEMENT_URL || 'http://localhost:8081';
const START_DATE = __ENV.STUB_PLAN_START_DATE || '2030-01-01';
const SCENARIO = __ENV.SCENARIO || 'smoke';

if (!['smoke', 'plan-throughput'].includes(SCENARIO)) {
    throw new Error(`지원하지 않는 시나리오입니다: ${SCENARIO}`);
}

const planDuration = new Trend(
    'plan_duration',
    true,
);

const planSuccess = new Rate('plan_success');

export const options = {
    scenarios: SCENARIO === 'smoke'
        ? {
            smoke: {
                executor: 'shared-iterations',
                exec: 'createPlan',
                vus: 1,
                iterations: Number(__ENV.SMOKE_ITERATIONS || 3),
                maxDuration: '2m',
                gracefulStop: '10s',
            },
        }
        : {
            'plan-throughput': {
                executor: 'constant-arrival-rate',
                exec: 'createPlan',
                rate: Number(__ENV.PLAN_RATE || 5),
                timeUnit: '1s',
                duration: __ENV.PLAN_DURATION || '30s',
                preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 5),
                maxVUs: Number(__ENV.MAX_VUS || 10),
                gracefulStop: '10s',
            },
        },
    thresholds: {
        plan_success: ['rate==1'],
        checks: ['rate==1'],
    },
    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'max',
        'p(90)',
        'p(95)',
        'p(99)',
    ],
};

export function setup() {
    const suffix = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
    const username = `planb-load-${suffix}@example.com`;
    const password = 'test1234!';

    const user = postJson(
        '/api/v1/user/create',
        {
            username,
            nickname: `load-${suffix}`,
            password,
            recoveryQuestion: 'FIRST_PET',
            recoveryAnswer: '콩이',
            ageRequirementAgreed: true,
            serviceTermsAgreed: true,
            privacyCollectionAgreed: true,
        },
        null,
        'setup create user',
    );

    requireResponse(
        user,
        201,
        '사용자 생성',
    );

    const login = postJson(
        '/login',
        {
            username,
            password,
        },
        null,
        'setup login',
    );

    requireResponse(
        login,
        200,
        '로그인',
    );

    const authorization = login.headers.Authorization;

    if (!authorization || !authorization.startsWith('Bearer ')) {
        fail('로그인 응답에 Bearer 토큰이 없습니다.');
    }

    const companion = postJson(
        '/api/v1/health/add-traveler',
        {
            travelerName: '부하 테스트 동행인',
            sensitiveAgree: true,
            hasMedication: false,
            healthInfo: {
                diseaseTypes: ['DIABETES'],
                walkType: 'MINIMAL',
            },
            mealInfo: {
                applied: false,
                breakfastApplied: false,
                breakfastTime: null,
                lunchApplied: false,
                lunchTime: null,
                dinnerApplied: false,
                dinnerTime: null,
            },
            foodInfoList: [],
            medicationInfoList: [],
        },
        authorization,
        'setup add companion',
    );

    requireResponse(
        companion,
        200,
        '동행인 생성',
    );

    const summary = http.get(
        `${BASE_URL}/api/v1/health/get-companion-summary`,
        requestParams(
            authorization,
            'setup get companion summary',
        ),
    );

    requireResponse(
        summary,
        200,
        '동행인 조회',
    );

    const companions = summary
        .json()
        .data
        .companionList;

    if (!companions || companions.length !== 1) {
        fail('부하 테스트 동행인 healthId를 결정할 수 없습니다.');
    }

    return {
        authorization,
        healthId: companions[0].healthId,
    };
}

export function createPlan(context) {
    const response = postJson(
        '/api/v1/travel/add-with-recommend',
        {
            travelName: `load-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
            locationDo: '서울',
            locationSigungu: '종로구',
            startDate: START_DATE,
            dateType: 'ONE_NIGHT_TWO_DAYS',
            transportation: 'CAR',
            decidedLocation: '서울역',
            plannedPlaces: [],
            travelStyle: 'LESS_WALK',
            travelTheme: 'NATURE',
            localFoods: [],
            recommendFoods: [],
            healthIds: [context.healthId],
        },
        context.authorization,
        'POST /api/v1/travel/add-with-recommend',
    );

    planDuration.add(response.timings.duration);

    const valid = check(response, {
        '일정 생성 HTTP 200': result => result.status === 200,
        '일정 생성 success=true': result => responseSuccess(result),
        '이틀 일정과 관광지 필드': result => validPlan(result),
    });

    planSuccess.add(valid);
}

export function teardown(context) {
    const response = http.get(
        `${MANAGEMENT_URL}/actuator/prometheus`,
        requestParams(
            context.authorization,
            'GET /actuator/prometheus',
        ),
    );

    if (response.status !== 200) {
        console.error(`Prometheus 수집 실패: HTTP ${response.status}`);
        return;
    }

    const prefixes = [
        'planb_',
        'hikaricp_connections_active',
        'hikaricp_connections_idle',
        'hikaricp_connections_pending',
        'http_server_requests_active',
        'jvm_memory_used_bytes',
        'jvm_threads_live_threads',
        'process_cpu_usage',
        'system_cpu_usage',
    ];

    const metrics = response
        .body
        .split('\n')
        .filter(line => prefixes.some(prefix => line.startsWith(prefix)))
        .join('\n');

    console.log(`PLANB_METRICS_BEGIN\n${metrics}\nPLANB_METRICS_END`);
}

function postJson(
    path,
    body,
    authorization,
    name,
) {
    return http.post(
        `${BASE_URL}${path}`,
        JSON.stringify(body),
        requestParams(
            authorization,
            name,
        ),
    );
}

function requestParams(
    authorization,
    name,
) {
    const headers = {
        'Content-Type': 'application/json',
    };

    if (authorization) {
        headers.Authorization = authorization;
    }

    return {
        headers,
        tags: {
            name,
        },
    };
}

function requireResponse(
    response,
    status,
    operation,
) {
    if (response.status !== status || !responseSuccess(response)) {
        fail(`${operation} 실패: HTTP ${response.status}`);
    }
}

function responseSuccess(response) {
    try {
        return response.json().success === true;
    } catch (error) {
        return false;
    }
}

function validPlan(response) {
    try {
        const days = response
            .json()
            .data
            .planDays;

        return days.length === 2
            && days.every(day => {
                const attractions = day
                    .schedules
                    .filter(schedule => schedule.courseType === 'ATTRACTION');

                return attractions.length === 2
                    && attractions.every(schedule => schedule
                        .locationName
                        .startsWith('스텁 관광지')
                        && schedule
                            .candidateId
                            .startsWith('tour:')
                        && Boolean(schedule.longitude)
                        && Boolean(schedule.latitude)
                        && schedule.travelMinutes === 15);
            });
    } catch (error) {
        return false;
    }
}
