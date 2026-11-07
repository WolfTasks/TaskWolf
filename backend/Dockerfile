# eclipse-temurin:25-jre-alpine
FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61
# brotli-libs 1.2.0-r1 -> 1.2.1-r0 (CVE-2026-111497)
# libexpat 2.9.0-r0 -> 2.10.0-r0 (CVE-2026-113351/115506/117610/118370)
# musl/musl-utils 1.2.6-r2 -> 1.2.6-r3 (CVE-2026-107891); zlib 1.3.2-r0 -> 1.3.2-r1 (CVE-2026-85091)
RUN apk upgrade --no-cache p11-kit p11-kit-trust libexpat openssl libssl3 brotli-libs musl musl-utils zlib
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
