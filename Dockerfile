# Build from source; tests are run separately before release.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY --chmod=0755 mvnw ./mvnw
COPY pom.xml ./pom.xml
COPY src/main ./src/main

# Accept Windows checkouts while keeping the Linux wrapper executable.
RUN sed -i 's/\r$//' mvnw \
    && ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build --chmod=0444 /build/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
USER 10001:10001

# Informational only: Spring Boot binds to the PORT supplied at runtime.
EXPOSE 8080
# Supply profile, database settings and JAVA_TOOL_OPTIONS at runtime.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
