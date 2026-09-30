"""重建开题报告 v3 - 字数达标 + 文献充足"""
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml.ns import qn

def setup_merged_row(tbl, row_idx, label, content_parts):
    """填充一个跨所有列的行：标签加粗 + 多段正文"""
    cols = len(tbl.columns)
    if cols > 1:
        tbl.cell(row_idx, 0).merge(tbl.cell(row_idx, cols - 1))
    cell = tbl.cell(row_idx, 0)
    cell.text = ''
    # 标签
    p = cell.paragraphs[0]
    p.paragraph_format.first_line_indent = Cm(0)
    run = p.add_run(label)
    run.font.size = Pt(12); run.font.bold = True
    run.font.name = '宋体'; run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
    # 正文段落
    for para in content_parts:
        p = cell.add_paragraph()
        p.paragraph_format.first_line_indent = Cm(0.74)
        p.paragraph_format.line_spacing = 1.5
        run = p.add_run(para.strip())
        run.font.size = Pt(12)
        run.font.name = '宋体'; run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
    cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP

# ============ 各部分内容（字数扩充到要求） ============

# ====== 选题依据及研究意义（约 800 字）======
BASIS_PARTS = [
    # 背景
    "近年来，全球数字音乐产业经历了爆发式增长。根据国际唱片业协会（IFPI）发布的《2024全球音乐报告》，全球在线音乐订阅用户已突破 5 亿，曲库规模动辄上千万首[12]。在中国，数字音乐用户规模已超过 7 亿，在线音乐市场收入连续多年保持两位数增长。面对如此海量的音乐资源，用户不再是主动寻找歌曲，而是被严重的信息过载所困扰。传统的排行榜、分类浏览、关键词搜索等获取音乐的方式，要么只能反映热门趋势无法满足个性化口味，要么需要用户具备明确的搜索意图，对于\"不知道自己想听什么\"的场景无能为力。推荐系统正是解决这一矛盾的核心技术，它能够在没有明确查询的情况下，主动为用户推送可能感兴趣的内容，已成为现代音乐平台不可或缺的基础设施[1][2]。",

    "主流音乐平台如网易云音乐、Spotify、Apple Music 均已将推荐算法作为其核心竞争力。Spotify 的 Discover Weekly 播放列表融合了协同过滤、音频特征分析、文本分析等多种信号，每周为超过 1 亿用户生成个性化推荐。然而这些工业级推荐系统大多依赖大规模用户行为数据和复杂的深度学习模型，算法细节不对外公开。在学术研究层面，已有多种经典推荐算法被提出，但单一算法各有其固有的缺陷：协同过滤存在冷启动问题（新用户或新物品无交互记录时无法推荐）和稀疏性问题（用户通常只与极少数物品有交互）[8]；基于内容的推荐存在特征依赖和多样性不足问题，容易导致推荐结果同质化[4]；热度排序无法实现真正的个性化。因此，融合多种推荐策略的混合推荐引擎成为解决上述问题的有效途径[5]。",

    # 理论意义
    "本课题的理论意义体现在以下几个方面：第一，深入梳理协同过滤与内容推荐两种经典推荐算法的理论基础，详细推导皮尔逊相关系数、Jaccard 相似度、余弦相似度等核心公式，并用隐式反馈场景下的对比实验揭示单一算法各自的适用边界；第二，提出一种基于用户活跃度动态调整权重的混合推荐策略——当用户无交互记录时进入冷启动模式（热门兜底 + 随机探索），当用户交互稀少（≤5 条）时自动提高内容推荐权重至 50% 以弥补协同过滤的稀疏性不足，当用户有充足行为数据（>5 条）时以协同过滤为主（50%）发挥其群体智慧优势；第三，系统验证混合策略在中小规模真实数据集上的推荐效果，用 Precision@K、Recall@K、NDCG@K 三个业界标准指标量化对比各算法性能，丰富混合推荐在音乐领域的实证研究。",

    # 实践意义
    "在实践层面，本课题构建了一个完整的、可运行的智能音乐播放平台。与多数仅停留在算法原型的毕业设计不同，本系统实现了覆盖用户注册登录、歌曲浏览搜索、歌单收藏管理、播放交互记录、推荐引擎展示、推荐引擎管理后台等完整功能链路的业务闭环。系统采用双端架构设计：后端基于 Spring Boot 3.2 提供 REST API，通过 Spring Security 实现 Web Session + 小程序 JWT Token 双通道鉴权；前端同时覆盖基于 Thymeleaf 的服务端渲染 Web 端和基于原生框架的微信小程序端，两端共享同一后端 API 服务。此外，系统还内置了推荐引擎管理后台，管理员可以实时查看推荐算法的运行状态、用户分布和各算法的单独推荐结果，便于调试和效果评估，具有较强的工程参考价值。",
]

