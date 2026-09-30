# -*- coding: utf-8 -*-
import docx
from docx.shared import Pt, Cm

doc = docx.Document(r'd:\代码项目\毕业设计\钟靖120230730毕业论文_增强版.docx')

print('=' * 60)
print('📐 页面设置检查')
print('=' * 60)
for i, section in enumerate(doc.sections):
    print(f'Section {i+1}:')
    print(f'  页面: {section.page_width.cm:.2f} x {section.page_height.cm:.2f} cm')
    print(f'  页边距: 上{section.top_margin.cm:.1f} 下{section.bottom_margin.cm:.1f} 左{section.left_margin.cm:.1f} 右{section.right_margin.cm:.1f} cm')
    if i > 0:
        print(f'  ⚠️ 多节文档！需要检查分节和页码')

print()
print('=' * 60)
print('📊 样式使用统计')
print('=' * 60)
style_counts = {}
for p in doc.paragraphs:
    s = p.style.name
    style_counts[s] = style_counts.get(s, 0) + 1
for s, c in sorted(style_counts.items()):
    print(f'  {s}: {c}段')

print()
print('=' * 60)
print('📝 字体/字号采样（前40个有内容段落）')
print('=' * 60)
issues = []
sampled = 0
for i, p in enumerate(doc.paragraphs):
    if not p.text.strip():
        continue
    sampled += 1
    if sampled > 40:
        break
    
    style = p.style.name
    text = p.text.strip()[:25]
    
    font_name = '?'
    font_size = '?'
    font_bold = False
    font_italic = False
    align_map = {None: '默认', 0: '左', 1: '居中', 2: '右', 3: '两端'}
    align = align_map.get(p.alignment, '?')
    
    if p.runs:
        r = p.runs[0]
        font_name = r.font.name or '未设'
        font_size = f'{r.font.size.pt:.0f}pt' if r.font.size else '未设'
        font_bold = r.font.bold or False
        font_italic = r.font.italic or False
    
    # 首行缩进
    first_ind = ''
    if p.paragraph_format.first_line_indent:
        first_ind = f'首缩{p.paragraph_format.first_line_indent.cm:.1f}cm'
    
    # 行距
    ls = p.paragraph_format.line_spacing
    
    # 检测问题
    prob = []
    if style == 'Normal' and font_size not in ('12pt', '小四'):
        prob.append(f'字号={font_size}≠12pt')
    if style == 'Normal' and not first_ind:
        prob.append('无首行缩进')
    if 'Heading' in style and not font_bold:
        prob.append('标题未加粗')
    if 'Heading 1' in style and align != '居中':
        prob.append(f'H1未居中(align={align})')
    
    if prob:
        issues.append((i, style, text, prob))
    
    flag = ' ⚠️' if prob else ''
    print(f'{i:3d} [{style:10s}] {font_name:6s} {font_size:5s} {"粗" if font_bold else " "} {"斜" if font_italic else " "} | {align} | {first_ind:10s} | {text:25s}{flag}')

print()
print('=' * 60)
print('❌ 发现的问题汇总')
print('=' * 60)
if issues:
    for idx, style, text, probs in issues:
        print(f'  段{idx} [{style}] "{text}": {", ".join(probs)}')
else:
    print('  ✅ 前40段无明显格式问题')

print()
print('=' * 60)
print('📋 学校规范 vs 当前状态')
print('=' * 60)
checklist = [
    ('页面大小', 'A4 (21x29.7cm)', '检查 section.page_width'),
    ('页边距', '上下左右各 2.4cm', '需在 Word 中设置'),
    ('正文字体', '宋体 小四 (12pt)', 'python-docx 默认 Calibri → 需改宋体'),
    ('正文行距', '25磅固定值', '需在 Word 段落设置'),
    ('正文首行缩进', '2 字符 (约 0.74cm)', 'python-docx 默认无 → 需加'),
    ('一级标题', '小三 (15pt) 黑体 居中', 'Heading 1 样式定义'),
    ('二级标题', '四号 (14pt) 黑体 左顶格', 'Heading 2 样式定义'),
    ('三级标题', '小四 (12pt) 黑体 左顶格', 'Heading 3 样式定义'),
    ('页码', '摘要前罗马/正文阿拉伯从1开始', '需分节设置'),
    ('页眉', '留空（学校要求）', '需清除'),
    ('目录', 'Word 自动生成域', '当前仅有标题无域 → 需插入'),
    ('三线表', '上下粗线表头下细线', '实验文档有表格但未复制进来'),
    ('参考文献', '宋体五号 GB/T 7714-2025', '需检查'),
    ('摘要字体', '楷体GB2312五号', '需单独设置'),
]
for item, standard, current in checklist:
    print(f'  {item:10s} | 规范: {standard:30s} | {current}')
