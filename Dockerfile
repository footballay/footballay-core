FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.source="https://github.com/Footballay/footballay-core"

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY build/libs/*.jar /app/app.jar

ENV SERVER_PORT=8080
# [보안] Java4Shell(CVE-2026-114938) 대응 - 2026/11/20
# OpenJDK AVX-512 Base64 디코딩 intrinsic 취약점 완화를 위해 -XX:-UseBASE64Intrinsics 를 기본 적용한다.
# 운영 배포는 footballay-core-action scripts/deploy.sh 가 JAVA_TOOL_OPTIONS를 덮어쓰므로 그쪽에서도 강제 적용한다.
# 수정 JRE(Temurin 17.0.21.1+2 이상) 이미지로 고정한 뒤 제거 여부를 검토한다.
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Stack=false -Dsun.net.inetaddr.negative.ttl=0 -XX:-UseBASE64Intrinsics"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/health" || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_MEMORY} ${JAVA_TOOL_OPTIONS} -jar /app/app.jar \
  --server.port=${SERVER_PORT} \
  --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]