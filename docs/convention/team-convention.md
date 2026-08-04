# Team PlanB - Git & GitHub Convention Guideline

---



## 1. Issue Convention

---


### 1-1. Issue Naming Convention

```
{[Type]} : {Description}
```

#### Type

- `feat` : 새 기능 개발
- `fix` : 버그 수정
- `hotfix` : 프로덕션 긴급 수정
- `chore` : 설정, 의존성, 환경 관련
- `docs` : 문서 작업
- `refactor` : 리팩토링 (기능 변경 없음)

#### example

```
feat : implements chat domain
fix : resolve QueryDSL Q-class generation issue
chore : add spring test container dependency
```

### 1-2. Issue Template

각 개발 파트별 Issue Template & Pull request Template 공유 완료

---

<br>

## 2. Branch

---


### 2.1 Git Flow Branch Strategy

- **`main`**: 배포 코드 브랜치
- **`dev`**: 다음 배포를 위한 통합 개발 브랜치
- **`feature-{IssueNumber}-설명`**: 기능 개발 브랜치
- **`fix-{IssueNumber}-설명`**: 버그 수정 브랜치
- **`hotfix-{IssueNumber}-설명`**: 배포 후 긴급 버그 수정 브랜치
- **`chore-{IssueNumber}-설명`**: 설정, 의존성, 리팩토링 브랜치
- **`docs-{IssueNumber}-설명`** : 문서 작업 브랜치

> 🔴️ `hotfix`만 `main`에서 분기. 그 외 모든 작업 브랜치는 `dev`에서 분기.
>

### 2-2. Branch Name Template

```
{Type}-{IssueNumber}-{Description}
```
: `issue`에서 `create branch`를 통해 해당 브랜치 생성하기 -> 컨벤션 규칙 자동 적용

ex) `1-feat-implement-real-time-chat-system` , `3-refactor-migrate-chat-presence-handling`


#### Rule

- 소문자만 사용됨
- 영어로 작성
- 너무 길게 쓰지 않기 (3~4단어 이내)

<br><br>

## 3. Commit Convention

---

### 3-1. Commit Message Template


```bash
{Type} : {Description}
```

#### ex )

```
feat : add @Async annotation for asyncronnous processing
refactor : revise user field by deleteing createdAt field
fix : resolve idempotency problem of party apply service method
chore : upgrade JUnit dependency (4->5)
docs : add Swagger @Tag annotation
```

### 3-2. Type list

| Type | description                    |
| --- |--------------------------------|
| `feat` | 새로운 기능 추가                      |
| `fix` | 버그 수정                          |
| `chore` | 빌드, 설정, 의존성 추가 혹은 수정           |
| `docs` | 문서 수정 및 주석 (README, Swagger 등) |
| `refactor` | 코드 리팩토링 및 구조 개선                |
| `style` | 코드 포맷팅 및 스타일 변경                |
| `test` | 테스트 코드 추가 및 수정                 |
| `hotfix` | 배포 후 긴급 버그 수정                  |

#### Rule

- 소문자로 시작
- 동사 원형으로 시작
- 50자 이내
- 마침표 없음
- description 이나 detail 깊게 적지 않기

🔴 주석 추가 시 , 세세한 로직 설명 X <br>
🔴 `Facade` 사용시에 , 각 `Service`클래스의 메소드에 주석 설정 필수!
<br><br><br>

## 4. PR Convention

---

### 4-1. PR Title Template

```
#({IssueNumber}) ({Type}) : {Description}
```

#### Type

- `feat` : 새 기능 개발
- `fix` : 버그 수정
- `hotfix` : 프로덕션 긴급 수정
- `chore` : 설정, 의존성, 환경 관련
- `docs` : 문서 작업
- `refactor` : 리팩토링 (기능 변경 없음)

#### ex )

```
#11 feat : introduce flyway for database schema versioning
#7 test : implement chat domain testing infrastructure 
#3 refactor : migrate chat presence handling to stomp session events
```

### 4.2 Pull Request Guidelines

| category                           | description                                                              |
|------------------------------------|--------------------------------------------------------------------------|
| Title                              | 위 참고 (ex: `#111 feat : introduce flyway for database schema versioning`) |
| Reviewer                           | `dev` 브랜치 병합 시 , `AI CodeReviewer(미정)`와 `협업자`의 코드 리뷰 필수                  |
| Assignees                          | 작성자 본인 지정                                                                |
| Labels                             | 관련 라벨 부착                                                                 |
| Issue Connection                   | `Closes #{IssueNumber}`로 이슈 종료                                           |
| Pull Request Approval Requirements | `Reviewer` 규칙 이후 , 승인 시에 코드 병합 진행                                        |

### 4-3. PR Template

각 개발 파트별 Issue Template & Pull request Template 공유 완료

<br><br>

## 5. Code Review Checklist

---

- Code Rabbit or PR Reviewer 도입 예정
- Merge or Pull request issue 발생 시 , 연락 요망
- There's no Self Merge!
- No Force push allowed❗️


---

