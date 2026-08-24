# ===== 运行阶段：仅打包已编译的可执行 jar =====
FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai
# 应用监听端口（与 application.properties 中 server.port 一致）
ENV SERVER_PORT=9500

WORKDIR /app

# 将已编译的可执行 jar 拷贝进镜像
# 构建时通过 --build-context 或手动放置 jar，确保 jar 位于构建上下文根目录
COPY order-example.jar /app/order-example.jar

# 外部配置文件目录：与 jar 同目录（/app），通过挂载卷把宿主机的
# application.properties / application-{profile}.properties 放到这里，
# Spring Boot 默认会扫描 jar 同级目录下的配置，覆盖 jar 内部默认配置
ENV APP_CONFIG_DIR=/app
# 激活的 Spring 环境（profile），运行时通过 -e SPRING_PROFILES_ACTIVE=prod 指定，
# 默认 default；对应读取 /app/application-{profile}.properties
ENV SPRING_PROFILES_ACTIVE=default
# 远程调试开关与端口：运行时 -e JAVA_DEBUG=true 开启，默认关闭
ENV JAVA_DEBUG=false
ENV JAVA_DEBUG_PORT=5005

# 暴露端口（应用端口 + 远程调试端口）
EXPOSE ${SERVER_PORT} ${JAVA_DEBUG_PORT}

# 以非 root 用户运行，提升容器安全性
RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && chown -R appuser:appuser /app
USER appuser

# 启动脚本：根据 JAVA_DEBUG 开关决定是否追加 JDWP 调试参数，并用 exec 保证信号转发
RUN printf '%s\n' \
    '#!/bin/sh' \
    'set -e' \
    'JAVA_OPTS="-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"' \
    'if [ "${JAVA_DEBUG}" = "true" ]; then' \
    '  JAVA_OPTS="${JAVA_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${JAVA_DEBUG_PORT}"' \
    'fi' \
    'exec java ${JAVA_OPTS} -jar /app/order-example.jar' \
    > /app/entrypoint.sh \
    && chmod +x /app/entrypoint.sh

# 通过环境变量注入的可选配置（避免硬编码敏感信息）：
#   MYSQL_PASSWORD           MySQL 密码
#   ROCKETMQ_NAMESRV         RocketMQ name-server 地址，默认 127.0.0.1:9876
#   ROCKETMQ_PRODUCER_GROUP / ROCKETMQ_CONSUMER_GROUP  生产/消费组名
#   ES_PASSWORD              Elasticsearch 密码
#   SPRING_PROFILES_ACTIVE   激活的环境（dev/test/prod），默认 default
#   JAVA_DEBUG               是否开启远程调试（true/false），默认 false
#   JAVA_DEBUG_PORT          远程调试端口，默认 5005
ENTRYPOINT ["/app/entrypoint.sh"]
