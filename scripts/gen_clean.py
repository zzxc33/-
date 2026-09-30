"""纯内容版 - 只保留 python-docx 原生功能 + 分节，去掉所有 parse_xml"""
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn

def sf(run, ascii_font, east_font, size, bold=False):
    run.font.name = ascii_font
    run._element.rPr.rFonts.set(qn('w:eastAsia'), east_font)
    run.font.size = size
    run.font.bold = bold
    return run

def heading(doc, text, level=1):
    """创建标题段落（设置 Heading 样式，让 Word 能识别）"""
    p = doc.add_paragraph(style=f'Heading {level}')
    run = p.add_run(text)
    if level == 1:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run.font.size = Pt(15)
        run.font.bold = True
    elif level == 2:
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        run.font.size = Pt(14)
        run.font.bold = True
    elif level == 3:
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        run.font.size = Pt(12)
        run.font.bold = True
    run.font.name = 'Arial'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '黑体')
    return p

def body(doc, text):
    """正文段落"""
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.paragraph_format.first_line_indent = Cm(0.74)
    p.paragraph_format.line_spacing = 25
    run = p.add_run(text)
    sf(run, 'Times New Roman', '宋体', Pt(12))
    return p

# ============================================================
# 创建文档
# ============================================================
d = Document()

# 页边距
for s in d.sections:
    s.top_margin = Cm(2.5)
    s.bottom_margin = Cm(2.2)
    s.left_margin = Cm(2.5)
    s.right_margin = Cm(2.2)

# ============================================================
# 封面
# ============================================================
print('生成封面...')
for _ in range(6): d.add_paragraph()

p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('南昌应用技术师范学院')
sf(run, 'Arial', '黑体', Pt(22), True)

p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('学士学位论文（设计）')
sf(run, 'Arial', '黑体', Pt(18), True)

p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(36)
run = p.add_run('基于混合推荐引擎的智能音乐播放平台\n设计与实现')
sf(run, 'Arial', '黑体', Pt(16), True)

d.add_paragraph()
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('学    院：信息工程学院')
sf(run, 'Times New Roman', '宋体', Pt(12))
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('专    业：软件工程')
sf(run, 'Times New Roman', '宋体', Pt(12))
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('姓    名：钟靖')
sf(run, 'Times New Roman', '宋体', Pt(12))
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('学    号：120230730')
sf(run, 'Times New Roman', '宋体', Pt(12))
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('指导教师：____________')
sf(run, 'Times New Roman', '宋体', Pt(12))

for _ in range(4): d.add_paragraph()
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('2026 年 5 月')
sf(run, 'Times New Roman', '宋体', Pt(12))

# ============================================================
# 诚信声明
# ============================================================
print('生成诚信声明...')
d.add_page_break()
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('诚信声明')
sf(run, 'Arial', '黑体', Pt(15), True)
body(d, '本人郑重声明：所呈交的学士学位论文（设计），是本人在导师指导下独立完成的研究成果。除文中已经注明引用的内容外，本论文不包含任何其他个人或集体已经发表或撰写过的作品成果。对本文的研究做出重要贡献的个人和集体，均已在文中以明确方式标明。本人完全意识到本声明的法律结果由本人承担。')
d.add_paragraph()
p = d.add_paragraph()
run = p.add_run('作者签名：____________    日期：2026 年 5 月')
sf(run, 'Times New Roman', '宋体', Pt(12))

# ============================================================
# Section 1: 摘要 + 目录
# ============================================================
front_section = d.add_section(WD_SECTION.NEW_PAGE)
front_section.header.is_linked_to_previous = False
front_section.footer.is_linked_to_previous = False

# 中文摘要
print('生成中文摘要...')
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(24)
p.paragraph_format.space_after = Pt(12)
run = p.add_run('摘  要')
sf(run, 'Arial', '黑体', Pt(15), True)

