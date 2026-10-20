#
# Build stage
#
FROM eclipse-temurin:25-jdk AS build
COPY . /home/app/src
WORKDIR /home/app/src
RUN chmod +x gradlew && ./gradlew build --no-daemon

#
# Package stage
#
FROM eclipse-temurin:25-jre
COPY --from=build /home/app/src/build/libs/webtechproject-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
