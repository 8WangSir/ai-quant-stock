FROM eclipse-temurin:21-jdk-alpine

# 代理参数（构建时传入）
ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV HTTP_PROXY=${HTTP_PROXY} HTTPS_PROXY=${HTTPS_PROXY}

WORKDIR /app

# Copy JAR
COPY services/admin-service/target/*.jar /app/service.jar

ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/service.jar"]