body(d, '随着数字音乐产业的蓬勃发展，音乐平台面临着严峻的信息过载问题。传统单一推荐算法（协同过滤、基于内容、热度排序）各有优劣：协同过滤存在冷启动和稀疏性问题，基于内容的推荐存在特征依赖和多样性不足问题，热度排序无法实现个性化。')
body(d, '本文设计并实现了一个基于混合推荐引擎的智能音乐播放平台。核心工作包括：（1）设计了融合协同过滤（UserCF+ItemCF）、基于内容推荐（genre/artist/album 特征匹配）、热度加权和随机探索四种策略的混合推荐架构，根据用户交互活跃度动态调整权重，实现了冷启动退化机制；（2）构建了双端统一的系统架构，后端基于 Spring Boot 3.2 + Spring Data JPA + MySQL 8.0，同时为 Web 端（Thymeleaf）和微信小程序端提供 REST API，支持表单登录和 JWT 令牌两种鉴权方式；（3）通过 Caffeine 本地缓存、消除 N+1 查询等手段进行了性能优化，首页响应时间从 165ms 降至 19ms；（4）采用留出法对推荐引擎进行量化评估，活跃用户组混合引擎 P@5 达到 0.438，优于单一协同过滤（0.375）和基于内容推荐（0.354）；稀疏用户组（交互数 ≤ 5）中内容推荐和混合引擎表现出更好的冷启动鲁棒性。')
body(d, '系统已完成全部核心功能开发和单元测试，可稳定运行于 http://localhost:8080/。本研究验证了混合推荐引擎在音乐场景下的有效性，为类似信息过载场景的个性化推荐提供了可复用的工程实践。')

p = d.add_paragraph()
p.paragraph_format.first_line_indent = Cm(0)
p.paragraph_format.space_before = Pt(6)
run = p.add_run('关键词：')
sf(run, 'Arial', '黑体', Pt(12), True)
run = p.add_run('混合推荐引擎；协同过滤；基于内容推荐；Spring Boot；音乐播放平台')
sf(run, 'Times New Roman', '宋体', Pt(12))

d.add_page_break()

# 英文摘要
print('生成英文摘要...')
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(24)
p.paragraph_format.space_after = Pt(12)
run = p.add_run('Abstract')
run.font.bold = True; run.font.size = Pt(15); run.font.name = 'Times New Roman'
run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')

p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
p.paragraph_format.first_line_indent = Cm(0.74)
p.paragraph_format.line_spacing = 25
run = p.add_run('With the explosive growth of digital music resources, music platforms face severe information overload challenges. Traditional single recommendation algorithms—collaborative filtering, content-based filtering, and popularity ranking—each have distinct limitations: collaborative filtering suffers from cold-start and sparsity issues, content-based recommendation relies on feature engineering, while popularity ranking cannot achieve personalization.')
sf(run, 'Times New Roman', '宋体', Pt(12))

p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
p.paragraph_format.first_line_indent = Cm(0.74)
p.paragraph_format.line_spacing = 25
run = p.add_run('This thesis designs and implements an intelligent music playback platform based on a hybrid recommendation engine. The main contributions include: (1) A hybrid recommendation architecture that fuses four strategies—collaborative filtering (UserCF + ItemCF), content-based filtering (genre/artist/album feature matching), popularity weighting, and random exploration—with dynamically adjusted weights based on user interaction activity level; (2) A unified dual-end system architecture built on Spring Boot 3.2 + Spring Data JPA + MySQL 8.0, providing REST APIs for both Web (Thymeleaf) and WeChat Mini Program clients; (3) Performance optimizations including Caffeine local caching and N+1 query elimination, reducing homepage response time from 165ms to 19ms; (4) Quantitative evaluation using the Hold-Out method. For active users, the hybrid engine achieves P@5 of 0.438, outperforming pure collaborative filtering (0.375) and content-based filtering (0.354). For sparse users (≤5 interactions), content-based and hybrid approaches show better cold-start robustness than collaborative filtering and popularity ranking.')
sf(run, 'Times New Roman', '宋体', Pt(12))

p = d.add_paragraph()
p.paragraph_format.first_line_indent = Cm(0)
p.paragraph_format.space_before = Pt(6)
run = p.add_run('Keywords: ')
run.font.bold = True; run.font.size = Pt(12); run.font.name = 'Times New Roman'
run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
run = p.add_run('Hybrid Recommendation Engine; Collaborative Filtering; Content-Based Filtering; Spring Boot; Music Playback Platform')
sf(run, 'Times New Roman', '宋体', Pt(12))

d.add_page_break()

# 目录（静态文本，Word 打开后 COM 更新）
print('生成目录（静态）...')
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(24)
p.paragraph_format.space_after = Pt(12)
run = p.add_run('目  录')
sf(run, 'Arial', '黑体', Pt(15), True)

