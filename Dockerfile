FROM openjdk:17.0.2-jdk-slim as build
WORKDIR application
COPY . .
RUN ./gradlew --no-daemon installDist

FROM openjdk:17.0.2-jdk-slim
WORKDIR application
RUN apt-get update && apt-get install -y procps
COPY --from=build application/build/install/org.openmbee.flexo.mms.store-service/ .
RUN mkdir /config && touch /config/override.conf
COPY --from=build application/src/main/resources/application.conf.example ./config/application.conf
ENTRYPOINT ["./bin/org.openmbee.flexo.mms.store-service", "--config", "/config/application.conf", "--config","/config/override.conf"]
EXPOSE 8080 8443
