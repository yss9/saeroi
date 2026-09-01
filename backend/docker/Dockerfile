# circular-fashion/backend:latest
# 빌드 컨텍스트는 이 브랜치 루트. Jenkins의 backend 파이프라인이 backend/master 에서 빌드한다.

# ── 1st stage: 빌드 (Java 21 + Maven) ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 의존성 레이어를 소스와 분리해 캐시 적중률을 높인다
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN mvn -B -ntp dependency:go-offline || true

COPY src/ src/
# 테스트는 CI 스테이지에서 이미 돌았으므로 여기서는 건너뛴다
RUN mvn -B -ntp -DskipTests package

# ── 2nd stage: 런타임 (JRE만) ─────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

# 컨테이너를 root 로 돌리지 않는다. 애플리케이션 취약점이 컨테이너 탈출로 이어질 때
# root 인지 아닌지가 피해 범위를 가른다. UID 를 고정하는 이유는 file-storage 볼륨의
# 소유권이 이 UID 에 묶이기 때문이다 — 바꾸면 기존 업로드 파일에 접근하지 못한다.
RUN groupadd -r -g 10001 app && useradd -r -u 10001 -g app -d /app -s /usr/sbin/nologin app

# docker-compose.yml 의 file-storage 볼륨 마운트 지점
RUN mkdir -p /app/files && chown -R app:app /app

COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar

USER app

# nginx가 /api/ 를 backend:8080 으로 프록시한다 — 포트를 바꾸지 말 것
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
