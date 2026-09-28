# 🎵 基于混合推荐引擎的智能音乐播放平台

> 南昌应用技术师范学院 · 软件工程专业毕业设计  
> 作者：钟靖

一套完整的音乐推荐系统，后端基于 **Spring Boot 3.2** + **Thymeleaf**，同时提供 **Web 端** 和 **微信小程序端** 两种访问方式。核心亮点是四种策略动态加权的**混合推荐引擎**，并附带离线评估脚本、性能优化清单和毕设全流程文档。

---

## ✨ 核心亮点

### 🧠 混合推荐引擎
- **协同过滤**（UserCF + ItemCF，皮尔逊相关系数 + Jaccard 相似度融合）
- **内容推荐**（风格 × 0.5 + 歌手 × 0.4 + 流行度 × 0.1）
- **动态权重**：根据用户交互量自动切换（冷启动 → 稀疏 → 活跃 三档）
- **冷启动退化**：新用户 userId=0 时自动走「热门 50% + 随机探索 50%」
- **可观测诊断后台**：实时查看三算法独立推荐结果、全局统计、风格分布

### 📱 双端统一
- **Web 前端**：Thymeleaf 服务端渲染 + 原生 JS/CSS 动画
- **微信小程序**：原生开发，同一套 REST API
- 统一鉴权（Spring Security + JWT + BCrypt）

### ⚡ 性能优化
- Gzip 压缩（HTML/CSS/JS/JSON/SVG）
- 静态资源 7 天强缓存 + immutable
- Caffeine 本地缓存（TTL=5min，上限 1000 条）
- Druid 连接池（自带 `/druid/` 监控页面）
- Thymeleaf fragments 复用（head / sidebar / player 等）
- Google Fonts 瘦身 + `display=swap` 防 FOIT

### 📊 可量化评估
- 离线评估脚本：留一法（LOO）+ Precision@K / NDCG@K
- 30 用户 × 678 交互数据上的完整评估报告
- 43 个单元测试全绿（推荐引擎 + 评估器 + 用户行为统计）

---

## 🛠 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17（JDK 22 运行） |
| 后端框架 | Spring Boot | 3.2.12 |
| ORM | Spring Data JPA | 3.2.x |
| 安全 | Spring Security | 6.2.x + BCrypt + JWT |
| 数据库 | MySQL | 8.0+ |
| 连接池 | Druid | 1.2.24 |
| 缓存 | Caffeine | Spring Boot 3.x 自带 |
| 模板 | Thymeleaf | 3.1.x |
| API 文档 | SpringDoc OpenAPI | 2.6.0 |
| 验证码 | Kaptcha | 2.3.2 |
| 前端 | 原生 HTML/CSS/JS + ECharts 5.5 | — |
| 小程序 | 微信原生框架 | 基础库 3.x |
| 构建 | Maven | 3.9+ |
| 测试 | JUnit 5 + Mockito | Spring Boot Test |

---

## 📁 项目结构

```
毕业设计/
├── music-recommendation/          # ⭐ Spring Boot 后端（核心）
│   ├── src/main/java/            # Java 源码
│   │   ├── controller/           # 13 个 Controller（Web + API）
│   │   ├── service/              # Service 接口 + Impl 分离
│   │   │   └── recommendation/   # ⭐ 推荐引擎核心（4 个类）
│   │   ├── repository/           # Spring Data JPA
│   │   ├── entity/               # JPA 实体（6 个）
│   │   ├── config/               # Security + JWT + DataInitializer
│   │   └── common/               # ApiResponse 统一响应
│   ├── src/main/resources/
│   │   ├── templates/            # Thymeleaf 模板（20+ 页面 + fragments）
│   │   ├── static/               # CSS / JS / SVG 封面
│   │   ├── schema.sql            # 数据库 DDL
│   │   └── application.yml       # 主配置
│   └── pom.xml
│
├── music-miniprogram/             # 📱 微信小程序端
│   ├── app.js / app.json / app.wxss
│   ├── utils/request.js          # API 请求封装
│   └── pages/                    # 6 个页面
│       ├── index/  search/  playlist/
│       ├── mine/   login/   song-detail/
│
├── scripts/                       # 🛠 辅助脚本
│   ├── gen_data.py               # 造用户 × 交互数据
│   ├── add_sparse_users.py       # 造冷启动稀疏用户
│   ├── eval.py                   # 离线评估（LOO + P@K + NDCG）
│   ├── gen_seed_data.py          # 歌单种子数据
│   ├── gen_kaiti_v2.py           # 开题报告生成
│   ├── gen_experiment_doc.py     # 实验章节 Word 生成
│   ├── test_api.ps1              # 41 条 API 集成测试
│   └── gen_ppt.ps1               # 答辩 PPT 生成
│
├── docs/                          # 📚 毕设文档
│   ├── 开题报告.docx
│   ├── 文献综述.docx
│   ├── 中期检查报告.docx
│   └── 第5章_实验结果与分析.docx
│
├── defense/                       # 🎓 答辩材料
│   └── 答辩PPT.html              # 浏览器全屏演示
│
└── 文献/                          # 📖 参考文献 PDF
```