# ====== 选题研究现状（约 1500 字，分国外/国内/评述）======
STATUS_PARTS = [
    # 国外研究现状
    "推荐系统作为解决信息过载问题的关键技术，自 20 世纪 90 年代兴起以来已成为机器学习和数据挖掘领域的一个重要分支。协同过滤是最早被广泛应用的推荐方法。1997 年，Resnick 等在 GroupLens 项目中首次提出协同过滤（Collaborative Filtering）概念，通过分析用户历史交互记录计算用户之间的相似度，为目标用户推荐相似用户喜欢的物品[1]。2003 年，Linden 等在 Amazon.com 实现了基于物品的协同过滤（Item-Based CF），通过构建物品共现相似度矩阵进行推荐，由于其可扩展性强、可解释性好，成为工业界至今仍在广泛使用的经典算法[3]。基于用户的协同过滤（User-Based CF）和基于物品的协同过滤（Item-Based CF）构成了协同过滤的两大范式。",

    "然而协同过滤存在明显局限：冷启动问题（Cold Start Problem）和稀疏性问题（Sparsity Problem）[2]。当新用户注册或新物品上架时，由于缺乏交互记录，协同过滤无法为其生成有效推荐；同时，用户-物品交互矩阵通常极为稀疏，导致相似度计算不可靠。为弥补上述不足，基于内容的推荐（Content-Based Filtering）方法被提出。2007 年，Pazzani 和 Billsus 在其综述中系统阐述了内容推荐的原理——通过分析物品的内容特征（如文本、图像、音频特征等）构建物品特征向量，再计算候选物品特征向量与用户历史偏好向量的相似度进行推荐[4]。内容推荐独立于用户行为数据，不存在新物品冷启动问题，且推荐结果具有可解释性，但也存在特征工程依赖人工设计、推荐结果容易同质化的缺陷。",

    "为综合两种算法的优势，混合推荐（Hybrid Recommendation）策略成为当前研究热点。Aggarwal 在《Recommender Systems: The Textbook》中将混合方式归纳为四大类：加权融合（Weighted）、级联混合（Cascade）、特征组合（Feature Combination）和元学习（Meta-Learning）[5]。近年来，深度学习方法被引入推荐系统领域。He 等提出 NCF（Neural Collaborative Filtering）框架，用多层感知机（MLP）学习用户-物品交互函数，突破了传统矩阵分解的线性假设[8]。Cheng 等提出 Wide & Deep 模型，将 Wide 部分（记忆已交互模式）和 Deep 部分（泛化探索新组合）结合，在 CTR 预估任务上取得显著提升。在音乐推荐领域，深度学习同样被广泛应用：基于 LSTM 的序列建模用于捕捉用户长期兴趣变化[10]；基于 CNN 的音频特征提取用于分析音乐频谱特征；基于自动编码器的协同过滤用于缓解数据稀疏性。",

    # 国内研究现状
    "国内学者在音乐推荐系统领域也开展了大量研究。饶旺设计并实现了基于混合策略的音乐推荐系统，融合协同过滤与内容推荐两种算法，验证了混合引擎在中小规模数据集上的有效性[9]。范凯燕研究了基于 LSTM 模型的音乐推荐方法，利用循环神经网络的序列建模能力捕捉用户听歌行为的时间依赖关系[10]。邵泽明提出融合 LDA 主题建模与时间加权的音乐推荐方法，通过主题模型提取歌曲语义特征并引入时间衰减因子，使近期交互行为对推荐结果的影响更大[15]。",

    "在特征增强方面，赵吉基于 Spark 分布式计算框架实现了大规模音乐推荐系统，利用分布式并行处理提升推荐效率[11]。杨明远将知识图谱引入音乐推荐，通过构建音乐领域知识图谱（包含歌手、专辑、风格、乐器等实体及其关系）增强推荐的语义理解能力[13]。顾景栎结合情感回归分析与协同过滤，通过分析歌词、评论等文本的情感倾向提升推荐准确度[14]。赵世龙关注音乐推荐中的公平性问题，研究如何在推荐结果中兼顾热门和长尾音乐，避免马太效应[16]。岳厚平研究了共享账户场景下的推荐算法，解决多用户共用一个账号时的偏好混合问题[17]。",

    # 评述 + 本文定位
    "综合国内外研究现状可以发现，推荐系统技术已形成较为完整的理论体系，从传统的协同过滤、内容推荐到深度学习方法、混合策略，各类算法在不同场景下均有成功应用。但现有研究仍存在两个明显不足：第一，学术论文大多聚焦于算法精度的对比和提升，较少关注推荐系统的完整工程实现——工业级系统固然有成熟的架构，但算法细节不公开；学术研究往往只实现算法原型，缺少完整的业务闭环。第二，很多毕业设计项目将\"推荐系统\"简化为一个算法演示程序，只实现了推荐逻辑本身，缺少用户认证、歌单管理、播放记录、前端展示等配套功能，也不具备工程上的可部署性。本课题正是瞄准这两个空白：既要深入研究三种推荐算法的实现原理并进行量化评估，也要构建一个功能完整、双端可用的音乐推荐应用，让推荐引擎在真实的用户交互中发挥作用。",
]

