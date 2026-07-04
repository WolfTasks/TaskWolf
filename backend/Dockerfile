# eclipse-temurin:21-jre-alpine
FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0
RUN apk upgrade --no-cache p11-kit p11-kit-trust libexpat
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
