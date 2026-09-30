# -*- coding: utf-8 -*-
"""
论文增强脚本 v3：精准定位正文（第二次出现的章节标题），用补充内容丰富各章节
"""
import docx
from docx.shared import Pt
from docx.oxml.ns import qn

MAIN = r'd:\代码项目\毕业设计\钟靖120230730毕业论文.docx'
SURVEY = r'd:\代码项目\毕业设计\docs\文献综述.docx'
EXPERIMENT = r'd:\代码项目\毕业设计\docs\第5章_实验结果与分析.docx'
OUTPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_增强版.docx'

# ===== 1. 定位正文范围 =====
main_doc = docx.Document(MAIN)

heading1_occurrences = {}
for i, p in enumerate(main_doc.paragraphs):
    if p.style.name == 'Heading 1':
        text = p.text.strip()
        if text not in heading1_occurrences:
            heading1_occurrences[text] = []
        heading1_occurrences[text].append(i)

# 第二次出现的 Heading 1 就是正文
real_start = heading1_occurrences['第1章  绪论'][1]  # 70
real_end = heading1_occurrences['致  谢'][1]         # 132
print(f"正文范围: {real_start} ~ {real_end}")

# ===== 2. 收集 Front Matter（目录标题之前，跳过手动目录条目）=====
# 找到"目  录"标题位置，在它之前截止
toc_title_idx = None
for i, p in enumerate(main_doc.paragraphs):
    if p.text.strip() in ('目  录', '目 录', '目录'):
        toc_title_idx = i
        break

front_matter = []
end_front = toc_title_idx if toc_title_idx else real_start
for i in range(end_front):
    p = main_doc.paragraphs[i]
    front_matter.append((p.text, p.style.name))

# 添加目录标题（用 Normal 样式，Word 会自动生成目录）
front_matter.append(('目  录', 'Normal'))
print(f"Front Matter: 0 ~ {end_front} ({len(front_matter)} 段), 目录标题在 {toc_title_idx}")

# ===== 3. 收集参考文献和致谢正文 =====
after_main = []
for i in range(real_end + 1, len(main_doc.paragraphs)):
    p = main_doc.paragraphs[i]
    after_main.append((p.text, p.style.name))

# ===== 4. 提取文献综述 =====
survey_doc = docx.Document(SURVEY)
survey_paras = [p.text.strip() for p in survey_doc.paragraphs if p.text.strip()]
survey_paras = survey_paras[2:]  # 跳过前两行标题

def extract_section(paragraphs, start_keywords, end_keywords=None):
    """提取某个章节的内容段落"""
    result = []
    capturing = False
    for text in paragraphs:
        if any(kw in text for kw in start_keywords):
            capturing = True
            continue
        if capturing:
            if end_keywords and any(kw in text for kw in end_keywords):
                break
            if text.startswith('参考文献') or text.startswith('['):
                break
            if text:
                result.append(text)
    return result

# 文献综述各章节
svy_concept = extract_section(survey_paras, ['一、推荐系统的概念'])
svy_cf = extract_section(survey_paras, ['二、协同过滤算法'], ['三、'])
svy_cb = extract_section(survey_paras, ['三、基于内容'], ['四、'])
svy_hybrid = extract_section(survey_paras, ['四、混合推荐'], ['五、'])
svy_industry = extract_section(survey_paras, ['五、音乐推荐系统'], ['六、'])
svy_eval = extract_section(survey_paras, ['六、推荐质量'], ['七、'])
svy_gap = extract_section(survey_paras, ['七、现有研究'], ['参考文献'])

print(f"文献综述各节: concept={len(svy_concept)} cf={len(svy_cf)} cb={len(svy_cb)} hybrid={len(svy_hybrid)} industry={len(svy_industry)} eval={len(svy_eval)} gap={len(svy_gap)}")

