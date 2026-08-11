FROM eclipse-temurin:11-jre

LABEL org.opencontainers.image.source="https://github.com/daedov/pet-clinic"

# La imagen base arrastra libsystemd0/libudev1 sin los ultimos parches de Ubuntu.
# Se instalan explicitamente en vez de usar "apt-get upgrade", que Trivy marca
# como mala practica (AVD-DS-0013) y haria fallar el audit del Dockerfile.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libsystemd0 libudev1 \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --create-home spring

EXPOSE 8085

WORKDIR /app

COPY target/*.jar /app/app.jar

USER spring

CMD ["java", "-jar", "/app/app.jar"]
