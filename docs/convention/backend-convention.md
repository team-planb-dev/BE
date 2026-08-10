# Team PlanB - Backend Convention Guidelines


---

# 0. Convention List
1. [Package Structure](#1-package-structure)
2. [Class Naming Convention](#2-class-naming-convention)
3. [Layer Responsibilities](#3-layer-responsibilities)
4. [REST API Convention](#4-rest-api-convention)
5. [Common API Response](#5-common-api-response)
6. [HTTP Status Convention](#6-http-status-convention)
7. [DTO Convention](#7-dto-convention)
8. [Exception Convention](#8-exception-convention)
9. [Transaction Convention](#9-transaction-convention)
10. [Comment & Javadoc Convention](#10-comment--javadoc-convention)
11. [General Coding Convention](#11-general-coding-convention)

---

# 1. Package Structure

도메인 중심(Domain-Driven) 패키지 구조를 기본으로 한다.

```text
src/main/java/com/planb
│
├── domain/
│   ├── user/
│   ├── trip/
│   ├── ai/
│   └── ...
│
├── query/
│   ├── user/
│   ├── trip/
│   └── ...
│
└── global/
    ├── config/
    ├── exception/
    ├── security/
    ├── util/
    └── common/
```

각 도메인은 다음과 같은 구조를 따른다.

```text
user/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── facade/
├── repository/
└── service/
```

복잡한 QueryDSL 또는 JPQL 기반 데이터 접근은 `query` 패키지로 분리한다.

```text
query/
└── user/
    ├── repository/
    └── service/
```

---

## Rules

- 패키지명은 모두 소문자로 작성한다.
- 기능이 아닌 **도메인 기준**으로 패키지를 분리한다.
- 공통 기능은 `global` 패키지에서 관리한다.
- 비즈니스 로직은 `global` 패키지에 작성하지 않는다.
- QueryDSL 및 JPQL 기반의 복잡한 데이터 접근은 `query` 패키지로 분리한다.
- Request / Response DTO는 `dto/request`, `dto/response` 구조를 따른다.

---

# 2. Class Naming Convention

| Layer | Naming Convention | Example |
|--------|-------------------|---------|
| Controller | `{Domain}Controller` | `UserController` |
| Facade | `{Domain}Facade` | `UserFacade` |
| Service | `{Domain}Service` | `UserService` |
| Query Service | `{Domain}QueryService` | `UserQueryService` |
| Repository | `{Domain}Repository` | `UserRepository` |
| Query Repository | `{Domain}QueryRepository` | `UserQueryRepository` |
| Request DTO | `{Action}{Domain}Request` | `CreateUserRequest` |
| Response DTO | `{Action}{Domain}Response` | `CreateUserResponse` |
| Exception | `{Domain}Exception` | `UserException` |
| Exception Code | `{Domain}ExceptionType` | `UserExceptionType` |
| Enum | `{Domain}{Type}` | `UserRole` |

---

## Facade Convention

Facade는 하나의 사용자 요청을 처리하는 **Application Layer** 역할을 담당한다.

```text
facade/
└── UserFacade.java
```

### Rules

- 디렉터리명은 `facade`로 통일한다.
- 클래스명은 `{Domain}Facade` 형식을 사용한다.
- Controller는 여러 Service를 직접 호출하지 않는다.
- 하나의 요청에 여러 Service가 필요한 경우 Facade를 사용한다.
- 단순히 Service 하나만 호출하는 경우에는 Facade 생성을 지양한다.
- Facade는 Repository를 직접 호출하지 않는다.
- Facade 메서드에는 주요 비즈니스 흐름을 설명하는 Javadoc을 작성한다.

### Example

```java
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;
    private final UserQueryService userQueryService;

    /**
     * 회원가입 전체 흐름
     *
     * 1. 아이디 중복 검사
     * 2. 회원 생성
     */
    public UserCreateResponse createUser(UserCreateRequest request){

        userQueryService.validateDuplicateUsername(request.username());

        return userService.createUser(request);
    }
}
```

---

# 3. Layer Responsibilities

각 Layer는 하나의 책임(SRP)을 갖도록 구성한다.

---

## Controller

### Responsibilities

- HTTP Request / Response 처리
- Request DTO 수신
- Response DTO 반환
- HTTP Status 설정
- Facade 호출

### Rules

- Controller에서는 비즈니스 로직을 작성하지 않는다.
- Controller에서는 Repository를 호출하지 않는다.
- Controller에서는 Entity를 반환하지 않는다.
- Controller에서는 여러 Service를 직접 조합하지 않는다.

---

## Facade

### Responsibilities

- 여러 Service 호출 순서 제어
- 하나의 사용자 요청 처리
- 트랜잭션 경계 관리
- 여러 도메인 조합

### Rules

- 하나의 사용자 요청을 하나의 메서드에서 처리한다.
- Repository를 직접 호출하지 않는다.
- Service 간 호출 순서를 관리한다.
- 주요 비즈니스 흐름에는 Javadoc을 작성한다.

---

## Service

### Responsibilities

- 단일 도메인 비즈니스 로직 수행
- Entity 생성 및 수정
- Repository 호출

### Rules

- 하나의 책임만 수행한다.
- @Transactional 은 기본적으로 메소드에 사용되는걸 허용하지 않는다.
- 다른 Service와의 흐름 제어는 Facade에 위임한다.
- Controller를 참조하지 않는다.
- 다른 Domain의 Repository를 직접 호출하지 않는다.


---

## Query Service

### Responsibilities

- 조회 비즈니스 로직
- 조회 Validation
- Query Repository 호출

### Rules

- 조회와 관련된 비즈니스 로직만 작성한다.
- 생성, 수정, 삭제 로직은 작성하지 않는다.
- Query Repository를 사용한다.
- 필요한 경우에만 `@Transactional(readOnly = true)`를 적용한다.

---

## Repository

### Responsibilities

- Entity 저장
- 단순 CRUD
- 기본 조회

### Rules

- Spring Data JPA 기반 CRUD를 담당한다.
- 복잡한 데이터 접근은 Query Repository로 분리한다.
- 비즈니스 로직을 작성하지 않는다.

---

## Query Repository

### Responsibilities

- QueryDSL 및 JPQL 기반의 복잡한 조회
- Projection
- Paging
- Cursor Query
- Bulk Update / Bulk Delete
- Native Query
- 성능 최적화를 위한 커스텀 데이터 접근

### Rules

- QueryDSL 또는 JPQL 기반의 복잡한 데이터 접근을 담당한다.
- 기본 Repository로 처리하기 어려운 조회 및 벌크 연산을 작성한다.
- QueryDSL의 `update()`, `delete()`와 같은 Bulk Query를 사용할 수 있다.
- Native Query는 필요한 경우에만 사용한다.
- 비즈니스 로직은 작성하지 않는다.


# 4. REST API Convention

RESTful API 설계를 기본 원칙으로 한다.

---

## 4-1. Base URL

모든 API는 버전 정보를 포함한다.

```text
/api/v1/{resource}
```

### Example

```http
GET    /api/v1/users
POST   /api/v1/users
GET    /api/v1/users/{userId}
PATCH  /api/v1/users/{userId}
DELETE /api/v1/users/{userId}
```

---

## 4-2. Resource Naming

### Rules

- URL에는 명사(Noun)를 사용한다.
- 리소스명은 복수형(Plural)을 사용한다.
- URL에는 동사를 사용하지 않는다.
- URL은 소문자로 작성한다.
- 여러 단어로 구성된 URL에는 하이픈(`-`)을 사용한다.
- 리소스 간 포함 관계를 표현할 때만 URL을 중첩한다.
- URL 중첩은 가능한 한 깊지 않게 구성한다.

### Good

```http
GET /api/v1/users
GET /api/v1/chat-rooms
GET /api/v1/chat-rooms/{roomId}/messages
```

### Bad

```http
GET  /api/v1/getUsers
POST /api/v1/createUser
POST /api/v1/chatRoom/create
```

---

## 4-3. HTTP Method

| Method | Purpose |
|--------|---------|
| `GET` | 리소스 조회 |
| `POST` | 리소스 생성 또는 Command 실행 |
| `PUT` | 리소스 전체 수정 |
| `PATCH` | 리소스 일부 수정 |
| `DELETE` | 리소스 삭제 |

---

## 4-4. API Naming Rules

### Collection Resource

```http
GET  /api/v1/users
POST /api/v1/users
```

### Single Resource

```http
GET    /api/v1/users/{userId}
PATCH  /api/v1/users/{userId}
DELETE /api/v1/users/{userId}
```

### Nested Resource

```http
GET /api/v1/chat-rooms/{roomId}/messages
```

리소스의 소유 관계나 포함 관계가 명확한 경우에만 중첩 URL을 사용한다.

---

## 4-5. Query Parameters

검색, 필터링, 정렬, 페이지네이션에는 Query Parameter를 사용한다.

### Example

```http
GET /api/v1/users?page=0&size=20
GET /api/v1/stores?keyword=coffee
GET /api/v1/posts?sort=createdAt,desc
```

### Rules

- 검색 조건은 Query Parameter로 전달한다.
- 선택적인 조건을 Path Variable로 표현하지 않는다.
- 정렬 기준과 정렬 방향을 명확하게 표현한다.
- Query Parameter 이름은 camelCase를 사용한다.

---

## 4-6. Pagination

일반 목록 조회에는 Offset Pagination 또는 Cursor Pagination을 용도에 맞게 사용한다.

### Offset Pagination

일반적인 목록 조회와 페이지 이동이 필요한 경우 사용한다.

```http
GET /api/v1/users?page=0&size=20
```

### Cursor Pagination

채팅 메시지처럼 데이터가 지속적으로 추가되는 목록에는 Cursor Pagination을 우선 사용한다.

```http
GET /api/v1/chat-rooms/{roomId}/messages?before=1024&size=30
```

### Rules

- 실시간으로 데이터가 계속 추가되는 목록은 Cursor Pagination을 사용한다.
- Cursor는 정렬 기준과 일관된 방향을 가져야 한다.
- Cursor Pagination 응답에는 다음 조회에 사용할 Cursor와 추가 데이터 존재 여부를 포함한다.
- 일반적인 관리자 목록이나 페이지 번호 이동이 필요한 화면에는 Offset Pagination을 사용할 수 있다.

---

# 5. Common API Response

모든 REST API 응답은 프로젝트에 구현된 공통 응답 형식을 사용한다.

공통 응답과 성공·실패 응답 생성 방식은 실제 프로젝트 디렉터리에 구현된 다음 클래스를 기준으로 사용한다.

```text
ApiResult
Success Response
Failure Response
Error Response
```

> 공통 응답 클래스를 새로 중복 구현하지 않고, 프로젝트에 이미 구현된 응답 객체와 정적 팩토리 메서드를 참고하여 사용한다.

---

## 5-1. Success Response

성공 응답은 프로젝트에 구현된 `ApiResult`의 성공 응답 생성 메서드를 사용한다.

### Example

```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "planb"
  },
  "error": null
}
```

응답 데이터가 없는 성공 요청은 프로젝트에서 정의한 성공 응답 형식을 따른다.

---

## 5-2. Error Response

실패 응답은 Controller에서 직접 생성하지 않고 Global Exception Handler에서 공통 형식으로 반환한다.

### Example

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "User not found."
  }
}
```

---

## 5-3. Rules

- 모든 REST API는 프로젝트에 구현된 `ApiResult<T>` 형식을 사용한다.
- 성공 및 실패 응답은 기존 디렉터리에 구현된 정적 팩토리 메서드를 사용한다.
- 공통 응답 객체를 도메인별로 중복 구현하지 않는다.
- Controller는 Entity를 직접 반환하지 않는다.
- 성공 응답은 기존 Success Response 생성 방식을 사용한다.
- 실패 응답은 Global Exception Handler에서 생성한다.
- HTTP Status와 Response Body를 일관성 있게 사용한다.
- `ResponseEntity`를 사용하여 HTTP Status를 명확하게 표현한다.
- 예외 응답을 `200 OK`로 반환하지 않는다.
- 응답 객체의 필드 구조를 도메인마다 임의로 변경하지 않는다.

---

# 6. HTTP Status Convention

| Status | Usage |
|--------|-------|
| `200 OK` | 조회, 수정 및 일반 요청 성공 |
| `201 Created` | 리소스 생성 성공 |
| `204 No Content` | 응답 Body가 없는 삭제 또는 수정 성공 |
| `400 Bad Request` | 잘못된 요청 값 또는 요청 형식 |
| `401 Unauthorized` | 인증되지 않은 사용자 |
| `403 Forbidden` | 접근 권한이 없는 사용자 |
| `404 Not Found` | 요청한 리소스가 존재하지 않음 |
| `409 Conflict` | 중복 데이터 또는 현재 상태와의 충돌 |
| `500 Internal Server Error` | 처리되지 않은 서버 내부 오류 |

---

## 6-1. Rules

- 생성 성공 시 `201 Created`를 사용한다.
- 삭제 후 반환 데이터가 없으면 `204 No Content`를 사용할 수 있다.
- 인증 정보가 없거나 유효하지 않으면 `401 Unauthorized`를 사용한다.
- 인증되었으나 접근 권한이 없으면 `403 Forbidden`을 사용한다.
- 요청한 리소스가 존재하지 않으면 `404 Not Found`를 사용한다.
- 중복 생성이나 현재 리소스 상태와 충돌하면 `409 Conflict`를 사용한다.
- 예외 상황을 모두 `200 OK`로 반환하지 않는다.
- 내부 구현 오류나 스택 트레이스를 클라이언트에 노출하지 않는다.

> `204 No Content`를 사용하는 경우에는 HTTP 규격에 따라 Response Body를 반환하지 않는다.

---

# 7. DTO Convention

API 계층에서는 Entity를 Request 또는 Response 객체로 직접 사용하지 않는다.

---

## 7-1. Request DTO

Request DTO는 Client에서 Server로 전달되는 데이터를 표현한다.

Java `record` 사용을 기본으로 한다.

### Example

```java
public record UserCreateRequest(
        String username,
        String password
) {
}
```

---

## 7-2. Response DTO

Response DTO는 Server에서 Client로 반환되는 데이터를 표현한다.

클라이언트에 필요한 데이터만 포함한다.

### Example

```java
public record UserCreateResponse(
        Long id,
        String username,
        Instant createdAt
) {
}
```

---

## 7-3. Naming Convention

| Type | Naming Convention | Example |
|------|-------------------|---------|
| Request DTO | `{Action}{Domain}Request` | `CreateUserRequest` |
| Response DTO | `{Action}{Domain}Response` | `CreateUserResponse` |
| List Response | `{Domain}ListResponse` | `UserListResponse` |
| Detail Response | `{Domain}DetailResponse` | `UserDetailResponse` |
| Cursor Response | `{Domain}CursorResponse` | `ChatMessageCursorResponse` |

---

## 7-4. Rules

- Request DTO와 Response DTO를 분리한다.
- DTO는 Java `record`를 기본으로 사용한다.
- Entity를 API Request 또는 Response로 사용하지 않는다.
- Request DTO에서 Repository를 호출하지 않는다.
- Request DTO에 비즈니스 로직을 작성하지 않는다.
- Entity 생성 및 변경은 Service 또는 Entity의 정적 팩토리 메서드에서 수행한다.
- Response DTO에는 클라이언트에 필요한 데이터만 포함한다.
- 민감한 정보는 Response DTO에 포함하지 않는다.
- 하나의 DTO를 여러 API에서 무리하게 재사용하지 않는다.
- API의 목적과 응답 구조가 다르면 별도의 DTO를 생성한다.
- Entity와 DTO 간 변환 방식은 도메인 내에서 일관되게 유지한다.

---

# 8. Exception Convention

애플리케이션 예외는 프로젝트에서 구현된 `BaseException` 구조를 사용한다.

---

## 8-1. Common Exception

공통적으로 사용할 수 있는 예외는 `BaseExceptionEnum`에 추가한 뒤 `BaseException`을 통해 발생시킨다.

### Example

```java
throw new BaseException(BaseExceptionEnum.INVALID_REQUEST);
```

### Rules

- 공통 예외가 필요한 경우 먼저 `BaseExceptionEnum`에 예외 정보를 추가한다.
- `BaseExceptionEnum`에 정의되지 않은 임의의 코드나 메시지를 직접 전달하지 않는다.
- 단순한 `RuntimeException`을 직접 발생시키지 않는다.
- 공통 예외를 도메인마다 중복해서 정의하지 않는다.

---

## 8-2. Domain Exception

도메인별로 별도의 예외 구조가 필요한 경우, `MessageCommInterface`를 구현한 새로운 Exception Enum을 생성한다.

### Example

```java
public enum UserExceptionEnum implements MessageCommInterface {

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_NOT_FOUND",
            "User not found."
    ),

    DUPLICATE_USERNAME(
            HttpStatus.CONFLICT,
            "DUPLICATE_USERNAME",
            "Username already exists."
    );

    // Fields and implementation
}
```

도메인 예외 클래스는 해당 Exception Enum을 전달받아 사용한다.

```java
throw new UserException(UserExceptionEnum.USER_NOT_FOUND);
```

### Rules

- 공통 예외는 `BaseExceptionEnum`을 사용한다.
- 도메인에 종속적인 예외가 여러 개 필요한 경우 별도의 Exception Enum을 생성한다.
- 새로 생성하는 도메인 Exception Enum은 반드시 `MessageCommInterface`를 구현한다.
- Exception Enum에는 HTTP Status, Error Code, Error Message를 정의한다.
- 도메인 Exception Enum의 이름은 `{Domain}ExceptionEnum` 형식을 따른다.
- 도메인 예외 클래스의 이름은 `{Domain}Exception` 형식을 따른다.
- Error Code는 대문자 Snake Case를 사용한다.
- 예외 메시지는 클라이언트가 이해할 수 있도록 명확하게 작성한다.
- 내부 구현 정보나 민감한 데이터를 예외 메시지에 포함하지 않는다.

---

## 8-3. Exception Handling Flow

```text
Controller
    ↓
Facade / Service / Query Service
    ↓
BaseException or Domain Exception
    ↓
Global Exception Handler
    ↓
ApiResult Failure Response
```

---

## 8-4. Global Exception Handling Rules

- 모든 REST API 예외는 Global Exception Handler에서 공통 처리한다.
- Controller에서 비즈니스 예외를 `try-catch`로 처리하지 않는다.
- 예외 유형에 맞는 HTTP Status를 반환한다.
- 실패 응답은 프로젝트에 구현된 공통 Failure Response 형식을 사용한다.
- 예상하지 못한 예외는 공통 서버 오류로 변환한다.
- 서버 로그에는 디버깅에 필요한 정보를 기록하되, 클라이언트에는 내부 정보를 노출하지 않는다.
- 동일한 예외 처리 로직을 Controller마다 중복 작성하지 않는다.


# 9. Transaction Convention

트랜잭션의 범위는 **하나의 사용자 요청(Request)** 을 기준으로 설정한다.

생성(Create), 수정(Update), 삭제(Delete)와 같은 Command 작업은 여러 Service가 하나의 비즈니스 흐름으로 동작할 수 있으므로 **Facade 계층에서 트랜잭션을 관리하는 것을 원칙**으로 한다.

---

## 9-1. Transaction Flow

```text
Controller
        │
        ▼
Facade
(@Transactional)
        │
        ├─────────────┐
        ▼             ▼
Service A       Service B
        │             │
        ▼             ▼
Repository     Query Repository
```

하나의 요청(Request)에 포함되는 여러 Service 호출은 하나의 트랜잭션으로 관리한다.

---

## 9-2. Command Transaction

생성(Create), 수정(Update), 삭제(Delete) 작업은 Facade에서 트랜잭션을 시작한다.

### Example

```java
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;
    private final UserQueryService userQueryService;

    @Transactional
    public UserCreateResponse createUser(UserCreateRequest request){

        userQueryService.validateDuplicateUsername(request.username());

        return userService.createUser(request);
    }
}
```

Service는 자신의 단일 책임만 수행하며, 트랜잭션의 시작과 종료는 Facade가 담당한다.

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserCreateResponse createUser(UserCreateRequest request){

        User user = User.create(
                request.username(),
                request.password()
        );

        User savedUser = userRepository.save(user);

        return UserCreateResponse.from(savedUser);
    }
}
```

---

## 9-3. Query Transaction

조회 전용 로직은 필요한 경우에만 `@Transactional(readOnly = true)`를 사용한다.

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserQueryService {
}
```

단순 조회이거나 트랜잭션이 필요하지 않은 경우에는 생략할 수 있다.

---

## 9-4. Rules

- 생성(Create), 수정(Update), 삭제(Delete) 작업의 트랜잭션은 Facade에서 관리하는 것을 원칙으로 한다.
- **특수한 경우를 제외하고** 단일 Service 클래스에는 `@Transactional`을 사용하지 않는다.
- 하나의 요청에서 여러 Service를 호출하는 경우 하나의 Facade 트랜잭션으로 관리한다.
- Query Service는 필요한 경우에만 `@Transactional(readOnly = true)`를 사용할 수 있다.
- Controller에는 `@Transactional`을 사용하지 않는다.
- Repository 또는 Query Repository에는 트랜잭션 경계를 설정하지 않는다.
- 외부 API, AI API, 장시간 수행되는 작업은 가능한 트랜잭션 외부에서 처리한다.
- 새로운 트랜잭션 전파(`REQUIRES_NEW` 등)가 필요한 경우에는 코드 리뷰를 통해 사용 목적을 공유한다.

---

# 10. Comment & Javadoc Convention

주석은 **코드가 무엇을 하는지(What)** 가 아니라 **왜 그렇게 구현했는지(Why)** 를 설명하는 것을 원칙으로 한다.

---

## 10-1. Rules

- 코드만으로 충분히 의미가 전달되는 경우에는 주석을 작성하지 않는다.
- 복잡한 비즈니스 정책이나 설계 의도를 설명하는 주석을 작성한다.
- 단순히 코드 내용을 읽어주는 주석은 작성하지 않는다.
- Public Method에는 필요한 경우 Javadoc을 작성한다.
- Facade의 Public Method에는 전체 비즈니스 흐름을 설명하는 Javadoc 작성을 권장한다.
- TODO는 반드시 관련 Issue 번호와 함께 작성한다.

---

## Bad Example

```java
// 사용자 저장
userRepository.save(user);
```

---

## Good Example

```java
// 시스템 정책상 Username은 중복될 수 없으므로
// 저장 전에 중복 여부를 반드시 검증한다.
```

---

## TODO Example

```java
// TODO(#53)
// Replace the simple broker with RabbitMQ.
```

---

## Facade Example

```java
/**
 * 회원가입 전체 프로세스
 *
 * 1. 아이디 중복 검사
 * 2. 회원 생성
 * 3. 기본 프로필 생성
 * 4. 기본 권한 설정
 */
public UserCreateResponse createUser(UserCreateRequest request) {
    ...
}
```

---

## Javadoc Guidelines

Javadoc은 클래스와 메서드의 **역할(Role)** 과 **비즈니스 흐름**을 설명하는 용도로 사용한다.

### Good

```java
/**
 * 사용자의 닉네임 중복 여부를 검증한다.
 *
 * @param nickname 검증할 닉네임
 * @throws UserException 이미 존재하는 닉네임인 경우
 */
```

### Bad

```java
/**
 * userRepository.findByNickname()을 호출한다.
 */
```

Javadoc은 구현 코드가 아니라 **메서드의 목적과 책임**을 설명하도록 작성한다.


# 11. General Coding Convention

프로젝트 전반에서 일관된 코드 구조와 스타일을 유지한다.

---

## 11-1. Dependency Injection

의존성 주입은 생성자 주입을 기본으로 한다.

### Rules

- 생성자 주입(Constructor Injection)을 사용한다.
- Lombok의 `@RequiredArgsConstructor` 사용을 권장한다.
- `@Autowired`를 사용한 Field Injection은 사용하지 않는다.
- 필드는 가능한 `final`로 선언한다.
- 불필요한 Setter Injection은 사용하지 않는다.

### Good

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
}
```

### Bad

```java
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
}
```

---

## 11-2. Entity Convention

Entity는 데이터베이스 테이블과 도메인 상태를 표현한다.

### Rules

- Entity를 Controller에서 직접 반환하지 않는다.
- Entity를 Request 또는 Response DTO로 사용하지 않는다.
- Entity 생성은 생성자 또는 정적 팩토리 메서드를 사용한다.
- Entity의 상태 변경은 의미가 드러나는 도메인 메서드를 통해 수행한다.
- 외부 계층에서 Entity 필드를 임의로 변경하지 않는다.
- 무분별한 Setter 사용을 지양한다.
- 연관관계 편의 메서드는 Entity 내부에 작성한다.

### Good

```java
public class User {

    public static User create(
            String username,
            String encodedPassword
    ) {
        return new User(username, encodedPassword);
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
```

### Bad

```java
user.setNickname(nickname);
user.setUpdatedAt(Instant.now());
```

---

## 11-3. Optional Convention

`Optional`은 조회 결과가 존재하지 않을 가능성을 표현하는 반환 타입으로 제한하여 사용한다.

### Rules

- Repository 또는 Query Repository의 조회 반환 타입에 사용할 수 있다.
- Entity 필드에는 사용하지 않는다.
- DTO 필드에는 사용하지 않는다.
- 메서드 파라미터에는 사용하지 않는다.
- 컬렉션 반환값을 `Optional<List<T>>`로 감싸지 않는다.
- 값이 없는 컬렉션은 빈 컬렉션으로 반환한다.
- `Optional.get()`을 직접 호출하지 않는다.
- `orElseThrow()`, `orElse()`, `orElseGet()` 등을 목적에 맞게 사용한다.

### Good

```java
User user = userRepository.findById(userId)
        .orElseThrow(() ->
                new UserException(UserExceptionEnum.USER_NOT_FOUND)
        );
```

### Bad

```java
Optional<User> optionalUser = userRepository.findById(userId);
User user = optionalUser.get();
```

---

## 11-4. Naming Convention

| Target | Convention | Example |
|--------|------------|---------|
| Class | PascalCase | `UserService` |
| Method | camelCase | `createUser()` |
| Variable | camelCase | `userId` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Enum Type | PascalCase | `UserRole` |
| Enum Constant | UPPER_SNAKE_CASE | `ADMIN` |
| Package | lowercase | `com.planb.domain.user` |

### Rules

- 이름만으로 역할과 의도를 이해할 수 있도록 작성한다.
- 의미 없는 축약어 사용을 지양한다.
- 불필요하게 긴 이름은 피한다.
- Boolean 변수와 메서드는 상태가 드러나도록 작성한다.

### Good

```java
boolean isChatRoomMember;
boolean hasNextPage;

boolean existsByUsername(String username);
```

### Bad

```java
boolean flag;
boolean check;

boolean getUser(String username);
```

---

## 11-5. Method Convention

메서드는 하나의 책임만 수행하도록 작성한다.

### Rules

- 하나의 메서드는 하나의 책임만 가진다.
- 메서드 길이는 가능한 짧게 유지한다.
- 메서드명은 수행하는 동작이 명확하게 드러나도록 작성한다.
- 중첩된 조건문보다 Early Return을 우선한다.
- 지나치게 많은 파라미터가 필요한 경우 별도의 객체 사용을 검토한다.
- Boolean 파라미터로 메서드 동작을 크게 분기하지 않는다.
- 반환값이 없는 상태 변경 메서드는 변경 목적이 드러나는 이름을 사용한다.

### Good

```java
public void validateChatRoomMember(
        Long roomId,
        Long userId
) {
    if (chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
        return;
    }

    throw new ChatException(
            ChatExceptionEnum.CHAT_ROOM_MEMBER_NOT_FOUND
    );
}
```

### Bad

```java
public void process(
        Long id,
        boolean create,
        boolean delete,
        boolean validate
) {
    // ...
}
```

---

## 11-6. Helper, Validator & Mapper Convention

재사용되거나 복잡한 보조 로직은 별도의 클래스로 분리하는 것을 원칙으로 한다.

```text
user/
├── service/
│   └── UserService.java
├── validator/
│   └── UserValidator.java
├── helper/
│   └── UserHelper.java
└── mapper/
    └── UserMapper.java
```

### Helper

Helper는 특정 로직의 계산, 조합, 변환 등 보조 작업을 담당한다.

```java
@Component
public class UserHelper {

    public String generateDefaultNickname(Long userId) {
        return "traveler-" + userId;
    }
}
```

### Validator

Validator는 재사용 가능한 검증 로직을 담당한다.

```java
@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserQueryRepository userQueryRepository;

    public void validateDuplicateUsername(String username) {
        if (userQueryRepository.existsByUsername(username)) {
            throw new UserException(
                    UserExceptionEnum.DUPLICATE_USERNAME
            );
        }
    }
}
```

### Mapper

Mapper는 Entity와 DTO 사이의 변환을 담당한다.

```java
public final class UserMapper {

    private UserMapper() {
    }

    public static UserCreateResponse toCreateResponse(User user) {
        return new UserCreateResponse(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt()
        );
    }
}
```

### Rules

- Helper, Validator, Mapper 등 부가 역할은 별도 클래스로 분리한다.
- 하나의 Service 내부에 과도한 private 메서드를 작성하지 않는다.
- 여러 메서드 또는 클래스에서 재사용되는 보조 로직은 별도 클래스로 분리한다.
- 검증 로직이 Service의 핵심 책임을 흐리게 만드는 경우 Validator로 분리한다.
- 계산, 변환, 조합 로직이 복잡해지는 경우 Helper로 분리한다.
- Entity와 DTO 사이의 변환이 반복되거나 복잡한 경우 Mapper로 분리한다.
- 단순한 한두 줄의 로직까지 무조건 별도 클래스로 분리하지 않는다.
- 클래스가 지나치게 커지거나 private 메서드가 과도하게 증가하면 분리를 검토한다.
- Helper는 가능한 상태를 가지지 않도록 작성한다.
- 공통 Utility와 도메인 전용 Helper를 구분한다.
- 도메인에 종속된 로직을 `global.util`에 배치하지 않는다.
- Validator는 검증 실패 시 프로젝트의 공통 예외 정책에 따라 예외를 발생시킨다.
- Helper와 Mapper에 비즈니스 흐름 제어 로직을 작성하지 않는다.

---

## 11-7. Constant & Enum Convention

매직 넘버와 매직 문자열은 상수 또는 Enum으로 관리한다.

### Bad

```java
if (retryCount > 3) {
    // ...
}

if (role.equals("ADMIN")) {
    // ...
}
```

### Good

```java
private static final int MAX_RETRY_COUNT = 3;

if (retryCount > MAX_RETRY_COUNT) {
    // ...
}

if (role == UserRole.ADMIN) {
    // ...
}
```

### Rules

- 반복해서 사용되는 숫자와 문자열은 상수로 분리한다.
- 제한된 상태값은 Enum으로 표현한다.
- 도메인에 종속된 상수는 해당 도메인 내부에서 관리한다.
- 모든 상수를 무조건 `global.constant`에 배치하지 않는다.
- Enum 내부에 상태와 관련된 간단한 행위를 포함할 수 있다.
- 문자열 비교보다 Enum 비교를 우선한다.

---

## 11-8. Logging Convention

로그는 운영 환경의 추적, 디버깅 및 장애 분석을 목적으로 작성한다.

### Rules

- `System.out.println()`과 `printStackTrace()`를 사용하지 않는다.
- SLF4J 기반 Logger를 사용한다.
- Lombok의 `@Slf4j`를 사용할 수 있다.
- 정상적인 비즈니스 흐름을 `error` 레벨로 기록하지 않는다.
- 비밀번호, 토큰, API Key 등 민감한 정보를 로그에 기록하지 않는다.
- 예외 로그에는 필요한 경우 예외 객체를 함께 전달한다.
- 동일한 예외를 여러 계층에서 중복 로깅하지 않는다.
- 반복적으로 발생하는 로그로 운영 환경을 과도하게 채우지 않는다.

### Log Level

| Level | Usage |
|-------|-------|
| `TRACE` | 매우 상세한 실행 흐름 |
| `DEBUG` | 개발 및 디버깅 정보 |
| `INFO` | 주요 비즈니스 흐름 및 상태 변경 |
| `WARN` | 복구 가능하지만 확인이 필요한 상황 |
| `ERROR` | 요청 실패 또는 시스템 장애 |

### Good

```java
log.info(
        "Chat room created. roomId={}, ownerId={}",
        roomId,
        ownerId
);
```

```java
log.error(
        "Failed to process chat message. roomId={}, messageId={}",
        roomId,
        messageId,
        exception
);
```

### Bad

```java
log.info("Access token: {}", accessToken);
System.out.println(user);
```

---

## 11-9. Validation Convention

입력값 검증과 도메인 정책 검증의 책임을 구분한다.

### Request Validation

- 요청 형식과 필수 입력값을 검증한다.
- 프로젝트의 Validation 정책에 따라 Controller 또는 Request DTO에서 처리한다.
- 문자열 길이, 형식, null 여부 등 입력 형식 검증을 담당한다.

### Business Validation

- 중복 여부, 접근 권한, 현재 상태 등 비즈니스 정책을 검증한다.
- Service, Query Service 또는 별도의 Validator에서 수행한다.
- 검증 실패 시 `BaseException` 또는 도메인 Exception을 발생시킨다.

### Rules

- 프론트엔드 검증만 신뢰하지 않는다.
- Controller에 복잡한 비즈니스 검증을 작성하지 않는다.
- Repository의 Boolean 반환값을 Controller에서 직접 판단하지 않는다.
- 여러 곳에서 반복되는 검증 로직은 Validator로 분리한다.
- 검증 메서드는 `validate` 또는 `check`보다 목적이 구체적으로 드러나는 이름을 사용한다.

### Good

```java
validateDuplicateUsername(username);
validateChatRoomMembership(roomId, userId);
validateItineraryOwnership(itineraryId, userId);
```

### Bad

```java
validate();
check();
isValid();
```

---

## 11-10. Collection Convention

### Rules

- 컬렉션은 가능한 인터페이스 타입으로 선언한다.
- 빈 결과는 `null`이 아니라 빈 컬렉션으로 반환한다.
- 외부에 노출되는 컬렉션의 변경 가능성을 고려한다.
- 메서드 내부에서 입력받은 컬렉션을 임의로 변경하지 않는다.
- 순서가 중요한 경우 정렬 기준을 명확하게 정의한다.

### Good

```java
List<UserResponse> users = List.of();
```

### Bad

```java
List<UserResponse> users = null;
```

---

## 11-11. Null Convention

### Rules

- 가능한 `null` 반환을 피한다.
- 조회 결과가 없을 수 있는 단일 객체는 `Optional` 또는 명시적인 예외로 처리한다.
- 컬렉션은 빈 컬렉션으로 반환한다.
- 필수 의존성과 필수 파라미터가 `null`인 상태를 허용하지 않는다.
- 무분별한 `Objects.requireNonNull()` 사용보다 계층별 검증 책임을 명확히 한다.

---

## 11-12. General Rules

- 하나의 클래스는 하나의 책임을 갖도록 작성한다.
- Service 간의 직접 호출보다 Facade를 통한 흐름 제어를 우선한다.
- 비즈니스 로직을 Controller, DTO, Repository에 작성하지 않는다.
- 공통 기능과 도메인 기능의 위치를 구분한다.
- 테스트하기 어려운 전역 상태와 정적 의존성을 지양한다.
- 코드 변경 시 관련 테스트를 함께 작성하거나 수정한다.
- 사용하지 않는 코드, Import, 주석은 제거한다.
- 구현 방식이 기존 컨벤션과 크게 다른 경우 PR에 설계 의도를 작성한다.
- 성능 최적화 코드는 실제 문제와 측정 결과를 근거로 적용한다.
- 가독성과 유지보수성을 단순한 코드 축약보다 우선한다.