# ===== 5. 提取实验文档 =====
exp_doc = docx.Document(EXPERIMENT)
exp_paras = [(p.text.strip(), p.style.name) for p in exp_doc.paragraphs if p.text.strip()]
# 去掉第一个标题行
exp_paras = exp_paras[1:]

# ===== 6. 创建新文档 =====
new_doc = docx.Document()

def add_p(doc, text, style='Normal'):
    if not text:
        return
    p = doc.add_paragraph(text, style=style)
    for run in p.runs:
        run.font.name = '宋体'
        run.font.size = Pt(12)
        run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
    return p

# === Front Matter ===
for text, style in front_matter:
    new_doc.add_paragraph(text, style=style)

# === 第1章 绪论 ===
add_p(new_doc, '第1章  绪论', 'Heading 1')
add_p(new_doc, '1.1  研究背景与意义', 'Heading 2')
# 保留原文 1.1 段落
add_p(new_doc, '随着数字音乐产业的蓬勃发展，音乐资源呈现爆炸式增长。根据 IFPI 2024 年全球音乐报告，流媒体收入占比已超过 70%，用户面临着严峻的信息过载问题。在数百万首歌曲中，如何高效发现自己真正喜欢的音乐，已成为音乐平台和用户共同面临的核心挑战。', 'Normal')
add_p(new_doc, '传统单一推荐算法（协同过滤、基于内容、热度排序）各有优劣：协同过滤存在冷启动和稀疏性问题，基于内容的推荐存在特征依赖和同质化问题，热度排序则无法满足个性化需求。本课题设计了融合多种策略的混合推荐引擎，旨在弥补单一算法的短板，提升推荐质量。', 'Normal')

add_p(new_doc, '1.2  国内外研究现状', 'Heading 2')
for text in svy_industry:
    add_p(new_doc, text, 'Normal')
for text in svy_gap:
    add_p(new_doc, text, 'Normal')

add_p(new_doc, '1.3  研究内容与论文结构', 'Heading 2')
add_p(new_doc, '本文围绕基于混合推荐引擎的智能音乐播放平台展开研究，主要内容包括：设计融合协同过滤、基于内容推荐、热度加权和随机探索的混合推荐架构；实现双端统一的后端系统（Web + 微信小程序）；通过离线评估对推荐引擎进行量化验证；构建完整的用户交互闭环（播放、点赞、收藏、跳过）。', 'Normal')
add_p(new_doc, '论文结构如下：第 2 章介绍推荐系统的相关理论基础；第 3 章进行需求分析；第 4 章阐述系统设计；第 5 章详解推荐引擎的核心实现；第 6 章展示实验结果与分析；第 7 章总结全文并展望未来工作。', 'Normal')

# === 第2章 相关技术与理论基础 ===
add_p(new_doc, '第2章  相关技术与理论基础', 'Heading 1')

add_p(new_doc, '2.1  推荐系统概述', 'Heading 2')
add_p(new_doc, '推荐系统是信息过滤系统的一种，旨在帮助用户从大量信息中发现感兴趣的内容。典型的推荐系统架构包括：用户数据收集、推荐引擎、排序层和展示层。', 'Normal')
for text in svy_concept:
    add_p(new_doc, text, 'Normal')

add_p(new_doc, '2.2  协同过滤', 'Heading 2')
add_p(new_doc, '协同过滤是推荐系统中应用最广泛的技术之一，分为基于用户（UserCF）和基于物品（ItemCF）两种。UserCF 通过发现兴趣相似的用户进行推荐，ItemCF 通过发现相似物品进行推荐。', 'Normal')

add_p(new_doc, '2.2.1 相似度度量方法', 'Heading 3')
if svy_cf: add_p(new_doc, svy_cf[0], 'Normal')

add_p(new_doc, '2.2.2 冷启动问题与稀疏性问题', 'Heading 3')
if len(svy_cf) > 1: add_p(new_doc, svy_cf[1], 'Normal')

add_p(new_doc, '2.2.3 隐式反馈数据处理', 'Heading 3')
for text in svy_cf[2:]:
    add_p(new_doc, text, 'Normal')

