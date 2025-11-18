#!/bin/bash

echo "========================================="
echo "  小光AI助手 - 停止 Docker 服务"
echo "========================================="
echo ""

echo "🛑 正在停止服务..."
if ! docker-compose down; then
    echo ""
    echo "❌ 停止服务失败"
    exit 1
fi

echo ""
echo "✅ 服务已停止"
echo ""

# 询问是否删除数据
read -p "是否删除所有数据? (y/N): " DELETE_DATA
if [[ "$DELETE_DATA" =~ ^[Yy]$ ]]; then
    echo ""
    echo "⚠️  正在删除数据..."
    docker-compose down -v
    rm -rf data
    echo "✅ 数据已删除"
else
    echo ""
    echo "ℹ️  数据已保留在 data 目录中"
fi

echo ""
