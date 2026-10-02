-- ============================================================
-- 音乐推荐系统 — Docker 首次启动初始化
--
-- MySQL 容器启动时会自动执行 /docker-entrypoint-initdb.d/*.sql
-- （只在数据卷为空时执行，已存在则跳过）
--
-- Spring Boot 的 schema.sql + data.sql 会在建表后自动跑
-- 所以这里只需要确保库存在 + 字符集设置
-- ============================================================

CREATE DATABASE IF NOT EXISTS music_recommendation
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE music_recommendation;