add_p(new_doc, '2.3  基于内容推荐', 'Heading 2')
add_p(new_doc, '基于内容的推荐通过分析物品的特征向量与用户历史偏好进行匹配。在音乐场景中，特征包括风格、歌手、专辑、发行年份等。', 'Normal')
for text in svy_cb:
    add_p(new_doc, text, 'Normal')

add_p(new_doc, '2.4  混合推荐策略', 'Heading 2')
add_p(new_doc, '混合推荐通过融合多种基础策略弥补各自短板。本文采用加权融合（Weighted Hybrid）方式，为每种基础算法的预测结果分配权重后进行名次加权融合。', 'Normal')
for text in svy_hybrid:
    add_p(new_doc, text, 'Normal')

# === 第3章 需求分析 ===
add_p(new_doc, '第3章  需求分析', 'Heading 1')
add_p(new_doc, '3.1  功能需求', 'Heading 2')
add_p(new_doc, '通过对音乐播放平台目标用户的调研和竞品分析（Spotify、网易云音乐、QQ 音乐），本系统的功能需求可以归纳为五个核心模块：用户管理模块、音乐浏览模块、推荐引擎模块、歌单与收藏模块、播放控制模块。每个模块的具体功能如下。', 'Normal')
add_p(new_doc, '（1）用户管理模块：支持微信小程序 wx.login 一键登录、JWT Token 鉴权、用户信息管理；Web 端支持账号密码登录。', 'Normal')
add_p(new_doc, '（2）音乐浏览模块：按流派分类浏览、歌手专辑展示、热门歌曲排行（Top N）。', 'Normal')
add_p(new_doc, '（3）推荐引擎模块：个性化推荐首页、混合引擎动态权重调整、推荐理由可解释。', 'Normal')
add_p(new_doc, '（4）歌单与收藏模块：用户自建歌单、收藏歌曲、歌单分享。', 'Normal')
add_p(new_doc, '（5）播放控制模块：歌曲播放、暂停、上/下一首、播放列表管理、播放记录自动上报。', 'Normal')

add_p(new_doc, '3.2  非功能需求', 'Heading 2')
add_p(new_doc, '非功能需求是对系统质量属性的要求，本系统在以下四个方面提出明确指标：', 'Normal')
add_p(new_doc, '性能：API 接口响应时间 < 500ms（P95），Caffeine 本地缓存热点歌曲数据（maximumSize=1000, expireAfterWrite=300s）；', 'Normal')
add_p(new_doc, '可用性：双端统一（Web + 微信小程序），RESTful API 统一响应格式 ApiResponse<T>；', 'Normal')
add_p(new_doc, '安全性：JWT 无状态鉴权、Spring Security CSRF 保护（/api/** 免 CSRF 以支持小程序请求）；', 'Normal')
add_p(new_doc, '可扩展性：Service 层接口+实现分离架构，推荐引擎策略可插拔。', 'Normal')

add_p(new_doc, '3.3  用户角色与权限', 'Heading 2')
add_p(new_doc, '本系统定义了三种用户角色，每种角色拥有不同的功能权限：', 'Normal')
add_p(new_doc, 'ROLE_USER（普通用户）：浏览歌曲、播放、点赞、收藏、自建歌单、获取个性化推荐；', 'Normal')
add_p(new_doc, 'ROLE_ADMIN（管理员）：在普通用户权限基础上，可管理歌曲库（增删改）、管理用户、查看推荐引擎后台数据；', 'Normal')
add_p(new_doc, '未登录用户：仅可浏览歌曲列表和热门排行，不可获取个性化推荐，播放功能受限。', 'Normal')

# === 第4章 系统设计 ===
add_p(new_doc, '第4章  系统设计', 'Heading 1')

