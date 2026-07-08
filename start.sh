#!/usr/bin/env bash
# ====================================================================
# 一键构建启动脚本
# 功能：
#   1. 加载 .env 端口配置
#   2. 预检端口占用（占用即报错退出，并显示占用进程）
#   3. 执行 docker compose up --build -d
#   4. 等待后端健康就绪
#   5. 验证 127.0.0.1 与 localhost 一致性
#   6. 打印前端访问地址
# ====================================================================
set -euo pipefail

cd "$(dirname "$0")"

# ---------- 加载 .env ----------
if [ ! -f .env ]; then
  echo "[ERROR] 未找到 .env 配置文件" >&2
  exit 1
fi

# 安全解析 .env（避免含空格值被 shell 当作命令执行）
set -a
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    ''|\#*) continue ;;
  esac
  key="${line%%=*}"
  val="${line#*=}"
  # 去除首尾引号
  val="${val#\"}"; val="${val%\"}"
  val="${val#\'}"; val="${val%\'}"
  export "$key=$val"
done < .env
set +a

FRONTEND_PORT="${FRONTEND_PORT:-3008}"
BACKEND_PORT="${BACKEND_PORT:-8088}"
MYSQL_PORT="${MYSQL_PORT:-3309}"
REDIS_PORT="${REDIS_PORT:-6380}"

# ---------- 端口预检（占用即报错，不自动换端口） ----------
echo "============================================================"
echo "  端口占用预检 (IPv4 + IPv6 全量检测)"
echo "============================================================"
conflict=0
for p in "$FRONTEND_PORT" "$BACKEND_PORT" "$MYSQL_PORT" "$REDIS_PORT"; do
  occupants=$(lsof -nP -iTCP:"$p" -sTCP:LISTEN 2>/dev/null || true)
  if [ -n "$occupants" ]; then
    echo "[占用] 端口 $p 已被以下进程占用："
    echo "$occupants"
    conflict=1
  else
    echo "[空闲] 端口 $p 可用"
  fi
done

if [ "$conflict" -ne 0 ]; then
  echo ""
  echo "[ERROR] 存在端口冲突，已停止构建。请释放上述端口后重试。" >&2
  echo "        （本脚本不自动更换端口，遵循端口固定规则）" >&2
  exit 2
fi
echo ""

# ---------- Docker 构建 ----------
echo "============================================================"
echo "  开始 Docker Compose 构建（分层缓存：依赖层不变则复用）"
echo "============================================================"
docker compose --env-file .env up --build -d

# ---------- 等待后端就绪 ----------
echo ""
echo "============================================================"
echo "  等待后端服务就绪..."
echo "============================================================"
max_wait=90
waited=0
while [ "$waited" -lt "$max_wait" ]; do
  if curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${BACKEND_PORT}/api/blocks/spec-templates" 2>/dev/null | grep -q "200"; then
    echo "[就绪] 后端服务已响应 (耗时 ${waited}s)"
    break
  fi
  sleep 3
  waited=$((waited + 3))
  echo "  ... 等待中 (${waited}s / ${max_wait}s)"
done

if [ "$waited" -ge "$max_wait" ]; then
  echo "[WARN] 后端就绪超时，继续输出前端检测..." >&2
fi

# ---------- 一致性验证 127.0.0.1 vs localhost ----------
echo ""
echo "============================================================"
echo "  前端访问一致性验证 (127.0.0.1 vs localhost)"
echo "============================================================"
sleep 3
title_ip=$(curl -sS "http://127.0.0.1:${FRONTEND_PORT}" 2>/dev/null | grep -o '<title>[^<]*</title>' || echo "(无标题)")
title_lh=$(curl -sS "http://localhost:${FRONTEND_PORT}" 2>/dev/null | grep -o '<title>[^<]*</title>' || echo "(无标题)")
echo "  127.0.0.1:${FRONTEND_PORT} -> ${title_ip}"
echo "  localhost  :${FRONTEND_PORT} -> ${title_lh}"
if [ "$title_ip" = "$title_lh" ]; then
  echo "  [一致] 127.0.0.1 与 localhost 指向同一服务"
else
  echo "  [警告] 两者标题不一致，请检查" >&2
fi

# ---------- 打印访问地址 ----------
echo ""
echo "============================================================"
echo "  ✅ 构建启动完成"
echo "============================================================"
echo "  前端访问地址  : http://localhost:${FRONTEND_PORT}"
echo "  (与 http://127.0.0.1:${FRONTEND_PORT} 指向同一服务)"
echo "  后端 API 地址: http://localhost:${BACKEND_PORT}/api"
echo "  MySQL        : 127.0.0.1:${MYSQL_PORT}"
echo "  Redis        : 127.0.0.1:${REDIS_PORT}"
echo "============================================================"