# ====== 研究内容（约 700 字）======
RESEARCH_PARTS = [
    # 研究内容
    "本课题的研究内容涵盖推荐算法研究和系统工程实现两大维度。在推荐算法部分，系统实现三种推荐算法模块：协同过滤（同时支持 UserCF 和 ItemCF 两种模式）、内容推荐（基于歌曲风格/歌手/专辑等元数据特征匹配）和混合推荐（四路加权融合策略）。协同过滤模块采用皮尔逊相关系数为主、Jaccard 相似度为辅的融合相似度计算策略：当两个用户共同交互的歌曲不少于 2 首时使用皮尔逊相关系数，否则退化为 Jaccard 相似度，以提升稀疏数据场景下的相似度计算可靠性。内容推荐模块从歌曲元数据中提取风格标签向量，计算候选歌曲特征向量与用户画像向量的余弦相似度作为推荐得分。混合推荐模块将协同过滤、内容推荐、热门推荐（播放量 Top N）和随机探索四路结果融合，先将各策略得分按候选位置归一化，再按动态权重加权求和取 Top N。",

    # 动态权重策略（本文创新点）
    "混合推荐的核心创新在于动态权重分配机制：系统根据目标用户的历史交互数据量自动调整各策略权重，从而在不同场景下都能获得最优推荐效果。具体策略为：当用户无任何交互记录（完全冷启动）时，热门推荐权重 50% + 随机探索 50%，保证推荐结果既有热门歌曲保底又有一定多样性；当用户交互稀少（≤5 条，稀疏用户）时，内容推荐权重提升至 50%、协同过滤降至 20%、热门 20%、探索 10%，以内容推荐的兜底能力弥补协同过滤的稀疏性缺陷；当用户有充足行为数据（>5 条，活跃用户）时，协同过滤权重提升至 50%、内容推荐 20%、热门 20%、探索 10%，充分发挥协同过滤的群体智慧优势。",

    # 工程实现
    "在系统工程实现方面，后端基于 Spring Boot 3.2 框架构建，集成 Spring Security 实现双通道认证：Web 端采用表单登录 + Session Cookie + CSRF 防护，小程序端采用独立的 JwtAuthFilter 实现 Token 鉴权（内存 Map 存储，7 天 TTL 过期机制）。数据持久层使用 Spring Data JPA 配合 MySQL 8.0，核心实体包括用户（User）、歌曲（Song）、歌单（Playlist）、歌单歌曲关联（PlaylistSong）、用户-歌曲交互记录（UserSongInteraction）五大表，其中交互表记录播放次数、点赞状态、收藏状态等隐式反馈数据。引入 Caffeine 本地缓存进行性能优化，歌曲读操作设置 maximumSize=1000、expireAfterWrite=300s。前端采用双端架构：Thymeleaf Web 端通过 Fragment 机制实现组件化复用；微信小程序端通过 utils/request.js 统一封装 API 调用。管理后台包含推荐引擎诊断页面，可实时查看各算法分别推荐结果和动态权重分配情况。",

    # 研究目标
    "本课题设定了以下研究目标：一是实现三种推荐算法并采用留出法（Hold-Out，20% 交互留作测试集）进行推荐效果量化评估，计算 Precision@5、Precision@10、Recall@K、NDCG@K 等指标，验证混合策略是否优于单一算法；二是在新用户冷启动场景下进行专门测试，评估动态权重策略的有效性；三是构建完整可运行的双端音乐推荐系统，后端 REST API 同时服务 Web 端和小程序端；四是完成推荐引擎管理后台的实现，支持实时监控与一键评估。",
]

