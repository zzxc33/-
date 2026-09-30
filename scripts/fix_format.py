# -*- coding: utf-8 -*-
"""
论文格式修复脚本：批量修正 python-docx 能控制的格式
- 页面改为 A4 + 2.4cm 页边距
- 正文：宋体小四 12pt、25磅固定行距、首行缩进 2 字符
- Heading 1：黑体小三 15pt、加粗、居中、段前段后各 1 行
- Heading 2：黑体四号 14pt、加粗、左顶格、段前段后 0.5 行
- Heading 3：黑体小四 12pt、加粗、左顶格、段前段后 0.5 行
- 封面/摘要/Abstract 特殊处理
- 每章（Heading 1）前插入分页符
"""
import docx
from docx.shared import Pt, Cm, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_ORIENT
from docx.oxml.ns import qn
from copy import deepcopy

INPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_增强版.docx'
OUTPUT = r'd:\代码项目\毕业设计\钟靖120230730毕业论文_格式版.docx'

doc = docx.Document(INPUT)

# ===== 1. 页面设置：A4 + 2.4cm 页边距 =====
for section in doc.sections:
    # A4: 21cm x 29.7cm
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    # 页边距：上下左右各 2.4cm
    section.top_margin = Cm(2.4)
    section.bottom_margin = Cm(2.4)
    section.left_margin = Cm(2.4)
    section.right_margin = Cm(2.4)
    # 方向：纵向
    section.orientation = WD_ORIENT.PORTRAIT

print('✅ 页面设置: A4 + 2.4cm 页边距')

# ===== 2. 辅助函数 =====
def set_run_font(run, cn_font='宋体', en_font='Times New Roman', size_pt=12, bold=None, italic=None):
    """设置 run 的中文字体、英文字体、字号"""
    run.font.size = Pt(size_pt)
    run.font.name = en_font  # 英文字体
    # 设置中文字体（eastAsia）
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = rPr.makeelement(qn('w:rFonts'), {})
        rPr.insert(0, rFonts)
    rFonts.set(qn('w:ascii'), en_font)
    rFonts.set(qn('w:hAnsi'), en_font)
    rFonts.set(qn('w:eastAsia'), cn_font)
    rFonts.set(qn('w:cs'), cn_font)
    if bold is not None:
        run.font.bold = bold
    if italic is not None:
        run.font.italic = italic

def set_paragraph_format(paragraph, first_line_indent_cm=None, line_spacing_pt=None,
                         space_before_pt=None, space_after_pt=None,
                         alignment=None, page_break_before=False):
    """设置段落格式"""
    pf = paragraph.paragraph_format
    if first_line_indent_cm is not None:
        pf.first_line_indent = Cm(first_line_indent_cm)
    else:
        pf.first_line_indent = None
    if line_spacing_pt is not None:
        pf.line_spacing = Pt(line_spacing_pt)  # 固定值
    if space_before_pt is not None:
        pf.space_before = Pt(space_before_pt)
    if space_after_pt is not None:
        pf.space_after = Pt(space_after_pt)
    if alignment is not None:
        paragraph.alignment = alignment
    if page_break_before:
        # 在段落前插入分页符
        run = paragraph.add_run()
        run._element.getparent().remove(run._element)
        # 用 XML 插入分页符
        pPr = paragraph._element.get_or_add_pPr()
        pageBreakBefore = pPr.makeelement(qn('w:pageBreakBefore'), {})
        pPr.append(pageBreakBefore)

def apply_style(paragraph, style_type):
    """根据样式类型应用格式"""
    if style_type == 'H1':
        # Heading 1: 黑体 15pt 加粗 居中 段前段后各1行(12pt*1.2=14.4pt)
        for run in paragraph.runs:
            set_run_font(run, cn_font='黑体', size_pt=15, bold=True)
        set_paragraph_format(paragraph, alignment=WD_ALIGN_PARAGRAPH.CENTER,
                           space_before_pt=15, space_after_pt=15,
                           first_line_indent_cm=None, page_break_before=True)
    elif style_type == 'H2':
        # Heading 2: 黑体 14pt 加粗 左顶格 段前段后0.5行
        for run in paragraph.runs:
            set_run_font(run, cn_font='黑体', size_pt=14, bold=True)
        set_paragraph_format(paragraph, alignment=WD_ALIGN_PARAGRAPH.LEFT,
                           space_before_pt=8, space_after_pt=8,
                           first_line_indent_cm=None)
    elif style_type == 'H3':
        # Heading 3: 黑体 12pt 加粗 左顶格
        for run in paragraph.runs:
            set_run_font(run, cn_font='黑体', size_pt=12, bold=True)
        set_paragraph_format(paragraph, alignment=WD_ALIGN_PARAGRAPH.LEFT,
                           space_before_pt=6, space_after_pt=6,
                           first_line_indent_cm=None)
    elif style_type == 'BODY':
        # 正文 Normal: 宋体 12pt 25磅固定行距 首行缩进2字符(约0.74cm)
        for run in paragraph.runs:
            set_run_font(run, cn_font='宋体', size_pt=12)
        set_paragraph_format(paragraph, first_line_indent_cm=0.74,
                           line_spacing_pt=25,
                           space_before_pt=0, space_after_pt=0,
                           alignment=WD_ALIGN_PARAGRAPH.JUSTIFY)
    elif style_type == 'COVER':
        # 封面: 特殊处理，不缩进
        for run in paragraph.runs:
            set_run_font(run, cn_font='宋体', size_pt=12)
        set_paragraph_format(paragraph, first_line_indent_cm=None)
    elif style_type == 'ABSTRACT_ZH':
        # 中文摘要: 楷体GB2312 五号(10.5pt)
        for run in paragraph.runs:
            set_run_font(run, cn_font='楷体_GB2312', size_pt=10.5)
        set_paragraph_format(paragraph, first_line_indent_cm=0.74,
                           line_spacing_pt=22)
    elif style_type == 'REFERENCE':
        # 参考文献: 宋体五号(10.5pt) 悬挂缩进
        for run in paragraph.runs:
            set_run_font(run, cn_font='宋体', size_pt=10.5)
        pf = paragraph.paragraph_format
        pf.left_indent = Cm(0.74)
        pf.first_line_indent = Cm(-0.74)
        pf.line_spacing = Pt(18)

