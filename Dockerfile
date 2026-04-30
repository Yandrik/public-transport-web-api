FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src
COPY sublibs/public-transport-enabler sublibs/public-transport-enabler

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --home-dir /app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build /workspace/build/libs/public-transport-web-api-*.jar app.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
