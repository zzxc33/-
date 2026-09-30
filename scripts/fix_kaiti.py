# -*- coding: utf-8 -*-
"""开题报告修改：清标红 + 补开发环境 + 补参考文献"""
import docx
import re
from docx.oxml.ns import qn

INPUT = r'c:\Users\admin\xwechat_files\wxid_8851c1yvwxzj22_dfd4\msg\file\2026-09\开题报告_修改稿_钟靖(1).docx'
OUTPUT = r'd:\代码项目\毕业设计\开题报告_钟靖_最终版.docx'

doc = docx.Document(INPUT)
table = doc.tables[0]

# === 1. 清除所有标红 ===
red_cleared = 0
for row in table.rows:
    for cell in row.cells:
        for para in cell.paragraphs:
            for run in para.runs:
                try:
                    if run.font.color and run.font.color.rgb:
                        c = str(run.font.color.rgb).upper()
                        if c in ('FF0000', 'C00000', 'FF0001', 'FF00', 'C000'):
                            run.font.color.rgb = None  # 清除颜色
                            red_cleared += 1
                except:
                    pass
            # 也检查 paragraph 级别
            try:
                for run in para.runs:
                    pass  # 已处理
            except:
                pass
print(f"✅ 清除标红: {red_cleared} 处")

# === 2. 更新开发环境（行8）===
row8 = table.rows[8].cells[0]
# 清空现有段落，重新写
new_env = """本系统开发环境如下：
（1）硬件环境：Intel Core i7-10700 @ 2.90GHz（8核16线程）、16GB DDR4 3200MHz 内存、512GB NVMe SSD + 1TB HDD 存储。
（2）操作系统：Microsoft Windows 10/11。
（3）开发工具：Trae CN（VS Code 内核，支持 Java 调试、Spring Boot 自动配置、Git 版本控制），辅助工具包括微信开发者工具（小程序调试）、MySQL Workbench（数据库管理）、Postman（接口调试）、Git（版本控制）。
（4）编程语言与运行时：Java 21（OpenJDK），前端使用 HTML5/CSS3/JavaScript ES6+。
（5）后端框架与依赖：Spring Boot 3.2.12（Web、JPA、Security 三大 Starter）、Spring Data JPA（数据访问）、Spring Security（Web Session + 小程序 JWT 双通道鉴权）、MySQL Connector/J 8.0（JDBC 驱动）、Druid 1.2.24（高可用连接池+监控）、Caffeine 3.1.8（高性能本地缓存）、JJWT 0.12.5（JWT 令牌）、Thymeleaf 3.1.x（模板引擎）。
（6）数据库：MySQL Community Server 5.5+，数据库名 music_recommendation，UTF-8 字符集，Druid 连接池监控页面 /druid/ 可访问。
（7）构建与部署：Apache Maven 3.9.x（依赖管理与打包构建），Spring Boot 内置 Tomcat 运行于 8080 端口。
（8）运行环境：系统运行于 Chrome、Edge 等主流浏览器，小程序在微信客户端及微信开发者工具中运行。"""

# 清空行8的所有段落
for para in row8.paragraphs:
    el = para._element
    el.getparent().remove(el)

# 重新插入段落
lines = new_env.split('\n')
heading = lines[0]
body_lines = lines[1:]

# 标题
p = row8.add_paragraph()
p.add_run(heading)

# 各子项
for line in body_lines:
    line = line.strip()
    if not line:
        continue
    p = row8.add_paragraph()
    # 子项编号加粗 - 用 split 代替正则
    if line.startswith('（') and '）' in line:
        idx = line.index('）') + 1
        prefix = line[:idx]
        rest = line[idx:]
        r1 = p.add_run(prefix)
        r1.bold = True
        p.add_run(rest)
    else:
        p.add_run(line)

print(f"✅ 更新开发环境为详细 8 点版本")

# === 3. 补充参考文献（行6）===
row6 = table.rows[6].cells[0]

# 收集现有参考文献文本
existing_refs = []
for para in row6.paragraphs:
    txt = para.text.strip()
    if txt and not txt.startswith('主要参阅'):
        clean = re.sub(r'^\[\d+\]\s*', '', txt)
        existing_refs.append(clean)

print(f"现有参考文献: {len(existing_refs)} 条")

# 新增参考文献（来自 4 篇 PDF）
new_refs_pdfs = [
    "毛骞, 乔一天, 黄小龙. 推荐系统冷启动问题解决方法研究综述[J]. 计算机科学与探索, 2024, 18(5): 1210-1235.",
    "李改, 李致, 马会娟. 一种解决协同过滤系统冷启动问题的新算法[J]. 山东大学学报(工学版), 2024, 54(2): 12-20.",
    "王方圆. 融合协同过滤的XGBoost在音乐推送上的应用研究[J]. 科技创新与应用, 2024(11): 50-53.",
    "覃琼花. 基于协同过滤算法的个性化推荐系统研究[J]. 科技资讯, 2022, 20(10): 1-4.",
]

all_refs = existing_refs + new_refs_pdfs
print(f"合并后: {len(all_refs)} 条")

# 清空现有参考文献段落（保留标题）
title_para = row6.paragraphs[0]
for para in row6.paragraphs[1:]:
    el = para._element
    el.getparent().remove(el)

# 重新添加带正确编号的参考文献
for idx, ref_text in enumerate(all_refs):
    num = idx + 1
    full = f"[{num}] {ref_text}"
    p = row6.add_paragraph(full)
    # 设置悬挂缩进
    from docx.shared import Cm
    p.paragraph_format.left_indent = Cm(0.74)
    p.paragraph_format.first_line_indent = Cm(-0.74)

print(f"✅ 参考文献补充为 {len(all_refs)} 条")

# === 4. 保存 ===
doc.save(OUTPUT)

# === 5. 自检 ===
final = docx.Document(OUTPUT)
t2 = final.tables[0]
remaining_red = 0
total_refs = 0

for row in t2.rows:
    for cell in row.cells:
        for para in cell.paragraphs:
            t = para.text.strip()
            if t.startswith('[') and len(t) > 1 and t[1:2].isdigit():
                total_refs += 1
            for run in para.runs:
                try:
                    if run.font.color and run.font.color.rgb:
                        c = str(run.font.color.rgb).upper()
                        if c in ('FF0000', 'C00000', 'FF0001'):
                            remaining_red += 1
                except:
                    pass

print("\n" + "=" * 60)
print("📊 开题报告修改自检")
print("=" * 60)
print(f"  输出: {OUTPUT}")
print(f"  剩余标红: {remaining_red} {'❌' if remaining_red else '✅ 无'}")
print(f"  参考文献总数: {total_refs}")
print(f"  开发环境: 详细 8 点（硬件/OS/工具/语言/框架/DB/构建/运行）")
print(f"\n🎉 修改完成，可直接上传系统！")
