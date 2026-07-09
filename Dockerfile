# =============================================================================
# Build Stage — Amazon Corretto 21 (JDK)
# =============================================================================

FROM amazoncorretto:21-alpine-jdk AS build

WORKDIR /workspace

# --- Layer 1: Maven wrapper binaries ------------------

COPY mvnw ./
COPY .mvn/ .mvn/


RUN chmod +x mvnw

# --- Layer 2: Project descriptor----------------------------

COPY pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# =============================================================================
# Runtime Stage — Amazon Corretto 21
# (Corretto publishes no separate JRE image; the alpine JDK image stays small
# and keeps build and runtime on the same JVM distribution.)
# =============================================================================
FROM amazoncorretto:21-alpine AS runtime

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/target/todo-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar"]
