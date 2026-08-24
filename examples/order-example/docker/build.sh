#!/bin/sh
# 构建 order-example 镜像
# 用法：
#   ./build.sh              使用默认标签 order-example:2.0.0
#   ./build.sh <镜像标签>    自定义标签，例如 ./build.sh order-example:latest
set -e

IMAGE_TAG="${1:-order-example:2.0.0}"

# 进入脚本所在目录的上级（order-example 模块根），确保基于 target 构建
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TARGET_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)/target

if [ ! -f "${TARGET_DIR}/order-example.jar" ]; then
  echo "未找到 ${TARGET_DIR}/order-example.jar，请先执行 mvn package 编译。"
  exit 1
fi

if [ ! -f "${TARGET_DIR}/Dockerfile" ]; then
  echo "未找到 ${TARGET_DIR}/Dockerfile，请先执行 mvn package（已配置自动复制）。"
  exit 1
fi

echo "开始构建镜像：${IMAGE_TAG}"
docker build -t "${IMAGE_TAG}" -f "${TARGET_DIR}/Dockerfile" "${TARGET_DIR}"
echo "构建完成：${IMAGE_TAG}"