add_p(new_doc, '4.1  系统总体架构', 'Heading 2')
add_p(new_doc, '系统采用前后端分离架构，后端基于 Spring Boot 3.2 提供 REST API，同时服务 Web 端（Thymeleaf 模板渲染）和微信小程序端（JSON API）。推荐引擎与业务解耦，通过独立接口与 Service 层交互。', 'Normal')
add_p(new_doc, '后端核心组件包括：', 'Normal')
add_p(new_doc, '（1）Controller 层：AuthController（认证）、SongController（歌曲）、RecommendController（推荐）、PlaylistController（歌单）、UserController（用户）；', 'Normal')
add_p(new_doc, '（2）Service 层：接口（SongService, RecommendService）+ 实现（SongServiceImpl, HybridRecommenderServiceImpl）分离；', 'Normal')
add_p(new_doc, '（3）Repository 层：Spring Data JPA 数据访问接口；', 'Normal')
add_p(new_doc, '（4）安全层：Spring Security + JwtAuthFilter + JwtService；', 'Normal')
add_p(new_doc, '（5）缓存层：Caffeine 本地缓存用于热门歌曲和推荐结果；', 'Normal')
add_p(new_doc, '（6）数据库：MySQL 8.0 + Druid 1.2.24 连接池。', 'Normal')

add_p(new_doc, '4.2  数据库设计', 'Heading 2')
add_p(new_doc, '数据库设计遵循第三范式，核心表结构如下：', 'Normal')
add_p(new_doc, '用户表（user）：id, username, password, nickname, role(ROLE_USER/ROLE_ADMIN), wx_openid, created_at', 'Normal')
add_p(new_doc, '歌曲表（song）：id, title, artist_id, album_id, genre, cover_url, duration, play_count, like_count', 'Normal')
add_p(new_doc, '歌手表（artist）：id, name, bio, avatar_url', 'Normal')
add_p(new_doc, '专辑表（album）：id, title, artist_id, cover_url, release_year', 'Normal')
add_p(new_doc, '歌单表（playlist）：id, user_id, name, description, cover_url, created_at', 'Normal')
add_p(new_doc, '歌单歌曲表（playlist_song）：playlist_id, song_id（UNIQUE KEY 防重复）, added_at', 'Normal')
add_p(new_doc, '用户交互表（user_interaction）：user_id, song_id, play_count, liked(boolean), last_played_at（隐式评分来源）', 'Normal')

add_p(new_doc, '4.3  API 接口设计', 'Heading 2')
add_p(new_doc, 'API 采用统一响应格式 ApiResponse<T>：', 'Normal')
add_p(new_doc, '{ code: 0, msg: "ok", data: {...}, timestamp: 1738000000000 }', 'Normal')
add_p(new_doc, '核心 API 端点如下：', 'Normal')
add_p(new_doc, 'POST /api/auth/login  → 用户登录，返回 JWT Token', 'Normal')
add_p(new_doc, 'POST /api/auth/wechat-login → 小程序 wx.login 登录', 'Normal')
add_p(new_doc, 'GET  /api/songs → 歌曲列表（分页/排序/按流派过滤）', 'Normal')
add_p(new_doc, 'GET  /api/songs/hot → 热门歌曲 Top N', 'Normal')
add_p(new_doc, 'GET  /api/songs/search?keyword= → 歌曲搜索', 'Normal')
add_p(new_doc, 'GET  /api/songs/{id} → 歌曲详情', 'Normal')
add_p(new_doc, 'POST /api/songs/{id}/play → 记录播放行为', 'Normal')
add_p(new_doc, 'POST /api/songs/{id}/like → 记录点赞行为', 'Normal')
add_p(new_doc, 'GET  /api/recommend → 个性化推荐（混合引擎）', 'Normal')
add_p(new_doc, 'GET  /api/playlists → 歌单列表', 'Normal')
add_p(new_doc, 'GET  /api/user/me → 当前用户信息', 'Normal')

# === 第5章 推荐引擎核心实现 ===
add_p(new_doc, '第5章  推荐引擎核心实现', 'Heading 1')

