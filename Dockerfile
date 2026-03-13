FROM amazoncorretto:21 AS builder
WORKDIR /app

COPY gradlew ./
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew clean bootJar -x test -x copyApiDocument --no-daemon

FROM amazoncorretto:21-headless
WORKDIR /app

RUN (command -v dnf && dnf install -y shadow-utils && dnf clean all) || \
    (yum install -y shadow-utils && yum clean all) && \
    groupadd -r appuser && \
    useradd -r -g appuser appuser

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "\
    java \
    -javaagent:${PINPOINT_AGENT_PATH}/pinpoint-bootstrap-${PINPOINT_AGENT_VERSION}.jar \
    -Dpinpoint.agentId=${PINPOINT_AGENT_ID} \
    -Dpinpoint.applicationName=${PINPOINT_APPLICATION_NAME} \
    -Dpinpoint.config=${PINPOINT_AGENT_PATH}/pinpoint-root.config \
    -Dprofiler.transport.grpc.collector.ip=${PINPOINT_COLLECTOR_IP} \
    -Dprofiler.transport.grpc.collector.connect.timeout=${PINPOINT_CONNECT_TIMEOUT} \
    -Dprofiler.transport.grpc.collector.request.timeout=${PINPOINT_REQUEST_TIMEOUT} \
    -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} \
    ${JAVA_OPTS} \
    -jar app.jar \
"]