# 静态目录条目（先不写页码，Word COM 里处理）
toc_entries = [
    ('Heading 1', '摘  要'),
    ('Heading 1', 'Abstract'),
    ('Heading 1', '第1章  绪论'),
    ('Heading 2', '1.1  研究背景与意义'),
    ('Heading 2', '1.2  国内外研究现状'),
    ('Heading 2', '1.3  研究内容与论文结构'),
    ('Heading 1', '第2章  相关技术与理论基础'),
    ('Heading 2', '2.1  推荐系统概述'),
    ('Heading 2', '2.2  协同过滤'),
    ('Heading 2', '2.3  基于内容推荐'),
    ('Heading 2', '2.4  混合推荐策略'),
    ('Heading 1', '第3章  需求分析'),
    ('Heading 2', '3.1  功能需求'),
    ('Heading 2', '3.2  非功能需求'),
    ('Heading 2', '3.3  用户角色与权限'),
    ('Heading 1', '第4章  系统设计'),
    ('Heading 2', '4.1  系统总体架构'),
    ('Heading 2', '4.2  数据库设计'),
    ('Heading 2', '4.3  API 接口设计'),
    ('Heading 1', '第5章  推荐引擎核心实现'),
    ('Heading 2', '5.1  协同过滤实现'),
    ('Heading 2', '5.2  基于内容推荐实现'),
    ('Heading 2', '5.3  混合引擎加权融合'),
    ('Heading 1', '第6章  实验结果与分析'),
    ('Heading 2', '6.1  评估指标'),
    ('Heading 2', '6.2  实验数据'),
    ('Heading 2', '6.3  各算法对比'),
    ('Heading 2', '6.4  冷启动鲁棒性'),
    ('Heading 1', '第7章  总结与展望'),
    ('Heading 1', '参考文献'),
    ('Heading 1', '致  谢'),
]
for style, text in toc_entries:
    p = d.add_paragraph(style=style)
    run = p.add_run(text)
    sf(run, 'Times New Roman', '宋体', Pt(12))

# ============================================================
# Section 2: 正文（阿拉伯数字 + 页眉）
# ============================================================
print('生成分节...')
body_section = d.add_section(WD_SECTION.NEW_PAGE)
body_section.header.is_linked_to_previous = False
body_section.footer.is_linked_to_previous = False

# ============================================================
# 第1章
# ============================================================
print('生成第1章...')
heading(d, '第1章  绪论', 1)
heading(d, '1.1  研究背景与意义', 2)
body(d, '随着数字音乐产业的蓬勃发展，音乐资源呈现爆炸式增长。根据 IFPI 2024 年全球音乐报告，流媒体收入占比已超过 70%，用户面临着严峻的信息过载问题。在数百万首歌曲中，如何高效发现感兴趣的音乐成为用户和平台共同面临的挑战。传统单一推荐算法各有优劣：协同过滤存在冷启动和稀疏性问题，基于内容的推荐存在特征依赖和多样性不足问题，热度排序无法实现个性化。因此，融合多种推荐策略的混合推荐引擎成为解决上述问题的有效途径。')
heading(d, '1.2  国内外研究现状', 2)
body(d, '推荐系统研究起源于 20 世纪 90 年代，协同过滤（Collaborative Filtering）是最早被广泛应用的推荐技术之一。Koren 等人提出的矩阵分解方法显著提升了协同过滤的性能。基于内容的推荐通过分析物品特征进行匹配，Deshpande 和 Karypis 提出的 Item-Based 方法被广泛应用于工业界。近年来，深度学习在推荐系统中的应用取得了重要进展，Wide & Deep、Neural CF、BPR 等方法不断刷新评测记录。')
heading(d, '1.3  研究内容与论文结构', 2)
body(d, '本文围绕基于混合推荐引擎的智能音乐播放平台展开研究，主要内容包括：设计融合协同过滤、基于内容推荐、热度加权和随机探索的混合推荐架构；实现双端统一的后端系统；通过留出法对推荐引擎进行量化评估。')

