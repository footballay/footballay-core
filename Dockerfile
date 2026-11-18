FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.source="https://github.com/Footballay/footballay-core"

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY build/libs/*.jar /app/app.jar

ENV SERVER_PORT=8080
# Java4Shell(CVE-2026-114938) 완화: AVX-512 Base64 intrinsic 비활성화
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Stack=false -Dsun.net.inetaddr.negative.ttl=0 -XX:-UseBASE64Intrinsics"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/health" || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_MEMORY} ${JAVA_TOOL_OPTIONS} -jar /app/app.jar \
  --server.port=${SERVER_PORT} \
  --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]