# 小光AI助手 - Linux 服务器部署指南

## 概述

本文档详细说明如何在 Linux 服务器上部署小光AI助手的后端数据库服务，包括 ChromaDB（向量数据库）和 Neo4j（图数据库）。

**推荐使用 Linux 部署**：相比 Windows，Linux 上的 ChromaDB 和 Neo4j 更稳定、性能更好。

## 服务器要求

### 硬件要求
- **CPU**: 2核心或以上
- **内存**: 4GB 或以上（推荐 8GB）
- **存储**: 20GB 可用空间

### 软件要求
- **操作系统**: Ubuntu 20.04/22.04、Debian 11/12、CentOS 8+ 或其他主流 Linux 发行版
- **Python**: 3.10 或更高版本
- **Java**: 17 或更高版本（推荐使用 OpenJDK）

### 网络要求
- **端口开放**:
  - `8000`: ChromaDB HTTP API
  - `7474`: Neo4j HTTP/Browser 接口
  - `7687`: Neo4j Bolt 协议

## 安装步骤

### 步骤 1: 安装依赖

#### Ubuntu/Debian

```bash
# 更新包列表
sudo apt update

# 安装 Python 3.10+
sudo apt install -y python3 python3-pip python3-venv

# 安装 Java 17
sudo apt install -y openjdk-17-jdk

# 验证安装
python3 --version
java -version
```

#### CentOS/RHEL

```bash
# 安装 Python 3.10+
sudo dnf install -y python3 python3-pip

# 安装 Java 17
sudo dnf install -y java-17-openjdk java-17-openjdk-devel

# 验证安装
python3 --version
java -version
```

### 步骤 2: 安装 ChromaDB

```bash
# 创建工作目录
mkdir -p ~/xiaoguang-server
cd ~/xiaoguang-server

# 创建 Python 虚拟环境（推荐）
python3 -m venv venv
source venv/bin/activate

# 安装 ChromaDB（使用国内镜像）
pip install chromadb -i https://pypi.tuna.tsinghua.edu.cn/simple

# 或使用阿里云镜像
# pip install chromadb -i https://mirrors.aliyun.com/pypi/simple/

# 验证安装
python3 -c "import chromadb; print('ChromaDB installed successfully')"
```

**注意**：ChromaDB 版本 1.3.4 在某些系统上存在稳定性问题，建议使用稳定版本：

```bash
# 如果遇到问题，尝试降级到稳定版本
pip install "chromadb>=0.5.0,<0.6.0"
```

### 步骤 3: 安装 Neo4j

#### 方法 1：使用包管理器（推荐）

**Ubuntu/Debian**:

```bash
# 添加 Neo4j 仓库
wget -O - https://debian.neo4j.com/neotechnology.gpg.key | sudo apt-key add -
echo 'deb https://debian.neo4j.com stable latest' | sudo tee /etc/apt/sources.list.d/neo4j.list

# 安装 Neo4j Community Edition
sudo apt update
sudo apt install -y neo4j

# 设置初始密码
sudo neo4j-admin set-initial-password xiaoguang123

# 启动 Neo4j
sudo systemctl start neo4j
sudo systemctl enable neo4j  # 开机自启

# 查看状态
sudo systemctl status neo4j
```

**CentOS/RHEL**:

```bash
# 添加 Neo4j 仓库
sudo rpm --import https://debian.neo4j.com/neotechnology.gpg.key
cat <<EOF | sudo tee /etc/yum.repos.d/neo4j.repo
[neo4j]
name=Neo4j RPM Repository
baseurl=https://yum.neo4j.com/stable/5
enabled=1
gpgcheck=1
EOF

# 安装 Neo4j
sudo dnf install -y neo4j

# 设置密码并启动（同上）
```

#### 方法 2：手动安装

```bash
# 下载 Neo4j
cd ~/xiaoguang-server
wget https://dist.neo4j.org/neo4j-community-5.14.0-unix.tar.gz

# 解压
tar -xzf neo4j-community-5.14.0-unix.tar.gz
mv neo4j-community-5.14.0 neo4j

# 设置密码
./neo4j/bin/neo4j-admin set-initial-password xiaoguang123

# 配置远程访问
sed -i 's/#server.default_listen_address=0.0.0.0/server.default_listen_address=0.0.0.0/' neo4j/conf/neo4j.conf
```

