FROM eclipse-temurin:17.0.17_10-jdk AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

RUN ./gradlew dependencies || true

COPY src src

RUN ./gradlew clean assemble -x test --no-daemon

FROM eclipse-temurin:17.0.17_10-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    curl \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r ps3hfw && useradd -r -g ps3hfw ps3hfw

ENV TZ=UTC

WORKDIR /app

RUN mkdir -p /app/firmware && chown -R ps3hfw:ps3hfw /app

RUN curl -L -o /app/firmware/PS3UPDAT.PUP \
    "https://github.com/PS3-Pro/Firmware-Updates/releases/download/HFW/Hybrid_Firmware.PUP" \
    && chmod 644 /app/firmware/PS3UPDAT.PUP

COPY --from=builder /build/build/libs/*.jar app.jar

COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 53/tcp 53/udp 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:80/ || exit 1

ENV HTTP_PORT=80
ENV UPSTREAM_DNS=8.8.8.8
ENV LOCAL_IP=auto
ENV VERBOSE=false

USER ps3hfw

ENTRYPOINT ["docker-entrypoint.sh"]
