FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .


RUN mvn -B clean install -f common/pom.xml -DskipTests
RUN mvn -B clean install -f common-config/pom.xml -DskipTests

RUN mvn -B clean package -f patient-service/pom.xml -DskipTests


FROM eclipse-temurin:21-jdk
WORKDIR /app


COPY --from=builder /app/patient-service/target/happabi-*.jar app.jar



ENTRYPOINT ["java", "-jar", "app.jar"]