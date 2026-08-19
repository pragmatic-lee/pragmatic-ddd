#!/usr/bin/env bash
#
# 本地 Maven Install 脚本 —— pragmatic-ddd 多模块工程
#
# 作用：将框架各模块安装到本地 Maven 仓库（~/.m2/repository），供本地其他项目引用。
# 依据：docs/design/plans/engineering/multi-module-publish-proposal.md（B 方案：最小化父 POM + BOM + 聚合器分离）
#
# 用法：
#   ./install-local.sh                 # 全量安装整个 reactor（推荐，Maven 自动按依赖拓扑排序）
#   ./install-local.sh core rocketmq   # 选择性安装指定模块及其依赖（-am 自动带上依赖）
#   ./install-local.sh --with-examples # 全量安装并连 examples 一起（需 -Pexamples）
#   ./install-local.sh --run-tests     # 安装时执行单元测试（默认 -DskipTests 跳过测试执行）
#
# 说明：
#   - 各业务模块 <parent> 指向最小化父 POM pragmatic-ddd-parent:1.0.0，
#     在 reactor 内由 Maven 统一解析，无需预先单独安装父 POM。
#   - 使用 `-pl <模块> -am` 时，Maven 会先构建并安装被依赖的模块（如 core），顺序无需手动维护。
#   - 默认不构建 examples（根 POM 将其置于 -Pexamples profile），仅安装框架 7 个模块。
#   - 默认通过 `-DskipTests` 跳过单元测试执行（保留测试编译），避免测试依赖外部环境导致 install 失败。

set -euo pipefail

# 模块标识（artifactId 与 <modules> 顺序一致，供选择性与文档说明）
MODULES=(
    "pragmatic-ddd-parent"     # 最小化父 POM（构建配置 + 第三方版本管理），无兄弟依赖
    "pragmatic-ddd-bom"        # 版本清单（仅管理内部模块版本），依赖 parent
    "pragmatic-ddd-core"       # 核心库，依赖 parent
    "pragmatic-ddd-rocketmq"   # RocketMQ 集成，依赖 parent + core
    "pragmatic-ddd-kafka"      # Kafka 集成（planned），依赖 parent + core
    "pragmatic-ddd-mybatis"    # MyBatis 集成（planned），依赖 parent + core
    "pragmatic-ddd-spring-boot" # Spring Boot starter（planned），依赖 parent
)

WITH_EXAMPLES="false"
RUN_TESTS="false"

# ---- 参数解析 ----
ARGS=()
for arg in "$@"; do
    case "$arg" in
        --with-examples) WITH_EXAMPLES="true" ;;
        --run-tests)     RUN_TESTS="true" ;;
        *)               ARGS+=("$arg") ;;
    esac
done

# 受控字符串：空串时经 word-splitting 展开为零个参数（无空格，安全）
PROFILE_ARG=""
if [ "$WITH_EXAMPLES" = "true" ]; then
    PROFILE_ARG="-Pexamples"
fi
# 默认跳过单元测试执行（-DskipTests，保留测试编译）；--run-tests 时置空
SKIP_TESTS_ARG="-DskipTests"
if [ "$RUN_TESTS" = "true" ]; then
    SKIP_TESTS_ARG=""
fi

# ---- 选择性安装：校验传入的模块名在清单内 ----
if [ "${#ARGS[@]}" -gt 0 ]; then
    for name in "${ARGS[@]}"; do
        if ! printf '%s\n' "${MODULES[@]}" | grep -qx "$name"; then
            echo "错误：未知模块 '$name'。可用模块：${MODULES[*]}" >&2
            exit 1
        fi
    done
fi

# ---- 全量安装整个 reactor（推荐） ----
if [ "${#ARGS[@]}" -eq 0 ]; then
    echo "==> 全量安装整个 reactor（含 ${#MODULES[@]} 个框架模块）..."
    mvn -f pom.xml install $PROFILE_ARG $SKIP_TESTS_ARG
    echo "==> 安装完成。"
    exit 0
fi

# ---- 选择性安装：-pl 指定模块，-am 自动带上其依赖并按拓扑排序 ----
echo "==> 选择性安装模块：${ARGS[*]}（-am 自动安装其依赖）..."
mvn -f pom.xml install $PROFILE_ARG $SKIP_TESTS_ARG \
    -pl "$(IFS=,; echo "${ARGS[*]}")" \
    -am
echo "==> 安装完成。"
