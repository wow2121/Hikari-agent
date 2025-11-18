#!/bin/bash

echo "配置 Docker 国内镜像加速..."

# 创建 docker 配置目录
sudo mkdir -p /etc/docker

# 写入镜像配置
sudo tee /etc/docker/daemon.json > /dev/null <<EOF
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.ccs.tencentyun.com"
  ]
}
EOF

echo "✅ 配置已写入 /etc/docker/daemon.json"
echo ""
echo "重启 Docker 服务..."

sudo systemctl daemon-reload
sudo systemctl restart docker

echo "✅ Docker 已重启"
echo ""
echo "现在可以运行 ./deploy.sh 了"
