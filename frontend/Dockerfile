# ====================================================================
# 前端 Dockerfile - 分层缓存规范
# 约束：
#   1. 不使用 # syntax=docker/dockerfile:* 指令，仅依赖原生分层缓存
#   2. 依赖层与源码层分离：package*.json 不变则复用 npm ci 缓存
#   3. 基础镜像统一引用 DOCKER_REGISTRY 构建参数
# ====================================================================

ARG DOCKER_REGISTRY=docker.m.daocloud.io/library

# ---------- 第一阶段：依赖安装与构建 ----------
FROM ${DOCKER_REGISTRY}/node:20-alpine AS builder

WORKDIR /build

# 先仅拷贝依赖描述文件，利用分层缓存复用 npm ci
COPY .npmrc ./
COPY package.json package-lock.json ./
# package*.json 未变更时本层命中缓存，不再重复全量安装
RUN npm ci --registry=https://mirrors.cloud.tencent.com/npm/

# 仅业务源码变更时才触发重新构建
COPY . .
RUN npm run build

# ---------- 第二阶段：Nginx 部署静态资源 ----------
FROM ${DOCKER_REGISTRY}/nginx:alpine

WORKDIR /usr/share/nginx/html

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 容器内固定监听 80（宿主机端口由 docker-compose 映射）
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
