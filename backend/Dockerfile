# ---- Build stage: compile with Maven ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy the wrapper + pom first so dependency download is cached independently of source
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -B -DskipTests package

# ---- Run stage: only the JRE and the built jar ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as a non-root user
RUN useradd --system --create-home --shell /usr/sbin/nologin spring
USER spring:spring

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
