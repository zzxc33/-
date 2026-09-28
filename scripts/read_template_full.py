"""完整读取学校 .doc 模板 — 修复混合列宽 + 页面单位"""
import sys
import win32com.client

doc_path = r'c:\Users\admin\xwechat_files\wxid_8851c1yvwxzj22_dfd4\msg\file\2026-09\2.毕业论文（设计）开题报告.doc'

word = win32com.client.Dispatch('Word.Application')
word.Visible = False
doc = word.Documents.Open(doc_path)

TWIPS_PER_CM = 567  # Word COM 单位是 twips

print('=' * 60)
print('  学校开题报告 .doc 模板 · 完整格式（修正版）')
print('=' * 60)

# 页面设置（COM 单位是 twips，不是 cm！）
ps = doc.PageSetup
print(f'\n【页面设置】')
print(f'  纸张: {ps.PageWidth/TWIPS_PER_CM:.2f} x {ps.PageHeight/TWIPS_PER_CM:.2f} cm')
print(f'  边距: 上{ps.TopMargin/TWIPS_PER_CM:.2f} 下{ps.BottomMargin/TWIPS_PER_CM:.2f} 左{ps.LeftMargin/TWIPS_PER_CM:.2f} 右{ps.RightMargin/TWIPS_PER_CM:.2f} cm')

# 表格
print(f'\n【表格】共 {doc.Tables.Count} 个')
line_styles = {0:'无', 1:'单线', 2:'粗线', 6:'细双线', 7:'粗双线', 10:'点线', 11:'虚线'}

for ti in range(1, doc.Tables.Count + 1):
    t = doc.Tables(ti)
    print(f'\n  ── Table {ti}: {t.Rows.Count} 行 × {t.Columns.Count} 列 ──')
    
    # 边框
    b = t.Borders
    for edge_name, idx in [('上', 1), ('下', 2), ('左', 3), ('右', 4), ('内部横', 5), ('内部竖', 6)]:
        ls = b(idx).LineStyle
        lw = b(idx).LineWidth
        print(f'    {edge_name}: style={line_styles.get(ls, ls)}, width={lw}')
    
    # 每个单元格（不用 Columns 集合，改用逐个 cell）
    for ri in range(1, min(t.Rows.Count + 1, 4)):
        for ci in range(1, t.Columns.Count + 1):
            try:
                cell = t.Cell(ri, ci)
                txt = cell.Range.Text.replace('\r\x07', '').strip()
                if not txt:
                    continue
                font = cell.Range.Font
                pfmt = cell.Range.ParagraphFormat
                align_map = {0:'左', 1:'居中', 2:'右', 3:'两端', 4:'分散'}
                valign_map = {0:'顶端', 1:'居中', 2:'底端'}
                rh = t.Rows(ri).Height
                cw = cell.Width
                print(f'    [{ri},{ci}] "{txt[:25]}"')
                print(f'         字体={font.Name} {font.Size}pt bold={font.Bold}')
                print(f'         对齐=水平{align_map.get(pfmt.Alignment, "?")}/垂直{valign_map.get(cell.VerticalAlignment, "?")}')
                print(f'         行高={rh/TWIPS_PER_CM:.2f}cm 列宽={cw/TWIPS_PER_CM:.2f}cm')
            except Exception as e:
                print(f'    [{ri},{ci}] 错误: {e}')

# 全局样式
print(f'\n【全局默认字体】')
try:
    normal = doc.Styles('正文')
    f = normal.Font
    print(f'  正文样式: {f.Name} {f.Size}pt')
except:
    pass

# 封面区 + 正文字体对比
print(f'\n【关键区域字体对比】')
areas = [
    (1, 1, '封面大标题'),
    (2, 9, '表单标签（仿宋）'),
    (9, 10, '表单填写（宋体）'),
    (12, 13, '一级标题（仿宋加粗）'),
    (13, 14, '正文内容（楷体）'),
]
for start_p, end_p, label in areas:
    text_content = ''
    font_name = None
    font_size = None
    bold = None
    for i in range(start_p, min(end_p + 1, doc.Paragraphs.Count + 1)):
        p = doc.Paragraphs(i)
        txt = p.Range.Text.strip()[:30]
        if txt:
            f = p.Range.Font
            font_name = font_name or f.Name
            font_size = font_size or f.Size
            bold = bold if bold is not None else f.Bold
            text_content += txt
    print(f'  {label}: font={font_name} {font_size}pt bold={bold} text="{text_content[:40]}"')

doc.Close(False)
word.Quit()
print('\n✅ 完成')
