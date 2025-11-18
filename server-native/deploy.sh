#!/bin/bash
set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  小光AI助手 - Docker 一键部署 (Linux)${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# 检查是否以 root 运行
if [ "$EUID" -eq 0 ]; then
    echo -e "${YELLOW}⚠️  警告: 检测到以 root 用户运行${NC}"
    echo -e "${YELLOW}   建议使用普通用户运行，并将用户添加到 docker 组${NC}"
    echo ""
fi

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ 错误: 未检测到 Docker${NC}"
    echo ""
    echo -e "请先安装 Docker:"
    echo -e "  ${GREEN}Ubuntu/Debian:${NC} sudo apt update && sudo apt install -y docker.io docker-compose"
    echo -e "  ${GREEN}CentOS/RHEL:${NC}   sudo dnf install -y docker docker-compose"
    echo ""
    exit 1
fi

# 检查 docker-compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ 错误: 未检测到 docker-compose${NC}"
    echo ""
    echo -e "请先安装 docker-compose:"
    echo -e "  ${GREEN}Ubuntu/Debian:${NC} sudo apt install -y docker-compose"
    echo -e "  ${GREEN}CentOS/RHEL:${NC}   sudo dnf install -y docker-compose"
    echo ""
    exit 1
fi

# 检查 Docker 是否运行
if ! docker ps &> /dev/null; then
    echo -e "${YELLOW}⚠️  Docker 服务未运行，正在尝试启动...${NC}"

    # 尝试启动 Docker
    if command -v systemctl &> /dev/null; then
        sudo systemctl start docker
        sleep 2

        if ! docker ps &> /dev/null; then
            echo -e "${RED}❌ Docker 启动失败${NC}"
            echo ""
            echo -e "请手动启动 Docker: ${GREEN}sudo systemctl start docker${NC}"
            exit 1
        fi
        echo -e "${GREEN}✅ Docker 已启动${NC}"
    else
        echo -e "${RED}❌ 无法自动启动 Docker${NC}"
        echo -e "请手动启动 Docker 服务"
        exit 1
    fi
fi

echo -e "${GREEN}✅ Docker 环境检查通过${NC}"
echo ""

# 获取当前用户
CURRENT_USER=$(whoami)

# 检查用户是否在 docker 组中
if ! groups $CURRENT_USER | grep -q '\bdocker\b'; then
    echo -e "${YELLOW}⚠️  当前用户不在 docker 组中${NC}"
    echo -e "${YELLOW}   建议执行以下命令:${NC}"
    echo -e "   ${GREEN}sudo usermod -aG docker $CURRENT_USER${NC}"
    echo -e "   ${GREEN}newgrp docker${NC}"
    echo ""
fi

# 创建数据目录
echo -e "${BLUE}📁 创建数据目录...${NC}"
mkdir -p data/chromadb
mkdir -p data/neo4j/data
mkdir -p data/neo4j/logs
mkdir -p data/neo4j/import
mkdir -p data/neo4j/plugins

echo -e "${GREEN}✅ 数据目录已创建${NC}"
echo ""

# 检查端口占用
check_port() {
    local port=$1
    local service=$2

    if ss -tuln | grep -q ":$port "; then
        echo -e "${RED}❌ 端口 $port 已被占用 ($service)${NC}"
        echo -e "   请停止占用端口的服务或修改 docker-compose.yml 中的端口配置"
        return 1
    fi
    return 0
}

echo -e "${BLUE}🔍 检查端口占用...${NC}"
if ! check_port 8000 "ChromaDB" || ! check_port 7474 "Neo4j HTTP" || ! check_port 7687 "Neo4j Bolt"; then
    echo ""
    exit 1
fi
echo -e "${GREEN}✅ 端口检查通过${NC}"
echo ""

# 拉取最新镜像
echo -e "${BLUE}📥 拉取 Docker 镜像 (首次运行需要下载，请耐心等待)...${NC}"
echo ""

if ! docker-compose pull; then
    echo ""
    echo -e "${RED}❌ 镜像拉取失败${NC}"
    echo ""
    exit 1
fi

echo ""
echo -e "${GREEN}✅ 镜像拉取完成${NC}"
echo ""

# 启动服务
echo -e "${BLUE}🚀 正在启动服务...${NC}"
echo ""

if ! docker-compose up -d; then
    echo ""
    echo -e "${RED}❌ 服务启动失败${NC}"
    echo ""
    echo "查看日志: docker-compose logs"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ 服务启动成功${NC}"
echo ""

# 等待服务就绪
echo -e "${YELLOW}⏳ 等待服务就绪 (约需 30-60 秒)...${NC}"
echo ""

# 等待 ChromaDB
echo -e "${BLUE}   检查 ChromaDB...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8000/api/v1/heartbeat > /dev/null 2>&1; then
        echo -e "${GREEN}   ✅ ChromaDB 已就绪${NC}"
        break
    fi

    if [ $i -eq 30 ]; then
        echo -e "${RED}   ⚠️  ChromaDB 启动超时${NC}"
        echo -e "   查看日志: docker-compose logs chromadb"
    fi

    sleep 2
done

# 等待 Neo4j
echo -e "${BLUE}   检查 Neo4j...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:7474 > /dev/null 2>&1; then
        echo -e "${GREEN}   ✅ Neo4j 已就绪${NC}"
        break
    fi

    if [ $i -eq 30 ]; then
        echo -e "${RED}   ⚠️  Neo4j 启动超时${NC}"
        echo -e "   查看日志: docker-compose logs neo4j"
    fi

    sleep 2
done

echo ""

# 显示服务状态
echo -e "${BLUE}📊 容器状态:${NC}"
echo ""
docker-compose ps
echo ""

# 获取服务器 IP
SERVER_IP=$(hostname -I | awk '{print $1}')

echo -e "${BLUE}=========================================${NC}"
echo -e "${GREEN}  服务已启动！${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""
echo -e "${BLUE}📊 服务信息:${NC}"
echo ""
echo -e "  ${GREEN}ChromaDB:${NC}"
echo -e "    - 本地访问:  http://localhost:8000"
echo -e "    - 远程访问:  http://${SERVER_IP}:8000"
echo -e "    - 健康检查:  http://localhost:8000/api/v1/heartbeat"
echo ""
echo -e "  ${GREEN}Neo4j:${NC}"
echo -e "    - Web界面:   http://localhost:7474"
echo -e "    - 远程访问:  http://${SERVER_IP}:7474"
echo -e "    - Bolt协议:  bolt://${SERVER_IP}:7687"
echo -e "    - 用户名:    neo4j"
echo -e "    - 密码:      xiaoguang123"
echo ""
echo -e "${BLUE}=========================================${NC}"
echo ""
echo -e "${BLUE}💡 常用命令:${NC}"
echo ""
echo -e "  ${GREEN}查看日志:${NC}     docker-compose logs -f"
echo -e "  ${GREEN}停止服务:${NC}     ./stop-docker.sh"
echo -e "  ${GREEN}重启服务:${NC}     docker-compose restart"
echo -e "  ${GREEN}查看状态:${NC}     docker-compose ps"
echo ""
echo -e "${BLUE}=========================================${NC}"
echo ""
echo -e "${BLUE}📱 Android 应用配置:${NC}"
echo ""
echo -e "在 ${GREEN}app/src/main/java/com/xiaoguang/assistant/config/AppConfigManager.kt${NC} 中:"
echo ""
echo -e "  ${YELLOW}val neo4jBaseUrl = \"http://${SERVER_IP}:7474\"${NC}"
echo -e "  ${YELLOW}val chromaBaseUrl = \"http://${SERVER_IP}:8000\"${NC}"
echo ""
echo -e "${BLUE}=========================================${NC}"
echo ""
