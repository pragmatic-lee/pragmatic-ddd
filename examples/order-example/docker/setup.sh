#!/bin/sh
# ============================================================
# order-example 一键初始化脚本
# 用法：bash setup.sh
# 前置条件：已执行 mvn -pl examples/order-example -am clean package -DskipTests
#           （构建产物 target/order-example.jar 与 target/Dockerfile 需存在）
# 功能：启动基础设施 → 初始化 MySQL/ES → 构建应用镜像 → 启动应用 → 就绪自检
# 全程幂等，可重复执行。
# ============================================================
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "${SCRIPT_DIR}"

TARGET_DIR="${SCRIPT_DIR}/../target"
INDEX_SCRIPT="${SCRIPT_DIR}/es/init/order-es-create-index.sh"
IMAGE_TAG="order-example:2.0.0"
MYSQL_PASSWORD="MySqlXXL123"
MYSQL_PING="docker exec my-mysql mysqladmin ping -h127.0.0.1 -uroot -p${MYSQL_PASSWORD} --silent"
MYSQL_EXEC="docker exec -i my-mysql mysql -h127.0.0.1 -uroot -p${MYSQL_PASSWORD}"

echo "==> [0/9] 前置检查"
if [ ! -f "${TARGET_DIR}/order-example.jar" ]; then
  echo "未找到 ${TARGET_DIR}/order-example.jar，请先执行："
  echo "  mvn -pl examples/order-example -am clean package -DskipTests"
  exit 1
fi
if [ ! -f "${TARGET_DIR}/Dockerfile" ]; then
  echo "未找到 ${TARGET_DIR}/Dockerfile，请先执行 mvn package（已配置自动复制）。"
  exit 1
fi

echo "==> [1/9] 启动基础设施（MySQL / Redis / ES / RocketMQ）"
docker compose up -d mysql redis elasticsearch rmqnamesrv rmqbroker rmqdashboard

echo "==> [2/9] 等待 MySQL 就绪"
i=0
until ${MYSQL_PING} >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "MySQL 等待超时（60s），请检查 docker compose ps"
    exit 1
  fi
  sleep 1
done
echo "    MySQL 已就绪"

echo "==> [3/9] 初始化 MySQL（幂等：建库建表 + id_segment 初始行）"
${MYSQL_EXEC} < mysql/init/01-schema.sql
${MYSQL_EXEC} < mysql/init/02-data.sql

echo "==> [4/9] 等待 Elasticsearch 就绪"
i=0
until curl -sf http://localhost:9200/ >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "Elasticsearch 等待超时（60s），请检查 docker compose ps"
    exit 1
  fi
  sleep 1
done
echo "    Elasticsearch 已就绪"

echo "==> [5/9] 检查 IK 分词插件"
if curl -sf http://localhost:9200/_cat/plugins 2>/dev/null | grep -q analysis-ik; then
  echo "    IK 插件已安装"
else
  echo "    IK 插件未安装，建索引将失败（analyzer order_default_ik not found）。"
  echo "    安装方式（二选一）："
  echo "      ① 在线安装：docker exec -it my-es bin/elasticsearch-plugin install \\"
  echo "           https://github.com/infinilabs/analysis-ik/releases/download/v8.17.0/elasticsearch-analysis-ik-8.17.0.zip"
  echo "         docker restart my-es"
  echo "      ② 离线放置：将插件解压到 docker/es/plugins/ik 后 docker restart my-es"
  exit 1
fi

echo "==> [6/9] 创建 ES 索引（幂等：已存在则跳过）"
if curl -sf "http://localhost:9200/_cat/indices/order_index" | grep -q order_index; then
  echo "    索引 order_index 已存在，跳过"
else
  bash "${INDEX_SCRIPT}"
  echo "    索引 order_index 创建完成"
fi

echo "==> [7/9] 构建应用镜像 ${IMAGE_TAG}"
docker build -t "${IMAGE_TAG}" -f "${TARGET_DIR}/Dockerfile" "${TARGET_DIR}"

echo "==> [8/9] 启动应用"
docker compose up -d order-example

echo "==> [9/9] 等待应用就绪"
i=0
until curl -sf http://localhost:9500/health >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "应用启动超时（60s），请查看日志：docker logs my-order-example"
    exit 1
  fi
  sleep 1
done
echo "    应用已就绪（/health = OK）"

echo ""
echo "=============================================="
echo " ✅ order-example 一键初始化完成"
echo "=============================================="
echo " 全链路验证："
echo "   curl http://localhost:9500/testOrder"
echo " 核对数据："
echo "   docker exec my-mysql mysql -h127.0.0.1 -uroot -p${MYSQL_PASSWORD} \\"
echo "     order_example -e \"SELECT COUNT(*) FROM t_order;\""
echo "   curl http://localhost:9200/_cat/indices/order_index?v"
echo " 事件轨迹：open http://localhost:8080"
echo "=============================================="