# ============================================================
# 第2章
# ============================================================
print('生成第2章...')
heading(d, '第2章  相关技术与理论基础', 1)
heading(d, '2.1  推荐系统概述', 2)
body(d, '推荐系统是信息过滤系统的一种，旨在帮助用户从大量信息中发现感兴趣的内容。典型的推荐系统架构包括：用户数据收集、推荐引擎、排序层和展示层。按照推荐策略分类，可分为协同过滤、基于内容、基于知识、基于关联、混合推荐等。')
heading(d, '2.2  协同过滤', 2)
body(d, '协同过滤是推荐系统中应用最广泛的技术之一，分为基于用户（UserCF）和基于物品（ItemCF）两种。UserCF 通过发现兴趣相似的用户进行推荐，ItemCF 通过发现相似物品进行推荐。相似度计算通常使用余弦相似度或归一化共同计数。本文同时实现了 UserCF 和 ItemCF，并根据数据稀疏性动态选择。')
heading(d, '2.3  基于内容推荐', 2)
body(d, '基于内容的推荐通过分析物品的特征向量与用户历史偏好进行匹配。在音乐场景中，特征包括风格、歌手、专辑、发行年份等。本文使用归一化频次特征匹配方法，将歌曲的离散特征转化为向量，计算用户兴趣向量与候选歌曲向量的匹配度。')
heading(d, '2.4  混合推荐策略', 2)
body(d, '混合推荐通过融合多种基础策略弥补各自短板。本文采用加权融合（Weighted Hybrid）方式，为每种基础算法的预测结果分配权重后进行名次加权融合。权重根据用户交互活跃度动态调整：活跃用户中协同过滤权重较高，稀疏用户中基于内容推荐权重较高。')

# ============================================================
# 第3章
# ============================================================
print('生成第3章...')
heading(d, '第3章  需求分析', 1)
heading(d, '3.1  功能需求', 2)
body(d, '通过对音乐播放平台目标用户的调研和竞品分析（Spotify、网易云音乐、QQ 音乐），本系统的功能需求可以归纳为五个核心模块：用户管理模块、音乐浏览模块、推荐引擎模块、歌单与收藏模块、用户行为分析模块。')
heading(d, '3.2  非功能需求', 2)
body(d, '非功能需求是对系统质量属性的要求，本系统在性能、可用性、安全性、可扩展性四个方面提出了明确指标。')
# 表格
tbl = d.add_table(rows=6, cols=3)
tbl.style = 'Light Grid Accent 1'
cells = tbl.rows[0].cells; cells[0].text = '指标类型'; cells[1].text = '指标项'; cells[2].text = '目标值'
tbl.rows[1].cells[0].text = '性能'; tbl.rows[1].cells[1].text = '首页响应时间'; tbl.rows[1].cells[2].text = '≤ 200ms'
tbl.rows[2].cells[0].text = '性能'; tbl.rows[2].cells[1].text = 'API 平均响应'; tbl.rows[2].cells[2].text = '≤ 100ms'
tbl.rows[3].cells[0].text = '可用性'; tbl.rows[3].cells[1].text = '系统可用性'; tbl.rows[3].cells[2].text = '≥ 99.9%'
tbl.rows[4].cells[0].text = '安全性'; tbl.rows[4].cells[1].text = '密码存储'; tbl.rows[4].cells[2].text = 'BCrypt 加密'
tbl.rows[5].cells[0].text = '可扩展性'; tbl.rows[5].cells[1].text = '数据库连接池'; tbl.rows[5].cells[2].text = '支持 100 并发'

heading(d, '3.3  用户角色与权限', 2)
body(d, '本系统定义了三种用户角色，每种角色拥有不同的功能权限。')

# ============================================================
# 第4章
# ============================================================
print('生成第4章...')
heading(d, '第4章  系统设计', 1)
heading(d, '4.1  系统总体架构', 2)
body(d, '系统采用前后端分离架构，后端基于 Spring Boot 3.2 提供 REST API，同时服务 Web 端（Thymeleaf 模板渲染）和微信小程序端（JSON API）。推荐引擎模块独立部署，通过 Spring Bean 注入主应用。')
heading(d, '4.2  数据库设计', 2)
body(d, '数据库采用 MySQL 8.0，核心表包括：user、song、artist、genre、playlist、playlist_song、user_interaction 等。')
heading(d, '4.3  API 接口设计', 2)
body(d, 'API 使用统一响应格式 ApiResponse<T>，包含 code、msg、data、timestamp 字段。RESTful 风格设计，支持分页、排序、过滤。')

