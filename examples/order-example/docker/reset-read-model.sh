#!/bin/sh
# ============================================================
# order-example 读模型重置脚本（Redis + ES）
# 用法：bash reset-read-model.sh [--yes] [--keep-index]
#   --yes         跳过交互确认（CI / 重复执行）
#   --keep-index  保留 ES 索引，仅用 _delete_by_query 清空文档
#                 （默认：删除 order_index 后按 es/init 脚本重建）
# 功能：停应用 → 清 Redis 订单缓存 → 清 ES → 启应用 → 就绪自检
# 前置：基础设施已由 setup.sh 启动（my-mysql / my-redis / my-es 在跑）
# 说明：详见 docs/design/examples/order-example-read-model-reset.md
# ============================================================
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "${SCRIPT_DIR}"

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_NAME="order_index"
ALIAS_NAME="order"
INDEX_SCRIPT="${SCRIPT_DIR}/es/init/order-es-create-index.sh"
REDIS_CONTAINER="my-redis"
APP_CONTAINER="my-order-example"
KEY_PATTERN="order:agg:*"

SKIP_CONFIRM="false"
KEEP_INDEX="false"
for arg in "$@"; do
  case "${arg}" in
    --yes) SKIP_CONFIRM="true" ;;
    --keep-index) KEEP_INDEX="true" ;;
    *)
      echo "未知参数：${arg}"
      echo "用法：bash reset-read-model.sh [--yes] [--keep-index]"
      exit 1
      ;;
  esac
done

if [ "${KEEP_INDEX}" = "true" ]; then
  ES_ACTION="仅清空文档（保留索引与 Mapping）"
else
  ES_ACTION="删除索引 ${INDEX_NAME} 并按 ${INDEX_SCRIPT} 重建"
fi

echo "==> 即将执行读模型重置："
echo "    Redis   : 删除 ${REDIS_CONTAINER} 中所有匹配 ${KEY_PATTERN} 的 key（不 flushdb）"
echo "    ES      : ${ES_ACTION}"
echo "    应用    : 重启 ${APP_CONTAINER}"
echo ""
echo "    ⚠️ 读模型清空后，MySQL 中的存量订单在读侧将查不到（需重灌或重新造数）。"

if [ "${SKIP_CONFIRM}" != "true" ]; then
  printf "确认执行？输入 yes 继续："
  read -r answer
  if [ "${answer}" != "yes" ]; then
    echo "已取消。"
    exit 0
  fi
fi

echo ""
echo "==> [1/6] 前置检查"
for c in "${REDIS_CONTAINER}" my-es; do
  if ! docker ps --format '{{.Names}}' | grep -qx "${c}"; then
    echo "容器 ${c} 未运行，请先执行：bash setup.sh"
    exit 1
  fi
done
if ! curl -sf "${ES_URL}/" >/dev/null 2>&1; then
  echo "Elasticsearch 未就绪（${ES_URL}），请检查 docker compose ps"
  exit 1
fi
if ! curl -sf "${ES_URL}/_cat/plugins" 2>/dev/null | grep -q analysis-ik; then
  echo "IK 插件未安装，重建索引会失败（analyzer order_default_ik not found）。"
  echo "安装方式见 setup.sh [5/9] 或 docker/es 说明。"
  exit 1
fi
if [ "${KEEP_INDEX}" != "true" ] && [ ! -f "${INDEX_SCRIPT}" ]; then
  echo "未找到索引创建脚本：${INDEX_SCRIPT}"
  exit 1
fi
echo "    检查通过"

echo "==> [2/6] 停止应用容器 ${APP_CONTAINER}"
# 先停应用是必需的：否则 RocketMQ 中堆积的 OrderDataSyncEvent 会被消费并立即写回旧结构投影
docker compose stop order-example
echo "    已停止"

echo "==> [3/6] 清空 Redis 订单缓存（${KEY_PATTERN}）"
# macOS 的 xargs 无 -r 选项，故先落临时文件判空，再分批 del
KEY_FILE=$(mktemp)
docker exec "${REDIS_CONTAINER}" redis-cli --scan --pattern "${KEY_PATTERN}" > "${KEY_FILE}" || true
KEY_COUNT=$(grep -c . "${KEY_FILE}" || true)
if [ "${KEY_COUNT}" -gt 0 ]; then
  xargs docker exec "${REDIS_CONTAINER}" redis-cli del < "${KEY_FILE}" > /dev/null
  echo "    已删除 ${KEY_COUNT} 个 key"
else
  echo "    无匹配的 key，跳过"
fi
rm -f "${KEY_FILE}"
echo "    当前 dbsize = $(docker exec "${REDIS_CONTAINER}" redis-cli dbsize)"

echo "==> [4/6] 清理 Elasticsearch"
if [ "${KEEP_INDEX}" = "true" ]; then
  if curl -sf "${ES_URL}/_cat/indices/${INDEX_NAME}" | grep -q "${INDEX_NAME}"; then
    curl -s -X POST "${ES_URL}/${INDEX_NAME}/_delete_by_query?refresh=true&conflicts=proceed" \
      -H 'Content-Type: application/json' \
      -d '{"query":{"match_all":{}}}' > /dev/null
    echo "    已清空 ${INDEX_NAME} 的全部文档（索引与 Mapping 保留）"
  else
    echo "    索引 ${INDEX_NAME} 不存在，跳过"
  fi
else
  curl -s -X DELETE "${ES_URL}/${INDEX_NAME}" > /dev/null || true
  echo "    已删除索引 ${INDEX_NAME}"
  bash "${INDEX_SCRIPT}"
  echo "    已重建索引 ${INDEX_NAME} 并绑定别名 ${ALIAS_NAME}"
fi

echo "==> [5/6] 启动应用容器 ${APP_CONTAINER}"
docker compose start order-example

echo "==> [6/6] 等待应用就绪"
i=0
until curl -sf http://localhost:9500/api/health >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "应用启动超时（60s），请查看日志：docker logs ${APP_CONTAINER}"
    exit 1
  fi
  sleep 1
done

echo ""
echo "=============================================="
echo " ✅ 读模型重置完成"
echo "=============================================="
echo " 应用健康检查 : $(curl -s http://localhost:9500/api/health)"
echo " ES 文档数    : $(curl -s "${ES_URL}/${INDEX_NAME}/_count" | sed -n 's/.*"count":\([0-9]*\).*/\1/p')"
echo " ES 别名      : $(curl -s "${ES_URL}/_cat/aliases/${ALIAS_NAME}?h=alias,index" || echo '（无）')"
echo " Redis dbsize : $(docker exec "${REDIS_CONTAINER}" redis-cli dbsize)"
echo "----------------------------------------------"
echo " 重新造数     : curl http://localhost:9500/api/testOrder"
echo " 查看 ES 数据 : curl -s '${ES_URL}/${INDEX_NAME}/_search?pretty' \\"
echo "                  -H 'Content-Type: application/json' \\"
echo "                  -d '{\"query\":{\"match_all\":{}}}'"
echo "=============================================="
