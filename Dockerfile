FROM eclipse-temurin:11-jre

EXPOSE 8085

WORKDIR /app

COPY target/*.jar /app/app.jar

CMD ["java", "-jar", "/app/app.jar"]
