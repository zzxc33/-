"""
开题报告格式重排脚本 — 对齐学校 .doc 模板的公文格式体系
==========================================================
学校模板字体体系（从 Word COM 实测）：
  封面大标题 → 黑体 SimHei  16pt（三号）加粗
  章节标题   → 仿宋 FangSong 12pt（小四）加粗
  子标题     → 仿宋 FangSong 12pt（小四）加粗
  正文段落   → 楷体 KaiTi    12pt（小四）常规
  表格表头   → 仿宋 FangSong 12pt（小四）加粗
  表格内容   → 楷体 KaiTi    12pt（小四）常规
  参考文献   → 宋体 SimSun   10.5pt（五号）常规（GB/T 7714 学术格式）

表格边框：六方向全边框，单线 width=4（不是三线表！）
表格对齐：整体居中 + 单元格水平居中 + 垂直居中
"""
import sys
try:
    import docx
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'python-docx', '--quiet'])
    import docx

from docx.shared import Pt, Cm
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

doc_path = r'd:\代码项目\毕业设计\钟靖120230730开题报告.docx'
doc = docx.Document(doc_path)

# =====================================================
# 工具函数：设置 run 的中英文字体
# =====================================================
def set_run_font(run, cn_font, en_font, size_pt, bold=False):
    """设置 run 的中文字体、英文字体、字号、加粗"""
    run.font.name = en_font
    run.font.size = Pt(size_pt)
    run.font.bold = bold
    # 东亚字体
    rPr = run._element.get_or_add_rPr()
    ea = rPr.find(qn('w:ea'))
    if ea is None:
        ea = OxmlElement('w:ea')
        rPr.append(ea)
    ea.set(qn('w:eastAsia'), cn_font)

def set_para_format(para, alignment=None, line_spacing=None, space_before=None, space_after=None, first_line_indent=None):
    """设置段落格式"""
    pf = para.paragraph_format
    if alignment is not None:
        para.alignment = alignment
    if line_spacing is not None:
        pf.line_spacing = line_spacing
    if space_before is not None:
        pf.space_before = Pt(space_before)
    if space_after is not None:
        pf.space_after = Pt(space_after)
    if first_line_indent is not None:
        pf.first_line_indent = first_line_indent

def set_cell_border_all(cell, val='single', sz='4', color='000000'):
    """设置单元格四方向边框（全边框）"""
    tc = cell._element
    tcPr = tc.get_or_add_tcPr()
    tcBorders = OxmlElement('w:tcBorders')
    for edge in ['top', 'bottom', 'left', 'right']:
        el = OxmlElement(f'w:{edge}')
        el.set(qn('w:val'), val)
        el.set(qn('w:sz'), sz)
        el.set(qn('w:space'), '0')
        el.set(qn('w:color'), color)
        tcBorders.append(el)
    for old in tcPr.findall(qn('w:tcBorders')):
        tcPr.remove(old)
    tcPr.append(tcBorders)

# =====================================================
# 1. 段落字体重排（逐段落按内容类型分类）
# =====================================================
print('=' * 60)
print('  开题报告格式重排 · 开始')
print('=' * 60)

for i, p in enumerate(doc.paragraphs):
    txt = p.text.strip()
    if not txt:
        continue

    # 分类判断
    is_cover_title = (i == 0)                              # 封面大标题 "开题报告"
    is_subtitle = (i in [1, 2])                            # 副标题 / 破折号行
    is_toc_label = (i == 3)                                # "目  录"
    is_toc_hint = (i == 4)                                 # 目录更新提示
    is_heading1 = (p.style.name == 'Heading 1')            # 一级标题 一、二、三...
    is_heading4 = (p.style.name == 'Heading 4')            # 子标题
    is_reference = txt.startswith('[') and ']' in txt[:5]  # 参考文献 [1]...
    is_body = not any([is_cover_title, is_subtitle, is_toc_label, is_toc_hint,
                       is_heading1, is_heading4, is_reference])

    # 根据分类设置字体
    if is_cover_title:
        # 黑体 16pt（三号）加粗 + 居中
        for run in p.runs:
            set_run_font(run, '黑体', 'Arial', 16, bold=True)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.CENTER, line_spacing=1.5)
        print(f'  [{i}] 封面大标题 → 黑体 16pt 加粗 居中')

    elif is_subtitle:
        # 仿宋 12pt（小四）
        for run in p.runs:
            set_run_font(run, '仿宋', 'Arial', 12, bold=False)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.CENTER, line_spacing=1.5)
        print(f'  [{i}] 副标题 → 仿宋 12pt 居中')

    elif is_toc_label:
        # "目录" → 仿宋 12pt 加粗 居中
        for run in p.runs:
            set_run_font(run, '仿宋', 'Arial', 12, bold=True)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.CENTER, line_spacing=1.5)
        print(f'  [{i}] 目录 → 仿宋 12pt 加粗')

    elif is_toc_hint:
        # 仿宋 12pt
        for run in p.runs:
            set_run_font(run, '仿宋', 'Arial', 12, bold=False)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.CENTER)
        print(f'  [{i}] 目录提示 → 仿宋 12pt')

    elif is_heading1:
        # 章节标题 → 仿宋 12pt 加粗 + 居中 + 段前段后 12pt
        for run in p.runs:
            set_run_font(run, '仿宋', 'Arial', 12, bold=True)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.CENTER,
                         line_spacing=1.5, space_before=12, space_after=12)
        print(f'  [{i}] 一级标题 → 仿宋 12pt 加粗 居中')

    elif is_heading4:
        # 子标题 → 仿宋 12pt 加粗 + 左对齐
        for run in p.runs:
            set_run_font(run, '仿宋', 'Arial', 12, bold=True)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.LEFT,
                         line_spacing=1.5, space_before=6, space_after=6)
        print(f'  [{i}] 子标题 → 仿宋 12pt 加粗')

    elif is_reference:
        # 参考文献 → 宋体 10.5pt（五号）+ 悬挂缩进
        for run in p.runs:
            set_run_font(run, '宋体', 'Times New Roman', 10.5, bold=False)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.LEFT, line_spacing=1.5)
        print(f'  [{i}] 参考文献 → 宋体 10.5pt')

    elif is_body:
        # 正文 → 楷体 12pt（小四）+ 首行缩进 2 字符 + 行距 25 磅
        for run in p.runs:
            set_run_font(run, '楷体', 'Times New Roman', 12, bold=False)
        set_para_format(p, alignment=WD_ALIGN_PARAGRAPH.JUSTIFY,
                         line_spacing=25, first_line_indent=Cm(0.74))
        print(f'  [{i}] 正文 → 楷体 12pt 两端对齐 首行缩进')

    else:
        print(f'  [{i}] 未分类 → 跳过')