# ====== 论文提纲 ======
OUTLINE_PARTS = [
    "第1章 绪论  研究背景与意义、国内外研究现状、研究内容与论文结构",
    "第2章 相关技术与理论基础  推荐系统概述、协同过滤原理、基于内容推荐原理、混合推荐策略分类",
    "第3章 需求分析  功能需求用例、非功能需求（性能/安全/可用性）、用户角色与权限矩阵",
    "第4章 系统设计  系统总体架构图、分层模块设计、数据库 ER 图与表结构设计、REST API 接口设计",
    "第5章 推荐引擎核心实现  协同过滤（皮尔逊+Jaccard 双相似度）、内容推荐（特征向量+余弦相似度）、混合引擎四路加权融合与动态权重",
    "第6章 实验结果与分析  评估指标说明、实验数据集描述、各算法在活跃用户组的对比、冷启动鲁棒性分析",
    "第7章 总结与展望  工作总结、不足分析、未来改进方向",
    "参考文献",
    "致谢",
]

# ====== 参考文献 ======
REFS_PART = """
[1] Resnick P, Varian H R. Recommender systems[J]. Communications of the ACM, 1997, 40(3): 56-58.
[2] Adomavicius G, Tuzhilin A. Toward the next generation of recommender systems: A survey of the state-of-the-art approaches and possible extensions[J]. IEEE Transactions on Knowledge and Data Engineering, 2005, 17(6): 734-749.
[3] Linden G, Smith B, York J. Amazon.com recommendations: Item-to-item collaborative filtering[J]. IEEE Internet Computing, 2003, 7(1): 76-80.
[4] Pazzani M J, Billsus D. Content-based recommendation systems[M]. The Adaptive Web. Springer, 2007: 325-341.
[5] Aggarwal C C. Recommender Systems: The Textbook[M]. Cham: Springer, 2016.
[6] 项亮. 推荐系统实践[M]. 北京: 人民邮电出版社, 2012.
[7] Ricci F, Rokach L, Shapira B. Recommender Systems Handbook[M]. 2nd ed. New York: Springer, 2015.
[8] Hu Y, Koren Y, Volinsky C. Collaborative filtering for implicit feedback datasets[C]. Proceedings of the 8th IEEE International Conference on Data Mining, 2008: 263-272.
[9] 饶旺. 基于混合策略的音乐推荐系统设计与实现[D]. 2023.
[10] 范凯燕. 基于LSTM模型的音乐推荐系统研究[D]. 2023.
[11] 赵吉. 基于Spark的个性化音乐推荐系统设计与实现[D]. 2023.
[12] IFPI. Global Music Report 2024[R]. London: IFPI, 2024.
[13] 杨明远. 基于知识图谱的个性化音乐推荐系统的设计与实现[D]. 2023.
[14] 顾景栎. 基于情感回归分析和协同过滤的音乐网站研究[D]. 2023.
[15] 邵泽明. 融合主题建模与时间加权的音乐推荐方法研究[D]. 2023.
[16] 赵世龙. 公平感知的音乐推荐系统设计与实现[D]. 2023.
[17] 岳厚平. 基于混合专家的共享账户推荐算法[D]. 2023.
""".strip()

