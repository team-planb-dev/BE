# 빌드 단계: 소스에서 실행 가능한 jar 생성
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시를 위해 래퍼와 빌드 스크립트를 먼저 복사한다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 실행 단계: JRE만 포함해 이미지 크기를 줄인다.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

# 포트 바인딩은 application.yml의 server.port(${PORT:8080})가 처리한다.
ENTRYPOINT ["java", "-jar", "app.jar"]
