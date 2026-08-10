# TEMPORAL: base antigua a proposito, para validar que el gate de Trivy bloquea
FROM eclipse-temurin:11.0.16_8-jre

EXPOSE 8085

WORKDIR /app

COPY target/*.jar /app/app.jar

CMD ["java", "-jar", "/app/app.jar"]