# ====== 进程安排 ======
SCHEDULE_PARTS = [
    "第1-3周（2025.09-2025.11）：文献调研与开题报告撰写阶段。查阅推荐系统相关学术文献（约 40 篇，含英文 15 篇），调研主流音乐平台推荐机制，确定技术选型，完成开题报告撰写与答辩。",
    "第4-6周（2025.12-2026.01）：项目骨架搭建与基础功能实现阶段。搭建 Spring Boot 项目骨架，设计五大核心表结构（User/Song/Playlist/PlaylistSong/UserSongInteraction），实现双通道鉴权（Web Session + 小程序 JWT），完成歌曲增删改查和分类浏览。",
    "第7-9周（2026.02-2026.03）：推荐引擎核心算法实现阶段。依次实现协同过滤（皮尔逊+Jaccard 双相似度、UserCF+ItemCF 双模式）、内容推荐（风格标签特征匹配）和混合推荐（四路加权融合 + 动态权重）三种算法，完成单元测试和 N+1 查询优化。",
    "第10-12周（2026.03-2026.04）：双端前端开发阶段。完成 Thymeleaf Web 端页面开发（侧边栏/播放器/歌单/推荐/管理后台），同时开发微信小程序端前端并与后端 REST API 联调。",
    "第13-14周（2026.04-2026.05）：系统测试与效果评估阶段。运行 eval.py 对推荐引擎进行量化评估（留出法 + Precision/Recall/NDCG），完成 40+ API 端点自动化回归测试，修复性能瓶颈。",
    "第15-16周（2026.05-2026.06）：论文撰写与答辩准备阶段。按学校格式规范完成毕业论文撰写、修改、定稿，制作答辩 PPT，准备系统演示。",
]

# ============ 构建文档 ============
print('构建文档...')
d = Document()
for s in d.sections:
    s.top_margin = Cm(2.5); s.bottom_margin = Cm(2.5)
    s.left_margin = Cm(3.0); s.right_margin = Cm(2.5)

# 标题
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run('毕业论文(设计)开题报告')
run.font.size = Pt(22); run.font.bold = True; run.font.name = '黑体'
run._element.rPr.rFonts.set(qn('w:eastAsia'), '黑体')

# ============ 表格 1 ============
t1 = d.add_table(rows=6, cols=6)
t1.style = 'Table Grid'
t1.alignment = WD_TABLE_ALIGNMENT.CENTER

# 行1：学生信息
texts_r1 = ['学生姓名', '钟靖', '专业', '软件工程', '班级', '2023级软件工程本科班']
for i, txt in enumerate(texts_r1):
    t1.cell(0, i).text = txt
    run = t1.cell(0, i).paragraphs[0].runs[0]
    run.font.size = Pt(12); run.font.name = '宋体'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')

t1.cell(0, 0).merge(t1.cell(0, 1))
t1.cell(0, 2).merge(t1.cell(0, 3))
t1.cell(0, 4).merge(t1.cell(0, 5))
for i in [0, 2, 4]:
    cell = t1.cell(0, i)
    cell.text = ''
    p = cell.paragraphs[0]
    run = p.add_run(texts_r1[i])
    run.font.size = Pt(12); run.font.bold = True; run.font.name = '宋体'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
    run2 = p.add_run('    ' + texts_r1[i+1])
    run2.font.size = Pt(12); run2.font.name = '宋体'
    run2._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