### 步骤 4: 配置服务

#### 配置 Neo4j 远程访问

编辑 Neo4j 配置文件：

```bash
# 包管理器安装
sudo nano /etc/neo4j/neo4j.conf

# 或手动安装
nano ~/xiaoguang-server/neo4j/conf/neo4j.conf
```

确保以下配置：

```properties
# 允许远程访问
server.default_listen_address=0.0.0.0

# HTTP 端口
server.http.listen_address=:7474

# Bolt 端口
server.bolt.listen_address=:7687
```

保存后重启 Neo4j：

```bash
# 包管理器安装
sudo systemctl restart neo4j

# 或手动安装
~/xiaoguang-server/neo4j/bin/neo4j restart
```

### 步骤 5: 创建启动脚本

#### ChromaDB 启动脚本

创建 `start-chromadb.sh`：

```bash
cat > ~/xiaoguang-server/start-chromadb.sh <<'EOF'
#!/bin/bash

# 工作目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 激活虚拟环境
source venv/bin/activate

# 创建数据目录
mkdir -p data/chromadb

# 启动 ChromaDB
echo "Starting ChromaDB on port 8000..."
chroma run --path data/chromadb --port 8000 --host 0.0.0.0
EOF

chmod +x ~/xiaoguang-server/start-chromadb.sh
```

#### 统一启动脚本

创建 `start-all.sh`：

```bash
cat > ~/xiaoguang-server/start-all.sh <<'EOF'
#!/bin/bash

echo "========================================="
echo "  小光AI助手 - 服务启动"
echo "========================================="
echo ""

# 启动 Neo4j（如果使用手动安装）
if [ -d "$HOME/xiaoguang-server/neo4j" ]; then
    echo "启动 Neo4j..."
    $HOME/xiaoguang-server/neo4j/bin/neo4j start
fi

# 等待 Neo4j 启动
sleep 5

# 启动 ChromaDB（在后台）
echo "启动 ChromaDB..."
nohup $HOME/xiaoguang-server/start-chromadb.sh > chromadb.log 2>&1 &

echo ""
echo "========================================="
echo "服务已启动！"
echo ""
echo "ChromaDB: http://$(hostname -I | awk '{print $1}'):8000"
echo "Neo4j:    http://$(hostname -I | awk '{print $1}'):7474"
echo ""
echo "查看日志: tail -f ~/xiaoguang-server/chromadb.log"
echo "========================================="
EOF

chmod +x ~/xiaoguang-server/start-all.sh
```

### 步骤 6: 配置防火墙

#### Ubuntu/Debian (ufw)

```bash
# 启用防火墙
sudo ufw enable

# 开放端口
sudo ufw allow 8000/tcp comment 'ChromaDB'
sudo ufw allow 7474/tcp comment 'Neo4j HTTP'
sudo ufw allow 7687/tcp comment 'Neo4j Bolt'

# 查看状态
sudo ufw status
```

#### CentOS/RHEL (firewalld)

```bash
# 开放端口
sudo firewall-cmd --permanent --add-port=8000/tcp
sudo firewall-cmd --permanent --add-port=7474/tcp
sudo firewall-cmd --permanent --add-port=7687/tcp

# 重新加载
sudo firewall-cmd --reload

# 查看状态
sudo firewall-cmd --list-ports
```

### 步骤 7: 启动服务

```bash
cd ~/xiaoguang-server
./start-all.sh
```

## 使用 systemd 管理服务（推荐）

### 创建 ChromaDB systemd 服务

```bash
sudo tee /etc/systemd/system/chromadb.service > /dev/null <<EOF
[Unit]
Description=ChromaDB Vector Database
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME/xiaoguang-server
Environment="PATH=$HOME/xiaoguang-server/venv/bin:/usr/local/bin:/usr/bin:/bin"
ExecStart=$HOME/xiaoguang-server/venv/bin/chroma run --path $HOME/xiaoguang-server/data/chromadb --port 8000 --host 0.0.0.0
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 启用服务
sudo systemctl daemon-reload
sudo systemctl enable chromadb
sudo systemctl start chromadb

# 查看状态
sudo systemctl status chromadb
```

