# -*- coding: utf-8 -*-
"""Step 1: 清标红 + 补开发环境 + 生成中间版"""
import docx
from docx.shared import Pt, Cm
from docx.oxml.ns import qn

INPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_格式版.docx'
OUTPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_v2.docx'

doc = docx.Document(INPUT)

# === 1. 清标红 ===
red_count = 0
for p in doc.paragraphs:
    for r in p.runs:
        try:
            if r.font.color and r.font.color.rgb:
                c = str(r.font.color.rgb).upper()
                if c in ('FF0000', 'C00000', 'FF0001', 'FF00', 'C000'):
                    r.font.color.rgb = None
                    red_count += 1
        except:
            pass
# 表格里的
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            for p in cell.paragraphs:
                for r in p.runs:
                    try:
                        if r.font.color and r.font.color.rgb:
                            c = str(r.font.color.rgb).upper()
                            if c in ('FF0000', 'C00000', 'FF0001', 'FF00', 'C000'):
                                r.font.color.rgb = None
                                red_count += 1
                    except:
                        pass
print(f"✅ 清除标红: {red_count} 处")

# === 2. 找 6.1.1 位置，删除其下内容（直到 6.1.2），插入开发环境 ===
target_idx = None
next_idx = None
for i, p in enumerate(doc.paragraphs):
    text = p.text.strip()
    if target_idx is None and '6.1.1' in text and ('硬件' in text or '软件' in text):
        target_idx = i
    if target_idx is not None and i > target_idx:
        if p.style.name == 'Heading 3' and ('6.1.2' in text or '数据集' in text):
            next_idx = i
            break

print(f"📝 6.1.1 段{target_idx}，6.1.2 段{next_idx}")

if target_idx and next_idx:
    # 删除中间的段落（从 next_idx-1 往前删到 target_idx+1）
    for i in range(next_idx - 1, target_idx, -1):
        el = doc.paragraphs[i]._element
        el.getparent().remove(el)
    
    # 在 6.1.1 标题后插入开发环境内容
    # 用 add_paragraph 在 XML 层插入
    h3 = doc.paragraphs[target_idx]._element
    
    env_paragraphs = [
        ("本文实验在以下开发与运行环境下完成，覆盖硬件平台、操作系统、编程语言、核心框架与数据库等方面，保证了系统的可复现性与工程可靠性。", "body"),
        ("（1）硬件环境", "sub"),
        ("处理器：Intel Core i7-10700 @ 2.90GHz，8核16线程；内存：16GB DDR4 3200MHz；硬盘：512GB NVMe SSD（系统盘）+ 1TB HDD（数据盘）；网络：1000Mbps 以太网。", "body"),
        ("（2）软件环境", "sub"),
        ("操作系统：Microsoft Windows 10/11；编程语言：Java 21（OpenJDK）；构建工具：Apache Maven 3.9.x；后端框架：Spring Boot 3.2.12；数据库：MySQL Community Server 5.5+；连接池：Druid 1.2.24；本地缓存：Caffeine 3.1.8；模板引擎：Thymeleaf 3.1.x；小程序开发：微信开发者工具（稳定基础库）；IDE：Trae CN（VS Code 内核）。", "body"),
        ("（3）后端核心依赖", "sub"),
        ("后端核心依赖通过 pom.xml 统一管理，以 Spring Boot 3.2.12 作为父 POM 锁定版本。主要依赖包括：spring-boot-starter-web（Web 框架与 REST API）、spring-boot-starter-data-jpa（JPA 数据访问层）、spring-boot-starter-security（安全框架与 CSRF 防护）、mysql-connector-j 8.0（MySQL JDBC 驱动）、jjwt-api 0.12.5（JWT 令牌生成与验证）、druid-spring-boot-3-starter 1.2.24（高可用连接池与监控）、caffeine 3.1.8（高性能本地缓存，maximumSize=1000，expireAfterWrite=300s）。", "body"),
        ("（4）数据库配置", "sub"),
        ("数据库名为 music_recommendation，使用 UTF-8 字符集以支持中文音乐元数据（歌曲名、艺术家、流派等）。连接参数通过 application.yml 配置，敏感信息（数据库密码等）支持环境变量注入（格式 ${ENV_VAR:默认值}）。连接池初始大小为 5，最大活跃数为 20，Druid 监控页面通过 /druid/ 路径访问（开发环境无需认证）。", "body"),
    ]
    
    for text, ptype in env_paragraphs:
        # 在标题元素后插入新段落
        new_p = h3.makeelement(qn('w:p'), {})
        h3.addnext(new_p)
        
        # 填充 run
        r_elem = new_p.makeelement(qn('w:r'), {})
        new_p.append(r_elem)
        t_elem = r_elem.makeelement(qn('w:t'), {})
        t_elem.text = text
        r_elem.append(t_elem)
        
        # 设置段落格式
        pPr = new_p.makeelement(qn('w:pPr'), {})
        new_p.insert(0, pPr)
        
        if ptype == 'body':
            # 首行缩进 2 字符
            ind = pPr.makeelement(qn('w:ind'), {})
            ind.set(qn('w:firstLine'), '480')  # 2 字符 ≈ 480 twips
            pPr.append(ind)
        elif ptype == 'sub':
            # 子标题加粗
            rPr = r_elem.makeelement(qn('w:rPr'), {})
            r_elem.insert(0, rPr)
            b = rPr.makeelement(qn('w:b'), {})
            rPr.append(b)
        
        h3 = new_p  # 继续往后插
    
    print(f"  ✅ 插入 {len(env_paragraphs)} 段开发环境内容")

doc.save(OUTPUT)
print(f"\n✅ 已保存: {OUTPUT}")
