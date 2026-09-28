# 🎵 音乐资源推荐与歌单管理系统

> 软件工程专业毕业设计 · Spring Boot + 微信小程序双端

## ✨ 核心特性

- **混合推荐引擎**：协同过滤（UserCF + ItemCF，皮尔逊 + Jaccard 融合）+ 内容推荐（genre×0.5 + artist×0.4）+ 流行度 + 随机探索，四种策略动态加权融合
- **动态权重**：根据用户交互量自动切换权重（冷启动 → 稀疏 → 活跃三档）
- **双端统一后端**：Thymeleaf Web 前端 + 微信小程序共用一套 Spring Boot REST API
- **推荐引擎诊断后台**：实时查看三算法独立推荐结果、全局统计、风格分布

## 🛠 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 22 |
| Spring Boot | 3.2.12 |
| Spring Data JPA | 3.2.x |
| Spring Security | 6.2.x |
| MySQL | 8.0.40 |
| Thymeleaf | 3.1.x |
| SpringDoc OpenAPI | 2.6.0 |
| Kaptcha | 2.3.2 |

## 📁 目录结构

```
music-recommendation/
├── src/main/java/com/music/recommendation/
│   ├── controller/          # 8 个 Controller
│   │   ├── HomeController           # 首页（推荐 + Thymeleaf）
│   │   ├── MusicApiController       # 小程序 REST API（/api/**）
│   │   ├── AuthApiController        # 登录/注册 API
│   │   ├── AdminController          # 管理后台页面
│   │   ├── AdminRecommendController # 推荐引擎诊断 API
│   │   └── ...
│   ├── service/             # 6 个 Service
│   │   ├── SongService
│   │   ├── PlaylistService
│   │   └── recommendation/         # ⭐ 推荐引擎核心
│   │       ├── HybridRecommenderService      # 混合引擎（动态权重）
│   │       ├── CollaborativeRecommender      # 协同过滤
│   │       └── ContentBasedRecommender       # 内容推荐
│   ├── repository/          # Spring Data JPA Repository
│   ├── entity/              # JPA 实体
│   └── config/              # SecurityConfig + CORS
├── src/main/resources/
│   ├── templates/           # Thymeleaf 模板（14 个 + fragments）
│   ├── static/              # CSS / JS / images
│   ├── schema.sql           # 数据库初始化 DDL
│   ├── data.sql             # 种子数据（70 首歌 + 用户 + 初始交互）
│   └── application.yml
└── pom.xml

music-miniprogram/           # 微信小程序（原生开发）
├── app.js / app.json / app.wxss
├── utils/request.js          # API 请求封装
└── pages/
    ├── index/               # 发现音乐（首页 + 推荐）
    ├── search/              # 搜索
    ├── playlist/            # 歌单
    ├── mine/                # 我的
    ├── login/               # 登录注册
    └── song-detail/         # 歌曲详情

../scripts/                   # 辅助脚本（造数据/评估/生成文档）
  ├── eval.py                   # 离线评估（Pure Python, LOO + Precision/NDCG）
  ├── gen_data.py               # 造数据（50+ 用户 × 1500+ 交互）
  └── gen_experiment_doc.py     # 生成 Word 实验章节
```

## 🚀 快速启动

### 1. 环境要求

- JDK 22
- Maven 3.9+
- MySQL 8.0+（端口 3306，root 密码 123）
- 微信开发者工具（可选，用于小程序端）

### 2. 初始化数据库

```sql
-- 先建库
CREATE DATABASE IF NOT EXISTS music_recommendation
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

项目启动时会自动执行 `schema.sql` + `data.sql`，建表并插入 70 首歌和 4 个初始用户。

### 3. 启动后端

```powershell
cd music-recommendation
mvn spring-boot:run
```

首次编译需下载依赖（约 2-3 分钟）。启动成功后：

| 访问点 | 说明 |
|---|---|
| http://localhost:8080 | Web 首页 |
| http://localhost:8080/login | 登录页 |
| http://localhost:8080/swagger-ui.html | 📖 Swagger API 文档 |
| http://localhost:8080/admin/recommend | ⚙️ 推荐引擎诊断（需 admin 登录） |
| http://localhost:8080/api/home?userId=1 | 推荐 API 测试 |

### 4. 默认账号

| 用户名 | 密码 | 角色 | 说明 |
|---|---|---|---|
| admin | 123456 | ROLE_ADMIN | 管理后台 |
| user | 123456 | ROLE_USER | 普通用户 |

> data.sql 里造的新用户（user05 ~ user25）密码也是 `123456`。

### 5. 小程序端（可选）

1. 打开微信开发者工具
2. 导入项目 → 目录选 `music-miniprogram/`
3. **AppID 选测试号**（不用申请）
4. **详情 → 本地设置 → ✅ 不校验合法域名**（开发期必勾）
5. 编译后模拟器里就能看到首页推荐了

### 6. 重新生成数据（可选）

如果想扩展用户/交互数据或重新算评估指标：

```powershell
# 造 50+ 用户 × 1500+ 交互（会清空旧交互）
python ../scripts/gen_data.py

# 造 5 个稀疏新用户（交互 ≤ 5 条）
python ../scripts/add_sparse_users.py

# 离线评估：LOO + Precision@5/P@10/NDCG
python ../scripts/eval.py
```

## 📊 评估结果（2026-09-21，30 用户 × 678 交互）

| 场景 | 算法 | P@5 | P@10 | NDCG@5 |
|---|---|---|---|---|
| **活跃用户 (cnt>5, n=25)** | CF 协同过滤 | 0.160 | 0.360 | 0.121 |
| | **CB 内容推荐** | **0.400** | **0.640** | **0.271** |
| | Pop 流行基准 | 0.320 | 0.320 | 0.182 |
| | Hybrid 混合引擎 | 0.280 | 0.360 | 0.196 |
| **稀疏冷启动 (cnt≤5, n=5)** | CF | **0.000** | 0.400 | 0.000 |
| | **CB** | **0.200** | 0.200 | **0.077** |
| | Pop | **0.000** | 0.000 | 0.000 |
| | Hybrid | 0.000 | 0.000 | 0.000 |

> 完整分析见 `../docs/第5章_实验结果与分析.docx`

## 🔐 安全说明

- `/api/**` 全部放行（小程序端用 HTTP，开发期关闭合法域名校验）
- 生产环境需开启 HTTPS + Spring Security Token 过滤器
- `/admin/**` 需 `ROLE_ADMIN` 权限
- BCrypt 加密密码存储

## 📝 论文材料

| 文档 | 路径 |
|---|---|
| 开题报告 | `../docs/开题报告.docx` |
| 第 5 章 实验结果 | `../docs/第5章_实验结果与分析.docx` |
| 答辩 PPT（HTML 版） | `../defense/答辩PPT.html`（浏览器 F11 全屏） |
| 答辩 PPT（PPTX 生成脚本） | `../scripts/gen_ppt.ps1` + `../scripts/生成PPTX.bat` |

## ⚡ 性能优化清单

- ✅ Gzip 压缩（HTML/CSS/JS/JSON）
- ✅ 静态资源 7 天强缓存 + immutable
- ✅ DOM 引用缓存（减少 querySelector）
- ✅ Google Fonts 瘦身 + display=swap 防 FOIT
- ✅ `<img loading="lazy">` 懒加载
- ✅ BCrypt 密码哈希
- ✅ Thymeleaf fragments 复用（14 模板共享 head/player/sidebar）
- ✅ 推荐引擎诊断后台（可观测性）

## 📜 License

MIT — 仅供学习使用
