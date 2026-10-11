# ============================================================================
# Multi-stage build - Notification Service (Spring Boot 4.1.1 / Java 17)
# Stage 1 "builder": build ra file .jar bang Gradle Wrapper (khong can Gradle cai san tren VPS)
# Stage 2: chi copy file .jar da build sang JRE-only image (nhe, khong mang theo JDK/Gradle
# cache) - dung image final nho, khoi dong nhanh, giam attack surface tren VPS production.
# ============================================================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy truoc cac file it doi (wrapper, build script) de tan dung Docker layer cache:
# neu code (src/) thay doi nhung build.gradle khong doi, layer download dependencies
# van duoc cache lai, khong phai tai lai tu dau moi lan build image.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8086

# actuator/health duoc dung lam Docker HEALTHCHECK va cho Kubernetes/K8s livenessProbe sau nay.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8086/actuator/health | grep -q '"status":"UP"' || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
# JAVA_OPTS duoc ghi de qua docker-compose (xem .env.lite / .env.standard) de vua RAM cua VPS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
