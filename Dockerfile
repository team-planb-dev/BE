# 빌드 단계: 소스에서 실행 가능한 jar 생성
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시를 위한 래퍼·빌드 스크립트 우선 복사
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# JRE만 포함한 실행 단계
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

# application.yml의 server.port(${PORT:8080})를 통한 포트 바인딩
ENTRYPOINT ["java", "-jar", "app.jar"]
