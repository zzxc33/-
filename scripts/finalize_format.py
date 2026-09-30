# -*- coding: utf-8 -*-
"""
最终格式修复：加分节符 + 清页眉 + 目录页设置
python-docx 原生支持，不会导致 XML 污染
"""
import docx
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION_START
from docx.oxml.ns import qn

INPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_格式版.docx'
OUTPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_定稿.docx'

doc = docx.Document(INPUT)

# ===== 1. 找到关键标题的段落索引 =====
targets = {
    'abstract': None,    # 摘  要
    'toc': None,         # 目  录
    'chap1': None,       # 第1章  绪论（正文开始）
    'ref': None,         # 参考文献
    'thanks': None,       # 致  谢
}

for i, p in enumerate(doc.paragraphs):
    t = p.text.strip()
    if targets['abstract'] is None and ('摘  要' in t or '摘 要' in t) and p.style.name in ('Heading 1', 'Normal') and i > 20 and i < 40:
        targets['abstract'] = i
    if targets['toc'] is None and ('目  录' in t or '目录' in t) and i > 30 and i < 50:
        targets['toc'] = i
    if targets['chap1'] is None and '第1章' in t and '绪论' in t and p.style.name == 'Heading 1':
        targets['chap1'] = i
    if targets['ref'] is None and '参考文献' in t and p.style.name == 'Heading 1':
        targets['ref'] = i
    if targets['thanks'] is None and ('致  谢' in t or '致谢' in t) and p.style.name == 'Heading 1':
        targets['thanks'] = i

print('关键段落位置:')
for k, v in targets.items():
    label = {'abstract': '摘要', 'toc': '目录', 'chap1': '第1章', 'ref': '参考文献', 'thanks': '致谢'}
    text = doc.paragraphs[v].text.strip()[:30] if v else '?'
    print(f'  {label[k]}: 段{v} "{text}"')

# ===== 2. 在关键位置前插入分节符（从后往前避免索引变化）=====
# 顺序：致谢前 → 参考文献前 → 第1章前 → 目录前 → 摘要前
# python-docx 支持 WD_SECTION_START.NEW_PAGE（下一页分节符）

sections_to_add = [
    ('thanks', WD_SECTION_START.NEW_PAGE),
    ('ref', WD_SECTION_START.NEW_PAGE),
    ('chap1', WD_SECTION_START.NEW_PAGE),
    ('toc', WD_SECTION_START.NEW_PAGE),
    ('abstract', WD_SECTION_START.NEW_PAGE),
]

for key, section_type in sections_to_add:
    idx = targets[key]
    if idx is None:
        continue
    # 在该段落之前插入分节符
    p = doc.paragraphs[idx]
    # python-docx 的 add_section 是加在文档末尾，需要用 XML 在指定位置插入
    # 通过修改段落属性来实现
    pPr = p._element.get_or_add_pPr()
    # 移除已有的 section 标记（避免重复）
    for existing in pPr.findall(qn('w:sectPr')):
        pPr.remove(existing)
    # 创建新的 sectPr 并设置 type
    sectPr = pPr.makeelement(qn('w:sectPr'), {})
    sectType = sectPr.makeelement(qn('w:type'), {qn('w:val'): 'nextPage'})
    sectPr.append(sectType)
    # 添加页眉页脚引用（继承前一节，后面会取消链接）
    pPr.append(sectPr)
    print(f'  ✅ 在段{idx}前插入分节符（{key} = 下一页）')

# ===== 3. 清空所有节的页眉和页脚 =====
for i, section in enumerate(doc.sections):
    # 页眉
    header = section.header
    header.is_linked_to_previous = False
    for p in header.paragraphs:
        p.clear()
    
    # 页脚
    footer = section.footer
    footer.is_linked_to_previous = False
    for p in footer.paragraphs:
        p.clear()
    
    print(f'  ✅ Section {i+1}: 页眉页脚已清空, 取消链接到前一节')

# ===== 4. 设置页面属性 =====
for section in doc.sections:
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.4)
    section.bottom_margin = Cm(2.4)
    section.left_margin = Cm(2.4)
    section.right_margin = Cm(2.4)

# ===== 5. 保存 =====
doc.save(OUTPUT)

# ===== 6. 验证 =====
v = docx.Document(OUTPUT)
print(f'\n✅ 定稿版已生成!')
print(f'   输出: {OUTPUT}')
print(f'   Section 数: {len(v.sections)}')
print(f'   总段落: {len(v.paragraphs)}')
print()
print('⚠️  Word 中还需手动完成（python-docx 不支持页码域）：')
print('  1. 双击页脚 → 页码 → 设置页码格式')
print('     - Section 1~3（封面/声明/摘要/目录）: 罗马数字 Ⅰ Ⅱ Ⅲ')
print('     - Section 4+（正文）: 阿拉伯数字，起始页码设为 1')
print('  2. 引用 → 目录 → 自动目录 1（在"目  录"标题下）')
print('  3. 右键目录 → 更新域 → 更新整个目录')
print()
print('💡 提示：每节都已取消"链接到前一节"，页码设置不会串！')