# ===== 3. 分类处理每个段落 =====
print('\n📝 正在应用样式...')

# 先标记哪些 Heading 1 需要分页（除了第一个）
h1_indices = [i for i, p in enumerate(doc.paragraphs) if p.style.name == 'Heading 1']
# 跳过第一个（封面后直接开始，不需要额外分页）
h1_to_pagebreak = set(h1_indices[1:])

# 找到特殊段落位置
abstract_idx = None
keywords_idx = None
reference_start_idx = None
thank_idx = None

for i, p in enumerate(doc.paragraphs):
    t = p.text.strip()
    if t == '摘  要' or t == '摘 要':
        abstract_idx = i
    if t.startswith('关键词：') or t.startswith('关键词:'):
        keywords_idx = i
    if '参考文献' in t and p.style.name == 'Heading 1':
        reference_start_idx = i
    if '致  谢' in t or '致谢' in t:
        thank_idx = i

print(f'  摘要位置: {abstract_idx}')
print(f'  关键词位置: {keywords_idx}')
print(f'  参考文献位置: {reference_start_idx}')
print(f'  致谢位置: {thank_idx}')

# 遍历段落应用样式
cover_section_end = 25  # 封面+声明大约到段25
abstract_section_end = keywords_idx + 1 if keywords_idx else 40
reference_section_start = reference_start_idx or 180

body_count = 0
h1_count = 0
h2_count = 0
h3_count = 0

for i, p in enumerate(doc.paragraphs):
    if not p.text.strip() and p.style.name == 'Normal':
        continue  # 空 Normal 段落跳过
    
    style = p.style.name
    
    if style == 'Heading 1':
        page_break = i in h1_to_pagebreak
        apply_style(p, 'H1')
        # 重新应用 page_break（apply_style 里已经设置了）
        h1_count += 1
    elif style == 'Heading 2':
        apply_style(p, 'H2')
        h2_count += 1
    elif style == 'Heading 3':
        apply_style(p, 'H3')
        h3_count += 1
    elif style == 'Normal':
        # 判断属于哪个区域
        if i < cover_section_end:
            # 封面区域
            apply_style(p, 'COVER')
        elif abstract_idx and abstract_idx <= i < abstract_section_end:
            # 中文摘要区域（摘要标题和内容）
            apply_style(p, 'ABSTRACT_ZH')
        elif reference_section_start and i >= reference_section_start:
            # 参考文献/致谢区域
            apply_style(p, 'REFERENCE')
        else:
            # 正文区域
            apply_style(p, 'BODY')
        body_count += 1

print(f'  Heading 1: {h1_count}段')
print(f'  Heading 2: {h2_count}段')
print(f'  Heading 3: {h3_count}段')
print(f'  Normal: {body_count}段')

# ===== 4. 摘要标题特殊处理 =====
if abstract_idx:
    p = doc.paragraphs[abstract_idx]
    for run in p.runs:
        set_run_font(run, cn_font='黑体', size_pt=15, bold=True)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER

# ===== 5. 保存 =====
doc.save(OUTPUT)

# ===== 6. 验证 =====
print('\n' + '='*60)
print('✅ 格式修复完成!')
print('='*60)

# 快速验证
v = docx.Document(OUTPUT)
print(f'\n📄 输出文件: {OUTPUT}')
print(f'📐 页面: {v.sections[0].page_width.cm:.1f}x{v.sections[0].page_height.cm:.1f}cm')
print(f'📐 页边距: 上{v.sections[0].top_margin.cm:.1f} 下{v.sections[0].bottom_margin.cm:.1f}')

# 采样显示修复效果
print('\n=== 格式采样验证 ===')
samples = [
    (5, '封面'),
    (abstract_idx, '摘要标题'),
    (abstract_idx + 1, '摘要正文'),
    (h1_indices[1] if len(h1_indices) > 1 else 50, '第1章标题'),
    (h1_indices[2] if len(h1_indices) > 2 else 75, '第2章标题'),
    (reference_start_idx, '参考文献标题'),
]
for idx, label in samples:
    if idx is not None and idx < len(v.paragraphs):
        p = v.paragraphs[idx]
        t = p.text.strip()[:20]
        font = p.runs[0].font.name if p.runs else '?'
        size = f'{p.runs[0].font.size.pt:.0f}pt' if p.runs and p.runs[0].font.size else '?'
        bold = '粗' if p.runs and p.runs[0].font.bold else ' '
        align = p.alignment
        print(f'  [{label}] 段{idx}: {font} {size} {bold} | {t}')

print('\n⚠️  还需在 Word 中手动修复的项目:')
print('  1. 分节符 + 页码（摘要/目录罗马，正文阿拉伯从1）')
print('  2. 清空页眉（学校要求留空）')
print('  3. 插入 Word 目录域（自动生成 TOC）')
print('  4. 复制实验文档的 5 个对比表格到第 6 章')
print('  5. 三线表样式设置')
print('  6. 更新参考文献编号对齐')
