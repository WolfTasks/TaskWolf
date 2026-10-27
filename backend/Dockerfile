# eclipse-temurin:21-jre-alpine
FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
# brotli-libs 1.2.0-r1 -> 1.2.1-r0 (CVE-2026-111497)
RUN apk upgrade --no-cache p11-kit p11-kit-trust libexpat openssl libssl3 brotli-libs
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