print('\n✅ 段落字体重排完成')

# =====================================================
# 2. 表格格式重排
# =====================================================
for ti, tbl in enumerate(doc.tables):
    print(f'\n  处理 Table {ti}: {len(tbl.rows)} 行 × {len(tbl.columns)} 列')

    # 2a. 表格整体居中
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER

    # 2b. 列宽（保持之前的合理分配）
    target_dxa = [680, 2835, 2268, 1417]  # 1.2 / 5.0 / 4.0 / 2.5 cm
    tbl_grid = tbl._element.find(qn('w:tblGrid'))
    if tbl_grid is not None:
        for ci, col in enumerate(tbl_grid.findall(qn('w:gridCol'))):
            col.set(qn('w:w'), str(target_dxa[ci]))

    # 2c. 边框：六方向全边框（单线 width=4）
    for row in tbl.rows:
        for cell in row.cells:
            set_cell_border_all(cell, val='single', sz='4', color='000000')

    # 2d. 字体：表头 → 仿宋 12pt 加粗；内容 → 楷体 12pt
    for ri, row in enumerate(tbl.rows):
        for ci, cell in enumerate(row.cells):
            is_header = (ri == 0)
            cn_font = '仿宋' if is_header else '楷体'
            bold = is_header
            for para in cell.paragraphs:
                para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                pf = para.paragraph_format
                pf.line_spacing = 1.5
                pf.space_before = Pt(0)
                pf.space_after = Pt(0)
                for run in para.runs:
                    set_run_font(run, cn_font, 'Times New Roman', 12, bold=bold)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER

    header_font = '仿宋 12pt 加粗'
    content_font = '楷体 12pt'
    print(f'    ✅ 边框: 全边框单线 width=4')
    print(f'    ✅ 表头: {header_font} / 内容: {content_font}')
    print(f'    ✅ 对齐: 整体居中 + 单元格居中')
    print(f'    ✅ 列宽: 序号1.2cm/内容5cm/日期4cm/备注2.5cm')

print('\n✅ 表格格式重排完成')

# =====================================================
# 3. 保存 + 同步
# =====================================================
doc.save(doc_path)
print(f'\n✅ 已保存: {doc_path}')

import shutil
shutil.copy2(doc_path, r'd:\代码项目\毕业设计\docs\开题报告_更新版.docx')
print('✅ docs 版本已同步')

# 验证
print('\n' + '=' * 60)
print('  重排后验证（关键节点）')
print('=' * 60)
doc2 = docx.Document(doc_path)
for i in [0, 6, 7, 10, 27, 29, 31, 32]:
    p = doc2.paragraphs[i]
    font_name = ea_font = size = bold = None
    if p.runs:
        r = p.runs[0]
        font_name = r.font.name
        size = r.font.size
        bold = r.font.bold
        rpr = r._element.find(qn('w:rPr'))
        if rpr is not None:
            ea = rpr.find(qn('w:ea'))
            if ea is not None:
                ea_font = ea.get(qn('w:eastAsia'))
    size_str = f'{size.pt}pt' if size else '?'
    print(f'  [{i}] style={p.style.name:10s} | {ea_font}/{font_name} {size_str} B={bold} | "{p.text.strip()[:35]}"')

if doc2.tables:
    t = doc2.tables[0]
    hcell = t.rows[0].cells[1]
    dcell = t.rows[1].cells[1]
    hf = hcell.paragraphs[0].runs[0]
    df = dcell.paragraphs[0].runs[0]
    hea = hf._element.find(qn('w:rPr')).find(qn('w:ea')).get(qn('w:eastAsia'))
    dea = df._element.find(qn('w:rPr')).find(qn('w:ea')).get(qn('w:eastAsia'))
    print(f'\n  表格表头字体: {hea} {hf.font.size.pt}pt bold={hf.font.bold}')
    print(f'  表格内容字体: {dea} {df.font.size.pt}pt bold={df.font.bold}')
    print(f'  表格对齐: {t.alignment}')
    print(f'  表格边框: 已设全边框单线（需 Word 打开验证）')