add_p(new_doc, '5.1  协同过滤实现', 'Heading 2')
add_p(new_doc, 'UserCF 采用基于用户的协同过滤，通过计算用户间的皮尔逊相关系数找到最相似的 K 个邻居用户（K=5），推荐邻居用户喜欢但目标用户未接触过的歌曲。ItemCF 采用基于物品的协同过滤，通过计算歌曲间的余弦相似度构建相似物品矩阵。', 'Normal')
add_p(new_doc, '隐式评分转换策略：将 user_interaction 表中的交互记录转换为用户-歌曲二部图矩阵，点赞记 2 分、播放记 1 分（播放次数超过 10 次额外加分），跳过或未交互记 0 分。这种设计让协同过滤可以直接在无评分数据的音乐场景中工作。', 'Normal')
add_p(new_doc, '稀疏性优化：当活跃用户不足时，Jaccard 相似度作为皮尔逊系数的后备方案——它只关注"是否交互"这一二值信息，不依赖共同交互的数量，在冷启动阶段表现更稳定。', 'Normal')

add_p(new_doc, '5.2  基于内容推荐实现', 'Heading 2')
add_p(new_doc, '基于内容推荐通过提取歌曲的风格、歌手、专辑三个维度特征，使用归一化频次方法构建用户兴趣向量。具体实现步骤：', 'Normal')
add_p(new_doc, '（1）对每首歌曲，将 genre、artist_id、album_id 三个离散特征编码为 one-hot 向量；', 'Normal')
add_p(new_doc, '（2）对每个用户，将其播放/点赞过的所有歌曲的特征向量累加，得到用户兴趣画像；', 'Normal')
add_p(new_doc, '（3）计算候选歌曲特征向量与用户兴趣向量的余弦相似度，按得分排序推荐 Top N。', 'Normal')
add_p(new_doc, '内容推荐的核心优势是**不存在新物品冷启动**——只要歌曲具有结构化特征（genre/artist），就能立即被推荐给有对应偏好的用户。这一特性在第 6 章的冷启动鲁棒性实验中得到了验证。', 'Normal')

add_p(new_doc, '5.3  混合引擎加权融合', 'Heading 2')
add_p(new_doc, '混合引擎的核心创新点在于**动态权重策略**——根据用户交互活跃度自动调整各推荐策略的权重分配：', 'Normal')
add_p(new_doc, '（1）无行为数据（完全新用户）：Pop 流行推荐 85% + Random 随机 15%，避免全站同款推荐；', 'Normal')
add_p(new_doc, '（2）稀疏阶段（交互 1-5 条）：CB 内容推荐 50% + Pop 25% + ItemCF 20% + Random 5%，内容特征在少量数据下即可提取兴趣画像；', 'Normal')
add_p(new_doc, '（3）活跃阶段（交互 > 5 条）：UserCF 50% + ItemCF 20% + CB 20% + Random 10%，协同过滤相似度矩阵趋于稳定，成为主力推荐信号。', 'Normal')
add_p(new_doc, '融合方式采用**名次加权融合**（Rank-Based Weighted Fusion）：对每个算法的推荐列表，按排名赋予得分（排名越靠前得分越高，按 1/log₂(k+1) 衰减），加权求和后重新排序，取 Top N 作为最终推荐列表。', 'Normal')
add_p(new_doc, 'Random 随机探索在所有阶段保留 5%-15% 的权重，目的是让新歌曲和长尾歌曲获得曝光机会，避免推荐结果完全收敛导致多样性丧失。', 'Normal')

# === 第6章 实验结果与分析 ===
add_p(new_doc, '第6章  实验结果与分析', 'Heading 1')

