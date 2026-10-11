# ============================================================================
# Multi-stage build - Product Catalog Service (Spring Boot 4.1.1 / Java 17)
# Stage 1 "builder": build ra file .jar bang Gradle Wrapper
# Stage 2: chi copy file .jar da build sang JRE-only image (nhe hon, it attack surface)
# Luu y: service co gRPC server (product.proto) va HTTP REST server chay dong thoi.
# ============================================================================
# Builder dung Debian/glibc (jammy), KHONG dung alpine/musl: plugin protobuf tai binary protoc (glibc) -> tren alpine bi loi "protoc.exe: No such file or directory".
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Copy truoc cac file it doi de tan dung Docker layer cache:
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

# HTTP REST port
EXPOSE 8083
# gRPC server port (Product gRPC server: order-inventory-service goi vao CheckStockAndPrice)
EXPOSE 9091

# /actuator/health duoc dung lam Docker HEALTHCHECK
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health | grep -q '"status":"UP"' || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
# JAVA_OPTS duoc ghi de qua docker-compose (xem .env.lite / .env.standard) de vua RAM cua VPS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
