# P2-4C 무료 검증 결과와 실측 준비

작성일: 2026-10-10 · Issue #173 · 상태: 구현 검증 완료, 실제 API12회 완료·채택 미달

## 기준 코드

- 작업 트리: `/Users/wooju-kang/.codex/worktrees/p2-4c-candidate-prefetch/planB`
- 브랜치: `173-perf-prefetch-travel-generation-candidates-before-ai-selection`
- 기준 commit: `2a99375f1316b2652bf6965e61e2f21a4665afde` — 채택한 P2-4A
- P2-4B 구현 제외. 이번 변경은 미커밋 상태이며 소스 SHA256은 `evidence/p2-4c-20261010/free-validation-manifest.json`에 기록.

## 변경과 책임

Java가 관광지·음식점 후보를 선조회하고, AI는 후보 선택과 기존 상세·영양·경로 Tool 수행. 생성에 노출하는 Tool은 6개에서 4개로 변경. 후보 준비는 `PlanCandidatePrefetcher`, 불변 결과는 `PlanCandidateSnapshot`, Tool 노출은 `PrefetchedPlanTourismTool`, 후보 입력은 생성·재선택 prompt 책임. 기존 편집·날짜 재구성 검색 Tool은 유지. Facade·DB 트랜잭션·새 라이브러리 변경 없음.

관광지와 음식점 branch만 동시 진행. 음식점 keyword는 순차 검색. 중복 ID 제거, keyword 8개·keyword별 8개·전체 음식점 40개 상한, 필수 식사 수 부족 시 같은 지역 fallback 1회, 전체 선조회 60초 상한. 후보 상한은 기존 검색과 다른 계약이므로 실제 품질 비교 대상.

선조회 결과와 지정 장소는 전체 성공 후 요청 context에 기록. correction은 seed 복원, 생성 재선택은 최초 생성 완료 후보를 복사한 새 context 사용. 최초 카페 보존과 실패 재선택의 신규 후보 격리. 알 수 없는 ID·빈 ID·잘못된 type 거부. 빈 HTTP 응답과 공급자 실패 코드는 정상 0건과 구분.

마지막 오류 점검에서 선조회 예외의 응답 분류 누락 발견. 공개 handler 테스트의 RED 확인 후 `UPSTREAM_CALL_FAILED`로 변환. 원인 예외 보존, AI 호출 생략. 기존 `AI_TEMPORARILY_UNAVAILABLE` 응답 계약 유지.

## 검증 결과

| 검증 | 결과 | 범위 |
|---|---|---|
| 전체 외부 호출 없는 회귀 | 875/875, 실패·오류·건너뜀 0 | 마지막 오류 분류 보완 직전, 3분 48초 |
| 오류 분류 보완 후 영향 테스트 | 24/24, 실패·오류·건너뜀 0 | handler·prefetch·실제 HTTP smoke, 27초 |
| 설계 review-loop | 9 → 8 → 9.2/10 | 재선택 seed 계약 보완 후 통과 |
| 구현 review-loop | 7 → 9 → 9/10 | HTTP 누락·pin ID·취소·격리·서식 보완 후 통과 |
| 마지막 오류 분류 독립 검토 | 9/10, Critical/Important 없음 | 기존 upstream 분류·후보 격리·편집 보존 |
| 실제 API 생성 12회 | 11/12 성공·채택 미달 | p2-4c-actual-api-result.md 참조 |

공개 생성 결과의 canonical 장소명·좌표, Tool 등록, 정상 0건과 HTTP 누락, 음식점 fallback·상한·중복, 타임아웃·형제 branch 취소, 동시 요청 격리, correction reset, 재선택 간 후보 격리, 기존 편집·재구성 경로 검증. 테스트 성공은 실제 품질·지연 채택의 대체 근거가 아님.

HTTP stub는 실제 등록 Tool 목록에 따라 응답하도록 수정. 생성에서는 검색 Tool이 등록되지 않으므로 stub의 모델 HTTP 1회. 이 숫자를 실제 LLM 호출 감소 실측으로 해석하지 않음.

## 실측과 채택 판정

승인 후 `TravelLlmQualityBaselineTest`를 `P2_GENERATION_ONLY=true`로 실행. 서울·부산·강릉·경주 4case×3회, 기존 case와 첫 회차 cache miss·이후 hit 조건 유지. 모델 `gpt-5.6-terra`, reasoning `none`. 실행 전 소스 hash·환경 설정·캐시 조건 기록. DB·Redis는 Testcontainers 격리 환경. `.env` 값은 출력·Git·보관 사본에서 제외.

품질은 R0 누적 기준, 지연·token은 채택한 P2-4A 대비. P2-4A 성공 중앙값 19.986초·token 47,916.5. 채택 지연 상한 약 13.990초(30% 감소), token 상한 52,708.15(1.1배), 느려진 case 최대 1. 품질 gate와 미측정 감시 지표는 원 계획·P2-4A 결과의 한계 유지. 실패 요청은 성공 지연 집계에서 제외하면서 전체 통과 수에는 포함. JUnit BUILD SUCCESSFUL과 일정 생성 성공률을 별도 판정.

사용자 승인 후 실제12회 완료. 지연·하드 품질 gate 미달. 실제 gate 충족 전 commit·push·PR·dev/main 병합 보류. P2-4B 실패 원인 분석은 P2-4C 실측 이후 진행. 현재 B 결과는 token 감소·재선택 증가·메뉴 원본 불일치의 관찰 사실이며 단일 인과 미확정.

## 설계 문서 검증과 보관

`p2-4c-system-design.docx`는 지정 System Design reference 복제 기반. reference SHA256 `13504f6c221a42c1726460a9e865e563355539ff97d702d6c9b2267b4b261d76`. Letter portrait 1섹션·표 9개·grid·관계·번호 보존. 본문·표 슬롯·구조도 이미지 교체, 한글 글꼴 및 표지·빈 문단 간격 조정. 원본 7페이지에서 6페이지. 최종 6페이지 렌더 확인, 표 끝 주석과 제목 분리 보완. 원본 reference 변경 없음.

설계·조사·이 결과 문서·DOCX·집계 JSON은 프로젝트와 `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서`에 동일 사본 보관. 임시 상세 로그는 `/private/tmp/p24c-*`이며 영구 증거로 주장하지 않음. `refactoring-plan.md` 스테이징 금지 유지.
