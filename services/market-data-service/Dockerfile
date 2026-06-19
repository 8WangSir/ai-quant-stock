FROM eclipse-temurin:21-jdk-alpine

# 代理参数（构建时传入）
ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV HTTP_PROXY=${HTTP_PROXY} HTTPS_PROXY=${HTTPS_PROXY}

# Install Python 3 and build dependencies (使用代理)
RUN if [ -n "$HTTP_PROXY" ]; then export http_proxy=$HTTP_PROXY https_proxy=$HTTPS_PROXY; fi && \
    apk add --no-cache python3 py3-pip gcc musl-dev python3-dev libffi-dev openssl-dev

WORKDIR /app

# Install Python dependencies
COPY python/requirements.txt /app/python/
RUN pip3 install --no-cache-dir --break-system-packages -r /app/python/requirements.txt 2>/dev/null || \
    pip3 install --no-cache-dir -r /app/python/requirements.txt

# Copy Python scripts
COPY python/config.py /app/python/
COPY python/data_sync/ /app/python/data_sync/

# Copy JAR
COPY services/market-data-service/target/*.jar /app/service.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV PYTHON_HOME=/app/python
ENV PYTHON_EXECUTABLE=python3

EXPOSE 8081 9991

ENTRYPOINT ["java", "-jar", "/app/service.jar"]
