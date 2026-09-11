# 输送设备缓冲挡块跨产线移交划转系统

## 项目简介

流水线缓冲挡块档案与跨产线移交系统，支持挡块建档、产线树分组、移交划转登记、**移交确认（接收方确认/驳回）**、状态/产线/日期筛选、等待时长与完整流转记录、移交单与确认回执打印。

## 移交确认业务流程

1. **登记**：移交方在“移交台账管理”登记移交，单据进入 `PENDING` 待确认状态，此时**不**变更挡块产线绑定；同一挡块存在待确认移交单时不可重复登记。
2. **确认/驳回**：接收方在“移交确认”查看挡块规格（机型/厚度/规格模板）、原归属产线、目标产线、移交原因后：
   - 确认接收（必填处理说明）：状态变为 `CONFIRMED`，系统才把挡块当前产线绑定更新为目标产线，可打印确认回执；
   - 驳回（必填驳回说明）：状态变为 `REJECTED`，挡块保留原归属，绑定不变。
3. **筛选与跟踪**：支持按状态、相关产线（移出/移入）、日期区间、挡块编号筛选；待确认单据实时展示等待时长，详情抽屉展示完整流转记录（登记 → 确认/驳回 → 打印）。
4. **回执打印**：已确认单据可打印《缓冲挡块跨产线移交确认回执》，回执含确认时间、等待时长、处理说明和流转节点，并记录回执打印次数。

> 存量数据库升级：新版后端启动时会在 Web 服务接受请求前自动执行幂等迁移：
> `TransferConfirmSchemaMigration` 补齐 `block_transfer` 的确认字段与状态索引、创建 `transfer_flow_record` 表、
> 将升级前按旧流程登记（无流转记录）的历史单据回填为“已确认”并补登流转记录；
> `TransferSequenceSchemaMigration` 创建 `block_transfer_sequence` 按日期持久化发号器。
> 迁移不改动任何 `block_line_binding` 产线绑定，重复启动安全。
> 也可手动执行 `docker/mysql/init/z_V2_transfer_confirm.sql` 和 `docker/mysql/init/zz_V3_transfer_sequence.sql`（均幂等）。
> 注意：`/docker-entrypoint-initdb.d` 下脚本只在 MySQL 数据目录为空时执行，存量数据卷必须依赖上述启动迁移。

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