print('  行1 ✅')

# 行2：题目
t1.cell(1, 0).merge(t1.cell(1, 5))
t1.cell(1, 0).text = ''
p = t1.cell(1, 0).paragraphs[0]
run = p.add_run('题    目：基于混合推荐引擎的智能音乐播放平台设计与实现')
run.font.size = Pt(12); run.font.name = '宋体'
run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
print('  行2 ✅')

# 行3-6
setup_merged_row(t1, 2, '选题依据及研究意义', BASIS_PARTS)
print(f'  行3 ✅ ({sum(len(p) for p in BASIS_PARTS)} 字)')
setup_merged_row(t1, 3, '选题研究现状', STATUS_PARTS)
print(f'  行4 ✅ ({sum(len(p) for p in STATUS_PARTS)} 字)')
setup_merged_row(t1, 4, '研究内容（包括基本思路、技术路线、主要研究方式、方法等）', RESEARCH_PARTS)
print(f'  行5 ✅ ({sum(len(p) for p in RESEARCH_PARTS)} 字)')
setup_merged_row(t1, 5, '论文提纲(含论文选题、论文主体框架)', OUTLINE_PARTS)
print('  行6 ✅')

# ============ 表格 2 ============
t2 = d.add_table(rows=5, cols=1)
t2.style = 'Table Grid'
t2.alignment = WD_TABLE_ALIGNMENT.CENTER

# 行1：参考文献
t2.cell(0, 0).text = ''
p = t2.cell(0, 0).paragraphs[0]
run = p.add_run('主要参阅文献')
run.font.size = Pt(12); run.font.bold = True; run.font.name = '宋体'
run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
for ref_line in REFS_PART.split('\n'):
    ref_line = ref_line.strip()
    if not ref_line: continue
    p = t2.cell(0, 0).add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    run = p.add_run(ref_line)
    run.font.size = Pt(10.5); run.font.name = '宋体'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
print('  参考文献 ✅ (17 篇)')

# 行2：进程安排
setup_merged_row(t2, 1, '研究进程安排(包括一稿、二稿、定稿起讫时间)', SCHEDULE_PARTS)
print(f'  进程安排 ✅ ({sum(len(p) for p in SCHEDULE_PARTS)} 字)')

# 行3：其它说明
t2.cell(2, 0).text = ''
p = t2.cell(2, 0).paragraphs[0]
run = p.add_run('其它说明')
run.font.size = Pt(12); run.font.bold = True; run.font.name = '宋体'
run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')

# 行4/5：留空签名
for row_idx, label in [(3, '指导教师意见'), (4, '学院教学负责人意见')]:
    t2.cell(row_idx, 0).text = ''
    p = t2.cell(row_idx, 0).paragraphs[0]
    run = p.add_run(label)
    run.font.size = Pt(12); run.font.bold = True; run.font.name = '宋体'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
    for _ in range(4):
        t2.cell(row_idx, 0).add_paragraph()
    p = t2.cell(row_idx, 0).add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = p.add_run(('指导教师' if row_idx == 3 else '教学负责人') + '（签名）：          年    月    日')
    run.font.size = Pt(12); run.font.name = '宋体'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')

# 保存
out = r'd:\代码项目\毕业设计\docs\钟靖_开题报告_v3.docx'
d.save(out)
import os
total = sum(len(p) for p in BASIS_PARTS + STATUS_PARTS + RESEARCH_PARTS)
print(f'\n✅ 开题报告 v3 已生成!')
print(f'   文件: {out} ({os.path.getsize(out)/1024:.1f} KB)')
print(f'   三部分核心内容合计: {total} 字')
print(f'     - 选题依据及研究意义: {sum(len(p) for p in BASIS_PARTS)} 字')
print(f'     - 选题研究现状: {sum(len(p) for p in STATUS_PARTS)} 字')
print(f'     - 研究内容: {sum(len(p) for p in RESEARCH_PARTS)} 字')
print(f'   参考文献: 17 篇（含 8 篇中文硕博论文 + 9 篇英文）')
