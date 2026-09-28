import sys
try:
    import docx
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'python-docx', 'lxml', '--quiet'])
    import docx

from docx.shared import Pt, Emu, Cm
from docx.oxml.ns import qn
from lxml import etree
import re

doc = docx.Document(r'd:\代码项目\毕业设计\钟靖120230730开题报告.docx')
tbl = doc.tables[0]

print('=' * 50)
print('  开题报告进度安排表格 · 完整格式诊断')
print('=' * 50)

# 1. 基本信息
print('\n[1] 基本信息')
print(f'  行数: {len(tbl.rows)} (含表头)')
print(f'  列数: {len(tbl.columns)}')
print(f'  样式名: {tbl.style.name}')

# 2. 整体对齐
print(f'\n[2] 表格整体对齐')
print(f'  alignment: {tbl.alignment}')

# 3. 列宽
print(f'\n[3] 列宽')
for ci, col in enumerate(tbl.columns):
    w = col.width
    header_text = tbl.rows[0].cells[ci].text[:10]
    if w:
        print(f'  列{ci} ({header_text}): {w} EMU = {w/914400:.2f} cm')
    else:
        print(f'  列{ci} ({header_text}): auto（未设置）')

# 4. 每行高度 + 垂直对齐
print(f'\n[4] 行高 + 垂直对齐')
for ri, row in enumerate(tbl.rows):
    h = row.height
    valigns = set()
    for cell in row.cells:
        v = cell.vertical_alignment
        valigns.add(str(v) if v else 'None')
    label = '表头' if ri == 0 else f'数据行{ri}'
    h_str = f'{h} EMU = {h/914400:.2f} cm' if h else 'auto'
    print(f'  {label}: 高度={h_str}, 垂直对齐={valigns}')

# 5. 单元格字体 + 段落格式
print(f'\n[5] 单元格字体 + 段落格式（前 3 行）')
for ri in range(min(3, len(tbl.rows))):
    for ci, cell in enumerate(tbl.rows[ri].cells):
        for pi, para in enumerate(cell.paragraphs):
            for run in para.runs:
                font = run.font
                pf = para.paragraph_format
                label = '表头' if ri == 0 else f'行{ri}'
                txt = run.text.strip()[:18]
                print(f'  [{label}·列{ci}] "{txt}"')
                print(f'    字体: name={font.name}, size={font.size}, bold={font.bold}, color={font.color.rgb if font.color and font.color.rgb else "inherit"}')
                # 东亚字体
                rpr = run._element.find(qn('w:rPr'))
                if rpr is not None:
                    ea = rpr.find(qn('w:ea'))
                    if ea is not None:
                        print(f'    东亚字体: {ea.get(qn("w:eastAsia"))}')
                # 段落格式
                print(f'    段落: 对齐={para.alignment}, 行距={pf.line_spacing}, 首行缩进={pf.first_line_indent}')

# 6. 边框检查（XML）
print(f'\n[6] 表格边框（XML 原始）')
tbl_xml = etree.tostring(tbl._element, pretty_print=True).decode()
borders = re.findall(r'<w:tblBorders>.*?</w:tblBorders>', tbl_xml, re.DOTALL)
if borders:
    print('  找到 tblBorders:')
    for line in borders[0].split('\n'):
        print(f'    {line.strip()}')
else:
    print('  未找到 tblBorders → 使用 Table Grid 默认边框（四方向全有）')

# 7. 单元格单独边框
print(f'\n[7] 单元格边框（是否有单独覆盖）')
cell_border_count = 0
for row in tbl.rows:
    for cell in row.cells:
        tc_xml = etree.tostring(cell._element, pretty_print=True).decode()
        if '<w:tcBorders>' in tc_xml:
            cell_border_count += 1
print(f'  有自定义边框的单元格数: {cell_border_count}（应为 0，统一在表格级设）')

# 8. 学校要求对比
print(f'\n[8] 与学校要求对比')
print('  ┌──────────────────┬─────────────────────────────┬────────┐')
print('  │ 检查项           │ 学校要求                     │ 当前   │')
print('  ├──────────────────┼─────────────────────────────┼────────┤')
print('  │ 边框             │ 三线表（上下粗 + 中间细，无竖线）│ Table Grid（全边框含竖线）│ ⚠️ 不匹配')
print('  │ 字体             │ 宋体五号（10.5pt）           │ 需确认 │')
print('  │ 表头加粗         │ 是（通常）                    │ 需确认 │')
print('  │ 单元格对齐       │ 居中/垂直居中                │ 需确认 │')
print('  │ 行距             │ 单倍行距                     │ 需确认 │')
print('  │ 列宽             │ 合理分配                     │ 未设置 │')
print('  └──────────────────┴─────────────────────────────┴────────┘')
