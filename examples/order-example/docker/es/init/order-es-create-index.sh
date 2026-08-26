#!/usr/bin/env bash
# 订单 ES 索引创建脚本
# 用法: bash order-es-create-index.sh
# 如需指定 ES 地址/鉴权，可修改下方 ES_URL 与 AUTH 变量，或在环境 export 后调用。

ES_URL="${ES_URL:-http://localhost:9200}"
AUTH="${ES_AUTH:-}"   # 形如 "user:password"，留空表示无鉴权
INDEX_NAME="order_index"
ALIAS_NAME="order"

# 若启用鉴权，给 curl 追加 -u 参数
CURL_AUTH=()
if [ -n "$AUTH" ]; then
  CURL_AUTH=(-u "$AUTH")
fi

curl -X PUT "${ES_URL}/${INDEX_NAME}?pretty" \
  "${CURL_AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "order_default_ik": {
          "type": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "orderId": { "type": "long" },
      "status": { "type": "integer" },
      "statusName": { "type": "keyword" },
      "paymentMethod": { "type": "integer" },
      "paymentMethodName": { "type": "keyword" },
      "currency": { "type": "keyword" },
      "remark": { "type": "text", "analyzer": "order_default_ik" },
      "cancelReason": { "type": "text", "analyzer": "order_default_ik" },
      "paymentSerialNo": { "type": "keyword" },
      "createdAt": { "type": "date" },
      "updatedAt": { "type": "date" },
      "paidAt": { "type": "date" },
      "totalAmount": { "type": "scaled_float", "scaling_factor": 100 },
      "platformDiscount": { "type": "scaled_float", "scaling_factor": 100 },
      "actualAmount": { "type": "scaled_float", "scaling_factor": 100 },
      "customer": {
        "properties": {
          "customerId": { "type": "long" },
          "customerName": {
            "type": "text",
            "analyzer": "order_default_ik",
            "fields": { "keyword": { "type": "keyword" } }
          }
        }
      },
      "shippingAddress": {
        "properties": {
          "province": { "type": "keyword" },
          "city": { "type": "keyword" },
          "district": { "type": "keyword" },
          "detail": { "type": "text", "analyzer": "order_default_ik" },
          "receiverName": { "type": "keyword" },
          "receiverPhone": { "type": "keyword" }
        }
      },
      "logisticsInfo": {
        "properties": {
          "trackingNo": { "type": "keyword" },
          "companyCode": { "type": "keyword" },
          "companyName": { "type": "keyword" },
          "shippedAt": { "type": "date" }
        }
      },
      "orderItems": {
        "properties": {
          "itemId": { "type": "long" },
          "productId": { "type": "long" },
          "productName": {
            "type": "text",
            "analyzer": "order_default_ik",
            "fields": { "keyword": { "type": "keyword" } }
          },
          "spec": { "type": "keyword" },
          "price": { "type": "scaled_float", "scaling_factor": 100 },
          "quantity": { "type": "integer" },
          "subtotal": { "type": "scaled_float", "scaling_factor": 100 }
        }
      },
      "itemProductNames": { "type": "keyword" },
      "itemProductNamesText": { "type": "text", "analyzer": "order_default_ik" }
    }
  }
}'

# 为索引绑定读写别名
curl -X POST "${ES_URL}/_aliases?pretty" \
  "${CURL_AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d "{
  \"actions\": [
    {
      \"add\": {
        \"index\": \"${INDEX_NAME}\",
        \"alias\": \"${ALIAS_NAME}\"
      }
    }
  ]
}"
