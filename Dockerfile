FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY orchestrator orchestrator
COPY orders orders
COPY inventory inventory
COPY payments payments
COPY shipping shipping
COPY checkout checkout
RUN ./gradlew :checkout:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/checkout/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
