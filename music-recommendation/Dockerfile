# ==================== 阶段 1: Maven 构建 ====================
FROM maven:3.9-eclipse-temurin-17 AS builder

# 利用 Docker 缓存：先复制 pom.xml，下载依赖，再复制源码
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B -q

# ==================== 阶段 2: 运行时 ====================
FROM eclipse-temurin:17-jre-alpine AS runtime

# 时区 + 中文字体（Alpine 精简镜像默认没有）
RUN apk add --no-cache tzdata fontconfig ttf-dejavu wget \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 复制 jar
COPY --from=builder /app/target/*.jar app.jar

# 创建非 root 用户运行（安全最佳实践）
RUN addgroup -S appuser && adduser -S -G appuser appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# 健康检查：容器启动 45s 后每 30s 探测一次
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O - http://localhost:8080/actuator/health || exit 1

# JVM 参数：-Xms256m -Xmx512m（容器默认 1G 限制内，留 500M 给 MySQL）
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-jar", "app.jar"]
