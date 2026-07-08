# ====================================================================
# 后端 Dockerfile - 分层缓存规范
# 约束：
#   1. 不使用 # syntax=docker/dockerfile:* 指令，仅依赖原生分层缓存
#   2. 依赖层与源码层分离：pom.xml/settings.xml 不变则复用依赖下载缓存
#   3. 基础镜像统一引用 DOCKER_REGISTRY 构建参数
# ====================================================================

ARG DOCKER_REGISTRY=docker.m.daocloud.io/library

# ---------- 第一阶段：依赖下载与编译 ----------
FROM ${DOCKER_REGISTRY}/maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先仅拷贝依赖描述文件，利用分层缓存复用依赖下载
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml ./
# 预下载依赖：pom.xml 未变更时本层命中缓存，不再重复全量下载
RUN mvn -B -s /root/.m2/settings.xml compile -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true

# 仅业务源码变更时才触发重新编译
COPY src ./src
RUN mvn -B -s /root/.m2/settings.xml clean package -Dmaven.test.skip=true

# ---------- 第二阶段：精简运行镜像 ----------
FROM ${DOCKER_REGISTRY}/eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/target/*.jar app.jar

# 容器内固定监听 8080（宿主机端口由 docker-compose 映射）
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
