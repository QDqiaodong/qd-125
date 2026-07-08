#!/usr/bin/env bash
# 停止并清理本项目的容器（保留数据卷）
cd "$(dirname "$0")"
docker compose --env-file .env down
echo "[完成] 容器已停止（数据卷保留，如需清除数据请加 -v 参数手动执行）"