---

## 🚀 快速开始

### 1. 环境准备
```
JDK 17+（推荐 JDK 22） | Maven 3.9+ | MySQL 8.0+（端口 3306）
```

### 2. 初始化数据库
```sql
CREATE DATABASE IF NOT EXISTS music_recommendation
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 启动后端
```powershell
cd music-recommendation
mvn spring-boot:run
```

启动后自动执行 `schema.sql` 建表 + 种子数据初始化（70 首歌 + 6 个歌单 + 4 个初始用户）。

### 4. 访问

| 地址 | 说明 |
|------|------|
| http://localhost:8080 | 🏠 Web 首页 |
| http://localhost:8080/login | 🔐 登录 |
| http://localhost:8080/swagger-ui.html | 📖 API 文档 |
| http://localhost:8080/druid/ | 📊 Druid 监控（无密码） |
| http://localhost:8080/recommend/daily | 🎧 每日推荐 |
| http://localhost:8080/ranking | 🏆 排行榜 |
| http://localhost:8080/profile/stats | 📈 我的品味（ECharts 三图） |

### 5. 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | ROLE_ADMIN |
| user | 123456 | ROLE_USER |

### 6. 跑测试
```powershell
cd music-recommendation
mvn test          # 43 个单元测试
../scripts/test_api.ps1   # 41 条 API 集成测试
```

### 7. 小程序端
1. 打开微信开发者工具
2. 导入项目 → 目录选 `music-miniprogram/`
3. **AppID 选测试号**，**详情 → 本地设置 → ✅ 不校验合法域名**
4. 编译运行

### 8. 离线评估
```powershell
python scripts/gen_data.py          # 造数据
python scripts/eval.py             # 评估 → 输出 P@K / NDCG
```

---

## 🏗 推荐引擎架构

```
用户行为 (播放/点赞/评论)
        │
        ▼
┌─────────────────────────────┐
│   HybridRecommenderService   │  ← 动态权重调度
│  (冷启动/稀疏/活跃 三档切换)  │
└──────┬──────┬──────┬─────────┘
       │      │      │
       ▼      ▼      ▼
   ┌──────┐ ┌──────┐ ┌──────────┐
   │UserCF│ │ItemCF│ │Content   │
   │皮尔逊│ │Jaccard│ │genre×0.5│
   │      │ │      │ │artist×0.4│
   └──┬───┘ └──┬───┘ └────┬─────┘
      └────────┼──────────┘
               ▼
        加权融合 + 随机探索
               │
               ▼
         Top-N 推荐列表
```

冷启动退化路径：`userId=0` → 热门 50% + 随机 50%

---

## 📊 评估结果（2026-09）

| 场景 | 算法 | P@5 | P@10 | NDCG@5 |
|------|------|-----|------|--------|
| 活跃用户 (n=25) | CB 内容推荐 | **0.400** | **0.640** | **0.271** |
| | CF 协同过滤 | 0.160 | 0.360 | 0.121 |
| | Hybrid 混合 | 0.280 | 0.360 | 0.196 |
| 稀疏冷启动 (n=5) | CB | **0.200** | **0.200** | **0.077** |
| | CF | 0.000 | 0.400 | 0.000 |
| | Pop 热门基准 | 0.000 | 0.000 | 0.000 |

> 完整分析见 `docs/第5章_实验结果与分析.docx`

---

## 🎓 毕设材料索引

| 文档 | 路径 |
|------|------|
| 开题报告 | `docs/开题报告.docx` |
| 文献综述 | `docs/文献综述.docx` |
| 中期检查 | `docs/中期检查报告.docx` |
| 实验章节 | `docs/第5章_实验结果与分析.docx` |
| 答辩 PPT | `defense/答辩PPT.html`（浏览器 F11 全屏） |
| 后端详细 README | [music-recommendation/README.md](./music-recommendation/README.md) |

---

## 📄 License

MIT License · 仅供学习使用