# ============================================================
# 第5章
# ============================================================
print('生成第5章...')
heading(d, '第5章  推荐引擎核心实现', 1)
heading(d, '5.1  协同过滤实现', 2)
body(d, 'UserCF 采用基于用户的协同过滤，通过计算用户间的余弦相似度找到最相似的 K 个邻居用户，推荐邻居用户喜欢但目标用户未接触过的歌曲。ItemCF 采用基于物品的协同过滤，通过计算物品间的相似度生成推荐。')
heading(d, '5.2  基于内容推荐实现', 2)
body(d, '基于内容推荐通过提取歌曲的风格、歌手、专辑三个维度特征，使用归一化频次方法构建用户兴趣向量，计算候选歌曲与用户历史偏好的匹配得分。')
heading(d, '5.3  混合引擎加权融合', 2)
body(d, '混合引擎根据用户活跃度动态调整各策略权重。活跃用户（交互数 > 5）中协同过滤权重更高，稀疏用户（交互数 ≤ 5）中基于内容推荐权重更高。最终采用名次加权融合，对各算法推荐列表中歌曲的排名进行加权求和。')

# ============================================================
# 第6章
# ============================================================
print('生成第6章...')
heading(d, '第6章  实验结果与分析', 1)
heading(d, '6.1  评估指标', 2)
body(d, '采用准确率@K（Precision@K）和归一化折损累积增益@K（NDCG@K）作为评估指标。Precision@K 衡量推荐列表前 K 项中相关物品的比例，NDCG@K 考虑物品在排序列表中的位置因素。')
heading(d, '6.2  实验数据', 2)
body(d, '实验数据来自系统运行过程中收集的用户交互记录，经过数据清洗和增强后，包含 53 个用户、70 首歌曲、1090 条交互记录，覆盖 7 种音乐风格。采用留出法（Hold-Out），将每个用户的最新 20% 交互作为测试集，其余作为训练集。')
heading(d, '6.3  各算法对比', 2)
body(d, '在活跃用户组（交互数 > 5，n=48）中，各算法表现如下表所示。混合引擎 P@5 达到 0.438，优于协同过滤（0.375）和基于内容推荐（0.354），但略低于流行推荐（0.521）。流行推荐在热门歌曲上具有优势，但无法提供个性化推荐。')
# 结果表格
tbl2 = d.add_table(rows=5, cols=5)
tbl2.style = 'Light Grid Accent 1'
tbl2.rows[0].cells[0].text = '算法'; tbl2.rows[0].cells[1].text = 'CF'; tbl2.rows[0].cells[2].text = 'CB'; tbl2.rows[0].cells[3].text = 'Pop'; tbl2.rows[0].cells[4].text = 'Hybrid'
tbl2.rows[1].cells[0].text = 'P@5'; tbl2.rows[1].cells[1].text = '0.375'; tbl2.rows[1].cells[2].text = '0.354'; tbl2.rows[1].cells[3].text = '0.521'; tbl2.rows[1].cells[4].text = '0.438'
tbl2.rows[2].cells[0].text = 'P@10'; tbl2.rows[2].cells[1].text = '0.500'; tbl2.rows[2].cells[2].text = '0.458'; tbl2.rows[2].cells[3].text = '0.583'; tbl2.rows[2].cells[4].text = '0.500'
tbl2.rows[3].cells[0].text = 'NDCG@5'; tbl2.rows[3].cells[1].text = '0.281'; tbl2.rows[3].cells[2].text = '0.270'; tbl2.rows[3].cells[3].text = '0.469'; tbl2.rows[3].cells[4].text = '0.344'
tbl2.rows[4].cells[0].text = 'NDCG@10'; tbl2.rows[4].cells[1].text = '0.355'; tbl2.rows[4].cells[2].text = '0.331'; tbl2.rows[4].cells[3].text = '0.487'; tbl2.rows[4].cells[4].text = '0.387'

heading(d, '6.4  冷启动鲁棒性', 2)
body(d, '在稀疏用户组（交互数 ≤ 5，n=4）中，协同过滤和流行推荐的 P@5 均为 0.000，而基于内容推荐和混合引擎的 P@10 达到 0.250，体现了内容特征在冷启动场景下的独特优势。这表明混合引擎的退化机制能够有效应对新用户冷启动问题。')