# 插入实验文档内容（编号改为6.x）
for text, style in exp_paras:
    new_text = text
    for old, new in [('5.1.1', '6.1.1'), ('5.1.2', '6.1.2'), ('5.1.3', '6.1.3'),
                     ('5.2.1', '6.2.1'), ('5.2.2', '6.2.2'), ('5.3 ', '6.3 '),
                     ('5.4 ', '6.4 '), ('5.1 ', '6.1 '), ('5.2 ', '6.2 ')]:
        new_text = new_text.replace(old, new)
    
    if 'Heading' in style:
        # 判断级别
        if any(new_text.startswith(p) for p in ['6.1.1', '6.1.2', '6.1.3', '6.2.1', '6.2.2']):
            add_p(new_doc, new_text, 'Heading 3')
        elif new_text.startswith('6.') and ('实验' in new_text or '算法' in new_text or '权重' in new_text or '小结' in new_text):
            add_p(new_doc, new_text, 'Heading 2')
        else:
            add_p(new_doc, new_text, 'Heading 2')
    else:
        add_p(new_doc, new_text, 'Normal')

# 补充评估方法
add_p(new_doc, '6.1.4 评估流程与数据划分', 'Heading 3')
for text in svy_eval:
    add_p(new_doc, text, 'Normal')

# === 第7章 总结与展望 ===
add_p(new_doc, '第7章  总结与展望', 'Heading 1')
add_p(new_doc, '本文设计并实现了一个基于混合推荐引擎的智能音乐播放平台，主要工作包括：', 'Normal')
add_p(new_doc, '（1）算法层面：实现了协同过滤（UserCF + ItemCF 双模式、皮尔逊 + Jaccard 双相似度融合）、基于内容推荐（genre/artist/album 三维特征 + 余弦相似度）和混合推荐（四路加权、动态权重调整）三种方法，并通过离线 Leave-One-Out 交叉验证对其进行量化评估；', 'Normal')
add_p(new_doc, '（2）工程层面：构建了双端统一的后端系统（Spring Boot 3.2 + MySQL 8.0 + Druid + Caffeine），同时服务 Web 端（Thymeleaf 模板渲染）和微信小程序端（JSON API + JWT 鉴权 + wx.login）；', 'Normal')
add_p(new_doc, '（3）实验层面：通过 30 用户 × 678 交互的小规模数据集，验证了混合引擎在冷启动场景下的鲁棒性（CF P@5=0.000 vs CB P@5=0.200），以及动态权重策略相比固定权重在数据两端的优势。', 'Normal')
add_p(new_doc, '未来工作可从以下方向展开：（1）扩展数据集规模——当前仅 70 首歌、30 用户，大规模数据集下 CF 相似度矩阵将更稳定，预计混合引擎（CF 权重 50%）的优势会更明显；（2）引入深度学习模型——NCF、Wide & Deep 等端到端模型可自动学习非线性特征交互；（3）增加可解释性——为每条推荐生成自然语言理由，提升用户信任度；（4）优化工程——引入 Redis 分布式缓存替代 Caffeine 以支持多实例部署。', 'Normal')

# === 参考文献 ===
for text, style in after_main:
    new_doc.add_paragraph(text, style=style)

# ===== 7. 保存 =====
new_doc.save(OUTPUT)

# ===== 8. 验证 =====
v = docx.Document(OUTPUT)
tc = sum(len(p.text.strip()) for p in v.paragraphs)
tw = sum(len(p.text.split()) for p in v.paragraphs)
cn = tc - tw

print("\n" + "="*60)
print(f"✅ 增强版已生成!")
print(f"   输出: {OUTPUT}")
print(f"   总段落: {len(v.paragraphs)}")
print(f"   中文字数: {cn} 字 {'✅ 达标 (≥10000)' if cn >= 10000 else '❌ 不足'}")
print(f"   表格数: {len(v.tables)}")
print("\n=== 结构预览 ===")
for p in v.paragraphs:
    if p.style.name.startswith('Heading'):
        indent = '  ' * (int(p.style.name[-1]) - 1) if '3' in p.style.name else ''
        print(f"  {indent}[{p.style.name}] {p.text.strip()}")
