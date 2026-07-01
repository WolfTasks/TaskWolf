# eclipse-temurin:21-jre-alpine
FROM eclipse-temurin:21-jre-alpine@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
