"""
开题报告进度安排表格格式修复脚本
对齐南昌应用技术师范学院毕业论文格式要求：
  - 三线表：上下粗线 + 表头下细线，无竖线
  - 宋体五号（10.5pt）/ 英文 TNR 五号
  - 表头加粗
  - 单元格水平居中 + 垂直居中
  - 单倍行距
  - 合理列宽：序号 1cm / 内容 4.5cm / 日期 4cm / 备注 2.5cm
  - 表格整体居中
"""
import sys
try:
    import docx
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'python-docx', '--quiet'])
    import docx

from docx.shared import Pt, Cm, Emu
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

THICK = 24    # 粗线 24/8 = 3pt（上下边框）
THIN = 8      # 细线 8/8 = 1pt（表头下边框）
NONE_BORDER = 0

doc_path = r'd:\代码项目\毕业设计\钟靖120230730开题报告.docx'
doc = docx.Document(doc_path)
tbl = doc.tables[0]

# =====================================================
# 1. 表格整体居中
# =====================================================
tbl.alignment = WD_TABLE_ALIGNMENT.CENTER

# =====================================================
# 2. 列宽设置（4 列合理分配）
# =====================================================
col_widths = [Cm(1.2), Cm(5.0), Cm(4.0), Cm(2.5)]  # 序号/内容/日期/备注
for ci, col in enumerate(tbl.columns):
    # python-docx 设置列宽需要逐个 cell 设置
    for cell in col.cells:
        cell.width = col_widths[ci]

# =====================================================
# 3. 表格边框 — 三线表（通过 XML 精确控制）
# =====================================================
def set_cell_border(cell, top=None, bottom=None, left=None, right=None):
    """设置单元格边框，None=无，(size, val, color)=有"""
    tc = cell._element
    tcPr = tc.get_or_add_tcPr()
    tcBorders = OxmlElement('w:tcBorders')
    for edge, val in [('top', top), ('bottom', bottom), ('left', left), ('right', right)]:
        el = OxmlElement(f'w:{edge}')
        if val is not None:
            size, sz_val, color = val
            el.set(qn('w:val'), sz_val)
            el.set(qn('w:sz'), str(size))
            el.set(qn('w:space'), '0')
            el.set(qn('w:color'), color)
        else:
            el.set(qn('w:val'), 'nil')
        tcBorders.append(el)
    # 移除旧的
    for old in tcPr.findall(qn('w:tcBorders')):
        tcPr.remove(old)
    tcPr.append(tcBorders)

# 定义边框参数
THICK_B = (THICK, 'single', '000000')   # 粗黑线
THIN_B  = (THIN,  'single', '000000')   # 细黑线
NO_B    = None

# 行 0 = 表头，行 13 = 最后一行（序号 13 "其它"）
total_rows = len(tbl.rows)

for ri, row in enumerate(tbl.rows):
    for ci, cell in enumerate(row.cells):
        # 水平方向
        if ri == 0:
            # 表头：上粗线 + 下细线
            top_b = THICK_B
            bottom_b = THIN_B
        elif ri == total_rows - 1:
            # 最后一行：上无线 + 下粗线
            top_b = NO_B
            bottom_b = THICK_B
        else:
            # 中间行：上下都无线（三线表只有顶/底/表头下三根线）
            top_b = NO_B
            bottom_b = NO_B
        # 垂直方向：全部无竖线
        left_b = NO_B
        right_b = NO_B
        set_cell_border(cell, top=top_b, bottom=bottom_b, left=left_b, right=right_b)

print('✅ 三线表边框已设置（上粗 + 表头下细 + 下粗 + 无竖线）')

# =====================================================
# 4. 字体 + 段落格式（全部单元格）
# =====================================================
FONT_SIZE = Pt(10.5)  # 五号 = 10.5pt

def set_cell_font(cell, bold=False):
    for para in cell.paragraphs:
        # 段落：居中 + 单倍行距 + 无首行缩进
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        pf = para.paragraph_format
        pf.line_spacing = 1.0           # 单倍行距
        pf.space_before = Pt(0)
        pf.space_after = Pt(0)
        pf.first_line_indent = None

        for run in para.runs:
            run.font.name = 'Times New Roman'
            run.font.size = FONT_SIZE
            run.font.bold = bold
            # 东亚字体（宋体）
            rPr = run._element.get_or_add_rPr()
            ea = rPr.find(qn('w:ea'))
            if ea is None:
                ea = OxmlElement('w:ea')
                rPr.append(ea)
            ea.set(qn('w:eastAsia'), '宋体')

# 表头：加粗
for cell in tbl.rows[0].cells:
    set_cell_font(cell, bold=True)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER

# 数据行：常规
for ri in range(1, len(tbl.rows)):
    for cell in tbl.rows[ri].cells:
        set_cell_font(cell, bold=False)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER

print('✅ 字体已设置（宋体五号，表头加粗，居中对齐，单倍行距）')

# =====================================================
# 5. 清除 Table Grid 样式的默认边框干扰
# =====================================================
tbl.style = 'Table Grid'  # 保留样式名，但边框已被我们覆盖

# =====================================================
# 保存 + 同步
# =====================================================
doc.save(doc_path)
print(f'\n✅ 已保存: {doc_path}')

import shutil
shutil.copy2(doc_path, r'd:\代码项目\毕业设计\docs\开题报告_更新版.docx')
print('✅ docs 版本已同步')

# 验证
print('\n=== 验证：关键样式 ===')
doc2 = docx.Document(doc_path)
tbl2 = doc2.tables[0]
print(f'表格对齐: {tbl2.alignment}')
print(f'列宽: {[c.width for c in tbl2.columns]}')
print(f'表头字体: {tbl2.rows[0].cells[1].paragraphs[0].runs[0].font.name}')
print(f'表头加粗: {tbl2.rows[0].cells[1].paragraphs[0].runs[0].font.bold}')
print(f'数据行字体: {tbl2.rows[1].cells[1].paragraphs[0].runs[0].font.name}')
print(f'数据行加粗: {tbl2.rows[1].cells[1].paragraphs[0].runs[0].font.bold}')
print(f'数据行对齐: {tbl2.rows[1].cells[1].paragraphs[0].alignment}')
print(f'数据行垂直对齐: {tbl2.rows[1].cells[1].vertical_alignment}')
