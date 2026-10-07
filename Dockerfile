FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
WORKDIR /build

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25.0.4.1_1-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app
WORKDIR /app

# A fresh build context runs bootJar only, so no plain JAR is produced.
COPY --from=build --chown=app:app /build/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
