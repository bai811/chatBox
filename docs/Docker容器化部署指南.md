# Docker 容器化部署指南

本指南为您提供一套基于 `Docker` 和 `Docker Compose` 的全量容器化方案。您可以非常轻松地将包含 Spring Boot 后端、React/Vite 前端和 Redis 的整个套件一键部署到任意安装了 Docker 的云服务器上。

## 1. 部署架构概览

我们采用了一键式容器编排架构，主要包括 3 个容器（Service）：

- **`team-rag-redis`**: 提供短期记忆上下文和会话机制的高速缓存。持久化挂载至宿主机的 Docker Volume。
- **`team-rag-backend`**: 包含业务逻辑的 Spring Boot 服务（Java 21），不暴露公网，只通过内部网络响应 API。
- **`team-rag-frontend`**: 使用 Alpine 版的 Nginx 构建，既托管 Vite 编译出的静态 `dist` 文件资源，又反向代理 `/api` 流量到 `backend:8080`。对外开放 `80` 端口。

## 2. 准备工作

请确保您的云端服务器或本地机器上已安装：
- [Docker Engine](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)

## 3. 环境配置 (.env 文件)

在大模型服务与向量库接入配置中，系统涉及了隐私安全要求极高的密钥。
在运行项目之前，请在项目根目录（`docker-compose.yml` 所在的目录）下，创建一个名为 `.env` 的文件，填入您的密钥：

```bash
# 文件名: .env
# =============== 阿里百炼 API 配置 ===============
DASH_SCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxx

# =============== Pinecone 向量环境配置 ===============
PINECONE_API_KEY=pcsk_xxxxxxxxxxxxxxxxxxxxxxxxxx

# Pinecone 的索引名称（根据您实际建的索引配置）
PINECONE_INDEX=team-rag-index

# 您创建的 Pinecone 的可用区地域
PINECONE_REGION=us-east-1

# 可选项，如果需要隔离开不冲突，可填写独立命名空间
PINECONE_NAMESPACE=team-rag-namespace
```

> **注意**：`.env` 文件已被 `.gitignore` 保护，不会被提交到公开 Git 仓库，确保了安全性。

## 4. 一键部署与启动

在项目根目录下通过一条命令即可打通并启动整个应用网络：

```bash
# 构建镜像并在后台脱机运行 (Detached mode)
docker-compose up -d --build
```

执行时，Docker 会自动：
1. 下载 `Redis` 镜像并启动
2. 通过内部的 `maven` 环境编译后端生成 JAR 包，再将其复制到轻量的 `jre` 环境中运行
3. 启动 `Node` 环境编译前端 React 项目生成 `dist` 文件夹，并将其拷贝入轻量的 Nginx 系统中提供对外服务。

## 5. 验证是否部署成功

- 前端：直接在浏览器中访问您的服务器 IP `http://<您的服务器IP>/`
- 接口文档（Swagger）：访问 `http://<您的服务器IP>/doc.html` 查看。因为我们在 Nginx 侧特别为该路由配置了放行规则，所以它依然能正确导向到后端 Swagger UI。
- 后台终端日志：
  ```bash
  docker-compose logs -f backend
  ```

## 6. 日常运维命令

```bash
# 停止所有服务容器但不删除缓存卷
docker-compose stop

# 彻底下线应用并移除构建的所有容器实例
docker-compose down

# 进入后端容器内部调试
docker exec -it team-rag-backend /bin/sh

# 前端发版热更新（仅重新构建 frontend 单服务）
docker-compose up -d --build frontend
```
