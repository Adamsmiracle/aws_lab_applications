# =============================================================================
# Build Stage
# =============================================================================
# eclipse-temurin is the official Adoptium distribution — well-maintained,
# frequently patched, and available on Alpine for a small base image.
# Pin to an explicit patch tag once one is confirmed stable in your CI;
# "21-jdk-alpine" tracks the latest 21.x patch, which is acceptable while
# you establish a promotion pipeline.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# --- Layer 1: Maven wrapper binaries (changes almost never) ------------------
# Copy the wrapper script and its hidden support directory first so Docker can
# reuse this layer as long as neither file changes.
COPY mvnw ./
COPY .mvn/ .mvn/

# The Maven wrapper shell script must be executable.  Git does not always
# preserve the permission bit across platforms (Windows hosts in particular).
RUN chmod +x mvnw

# --- Layer 2: Project descriptor (changes rarely) ----------------------------
# Copying pom.xml before src/ means that dependency:go-offline is cached until
# a dependency version actually changes — not on every source edit.
COPY pom.xml ./

# Pre-fetch all compile/runtime/test/plugin dependencies into the local cache
# inside this layer.  Subsequent builds that change only src/ skip this step.
# -B = batch mode (no ANSI progress bars, cleaner CI logs)
RUN ./mvnw dependency:go-offline -B

# --- Layer 3: Application source (changes frequently) ------------------------
COPY src/ src/

# Build the fat executable JAR.  Tests are skipped here; run them earlier in
# your CI pipeline, before the image build, so failures are caught cheaply.
RUN ./mvnw clean package -DskipTests -B

# =============================================================================
# Runtime Stage
# =============================================================================
# JRE-only image: no compiler (javac), no jshell, no tools.jar.
# Smaller attack surface, smaller image, faster ECR pull times.
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create a dedicated system group and user.  Running as root inside a container
# violates least-privilege, and ECR image scanning (Inspector) will flag it.
# addgroup / adduser are BusyBox builtins present on Alpine.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the repackaged executable JAR from the build stage.
# The *.jar.original (the raw Maven output before Spring Boot repackaging) is
# intentionally left behind — it cannot run standalone.
COPY --from=build /workspace/target/qrcode-0.0.1-SNAPSHOT.jar app.jar

# Drop to the non-root user before any process starts.
USER appuser

# Spring Boot default HTTP port.
EXPOSE 8080

# exec form (JSON array) is required so the JVM process receives OS signals
# directly (PID 1) rather than through a shell wrapper.  This lets Kubernetes /
# ECS send SIGTERM and trigger a graceful shutdown instead of a hard kill.
#
# JVM container flags:
#   -XX:+UseContainerSupport   – reads cgroup memory/CPU limits instead of
#                                host totals (on by default since Java 11;
#                                explicit here for documentation).
#   -XX:MaxRAMPercentage=75.0  – cap the heap at 75 % of the container memory
#                                limit, leaving ~25 % for off-heap memory
#                                (metaspace, thread stacks, GC bookkeeping,
#                                direct buffers, ZXing byte arrays).
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar"]
