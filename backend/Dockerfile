# Multi-stage so the build toolchain never ships in the runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer, cached until the pom changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Extract the layered jar so application code is its own Docker layer.
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted


FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl && \
    addgroup -S smartwork && adduser -S smartwork -G smartwork
USER smartwork
WORKDIR /app

COPY --from=build --chown=smartwork:smartwork /build/extracted/dependencies/ ./
COPY --from=build --chown=smartwork:smartwork /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=smartwork:smartwork /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=smartwork:smartwork /build/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
