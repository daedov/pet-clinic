FROM eclipse-temurin:11-jre

RUN useradd --system --uid 10001 --create-home spring

EXPOSE 8085

WORKDIR /app

COPY target/*.jar /app/app.jar

USER spring

CMD ["java", "-jar", "/app/app.jar"]