### 管理服务

```bash
# 启动服务
sudo systemctl start chromadb neo4j

# 停止服务
sudo systemctl stop chromadb neo4j

# 重启服务
sudo systemctl restart chromadb neo4j

# 查看日志
sudo journalctl -u chromadb -f
sudo journalctl -u neo4j -f
```

## 验证部署

### 测试 ChromaDB

```bash
# 心跳检测
curl http://localhost:8000/api/v2/heartbeat

# 版本信息
curl http://localhost:8000/api/v2/version
```

### 测试 Neo4j

```bash
# HTTP 接口
curl http://localhost:7474

# 在浏览器中访问
firefox http://localhost:7474  # 或使用服务器IP
```

## 配置 Android 应用

更新 Android 应用的配置文件 `local.properties`：

```properties
sdk.dir=/path/to/android/sdk

# Neo4j 配置
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=xiaoguang123
NEO4J_DATABASE=neo4j
```

更新 `AppConfigManager.kt` 中的服务器地址：

```kotlin
val neo4jBaseUrl: String = "http://YOUR_SERVER_IP:7474"
val chromaBaseUrl: String = "http://YOUR_SERVER_IP:8000"
```

## 故障排除

### ChromaDB 启动失败

**检查端口占用**：

```bash
sudo lsof -i :8000
# 或
sudo netstat -tulpn | grep :8000
```

**查看日志**：

```bash
# systemd 方式
sudo journalctl -u chromadb -n 50

# 手动启动方式
tail -f ~/xiaoguang-server/chromadb.log
```

### Neo4j 启动失败

**检查内存**：

```bash
free -h
```

如果内存不足，调整 Neo4j 配置：

```properties
# /etc/neo4j/neo4j.conf 或 neo4j/conf/neo4j.conf
server.memory.heap.initial_size=512m
server.memory.heap.max_size=1g
server.memory.pagecache.size=512m
```

### ChromaDB 版本问题

如果遇到 ChromaDB 1.3.4 的稳定性问题：

```bash
# 降级到稳定版本
pip uninstall chromadb -y
pip install "chromadb>=0.5.0,<0.6.0"

# 清除旧数据
rm -rf ~/xiaoguang-server/data/chromadb/*

# 重启服务
sudo systemctl restart chromadb
```

## 数据备份

### 备份脚本

创建 `backup.sh`：

```bash
cat > ~/xiaoguang-server/backup.sh <<'EOF'
#!/bin/bash

BACKUP_DIR="$HOME/xiaoguang-backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "备份 ChromaDB..."
cp -r ~/xiaoguang-server/data/chromadb "$BACKUP_DIR/"

echo "备份 Neo4j..."
sudo neo4j-admin database dump neo4j --to-path="$BACKUP_DIR"

echo "备份完成: $BACKUP_DIR"
EOF

chmod +x ~/xiaoguang-server/backup.sh
```

### 配置定时备份

```bash
# 编辑 crontab
crontab -e

# 添加每天凌晨 2 点备份
0 2 * * * /home/YOUR_USERNAME/xiaoguang-server/backup.sh
```

## 性能优化

### ChromaDB 优化

```bash
# 使用 SSD 存储数据
# 确保数据目录在 SSD 上
mv ~/xiaoguang-server/data /mnt/ssd/xiaoguang-data
ln -s /mnt/ssd/xiaoguang-data ~/xiaoguang-server/data
```

### Neo4j 优化

编辑 `neo4j.conf`：

```properties
# 增加页面缓存（推荐物理内存的 50%）
server.memory.pagecache.size=2g

# 增加堆内存
server.memory.heap.initial_size=1g
server.memory.heap.max_size=2g

# 启用查询日志
dbms.logs.query.enabled=true
dbms.logs.query.threshold=1s
```

## 监控

### 使用 htop 监控资源

```bash
sudo apt install htop
htop
```

### 监控日志

```bash
# 实时查看 ChromaDB 日志
sudo journalctl -u chromadb -f

# 实时查看 Neo4j 日志
sudo tail -f /var/log/neo4j/neo4j.log
```

---

**文档版本**: v1.0
**最后更新**: 2025-11-18
**适用于**: Linux (Ubuntu/Debian/CentOS)