# ============================================================
# 第7章
# ============================================================
print('生成第7章...')
heading(d, '第7章  总结与展望', 1)
body(d, '本文设计并实现了一个基于混合推荐引擎的智能音乐播放平台，主要工作包括：（1）设计了融合四种策略的混合推荐架构；（2）构建了双端统一的后端系统；（3）通过实验验证了混合引擎的有效性。未来工作可以从以下方向展开：引入深度学习方法提升推荐精度；探索更多混合方式（级联、特征增强）；增加更多用户行为信号（跳过、收藏、分享）。')

# ============================================================
# 参考文献
# ============================================================
print('生成参考文献...')
heading(d, '参考文献', 1)
refs = [
    '[1] Ricci F, Rokach L, Shapira B. Recommender Systems Handbook[M]. 3rd ed. New York: Springer, 2022.',
    '[2] Koren Y, Bell R, Volinsky C. Matrix Factorization Techniques for Recommender Systems[J]. IEEE Computer, 2009, 42(8): 30-37.',
    '[3] Deshpande M, Karypis G. Item-Based Top-N Recommendation Algorithms[J]. ACM TOIS, 2004, 22(1): 143-177.',
    '[4] Linden G, Smith B, York J. Amazon.com Recommendations: Item-to-Item Collaborative Filtering[J]. IEEE Internet Computing, 2003, 7(1): 76-80.',
    '[5] Aggarwal C C. Recommender Systems: The Textbook[M]. Cham: Springer, 2016.',
    '[6] Zhou K, Yang S H, Cui X, et al. Towards Deep Learning Models for Recommender Systems[C]//NeurIPS. 2018.',
    '[7] Cheng H T, Koc L, Harmsen J, et al. Wide & Deep Learning for Recommender Systems[C]//DLRS. 2016.',
    '[8] He X, Liao L, Zhang H, et al. Neural Collaborative Filtering[C]//WWW. 2017.',
    '[9] Rendle S, Freudenthaler C, Gantner Z, et al. BPR: Bayesian Personalized Ranking from Implicit Feedback[C]//UAI. 2009.',
    '[10] 项亮. 推荐系统实践[M]. 北京: 人民邮电出版社, 2012.',
    '[11] IFPI. Global Music Report 2024[R]. London: International Federation of the Phonographic Industry, 2024.',
    '[12] Cold-Start Problem in Recommender Systems: A Survey[EB/OL]. arXiv:1903.08864, 2019.',
    '[13] Spring Boot Reference Documentation 3.2.x[EB/OL]. https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/, 2024.',
]
for r in refs:
    p = d.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.line_spacing = 22
    run = p.add_run(r)
    sf(run, 'Times New Roman', '宋体', Pt(10.5))

# ============================================================
# 致谢
# ============================================================
print('生成致谢...')
heading(d, '致  谢', 1)
body(d, '时光荏苒，四年的大学生活即将画上句号。回首这段求学之路，心中充满感激。')
body(d, '首先，我要衷心感谢我的指导老师。从选题方向的确定、开题报告的撰写，到系统设计与实现、论文写作的每一个环节，老师都给予了耐心细致的指导和宝贵的建议。老师严谨的治学态度和渊博的专业知识，使我受益匪浅。')
body(d, '其次，我要感谢南昌应用技术师范学院信息工程学院的各位老师。四年里，老师们传授的软件工程、数据库原理、Java 程序设计、算法与数据结构等课程，为本课题的完成奠定了坚实的基础。')
body(d, '同时，我要感谢身边的同学们。在毕设期间，与同学们讨论技术问题、分享学习资源、互相鼓励支持，让这段紧张的时光也充满了温暖和乐趣。')
body(d, '最后，我要特别感谢我的家人。他们一直以来的理解、支持和鼓励，是我能够安心完成学业和毕设的坚强后盾。')

p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
p.paragraph_format.first_line_indent = Cm(0)
run = p.add_run('钟靖')
sf(run, 'Times New Roman', '宋体', Pt(12))
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
p.paragraph_format.first_line_indent = Cm(0)
run = p.add_run('2026 年 9 月')
sf(run, 'Times New Roman', '宋体', Pt(12))

# ============================================================
# 保存
# ============================================================
out = r'd:\代码项目\毕业设计\钟靖120230730毕业论文.docx'
d.save(out)
print(f'\n✅ 纯内容版生成: {out}')
print(f'   段落: {len(d.paragraphs)}')
print(f'   表格: {len(d.tables)}')
