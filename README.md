# 输送设备缓冲挡块跨产线移交划转系统

## 项目简介

流水线缓冲挡块档案与跨产线移交系统，支持挡块建档、产线树分组、移交划转登记、时间区间筛选和单据打印。

## 技术栈

- 前端：Vue 3、Vite 5、Element Plus、Vue Router、Axios、Day.js
- 后端：Spring Boot 3、JDK 17、Spring Data JPA、Spring Data Redis、Maven
- 数据：MySQL 8、Redis 7
- 部署：Docker Compose、Nginx

## 端口说明

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3125 或 http://127.0.0.1:3125 |
| 后端 API | http://127.0.0.1:8125/api |
| MySQL | 127.0.0.1:3425 |
| Redis | 127.0.0.1:6425 |

端口来自根目录 `.env`，Docker 端口只绑定 `127.0.0.1`。

## 启动方式

```bash
cd qd-125
docker compose up -d --build
```

本地拆分验证：

```bash
cd backend
mvn compile -q

cd ../frontend
npm ci
npm run build
```

## Docker 构建说明

前端按 `package-lock.json` 执行 `npm ci` 和 `npm run build`；后端使用 Maven 构建；Compose 统一启动 MySQL、Redis、backend、frontend：

```bash
docker compose up -d --build
docker compose ps
```

## 常见问题

- `npm ci` 有 audit 提示不等于构建失败；以 `npm run build` 结果判断构建链路。
- API 报错：先确认后端容器和数据库初始化，再检查 `/api` 代理。
- 镜像拉取失败：调整 `.env` 中 `DOCKER_REGISTRY`。
