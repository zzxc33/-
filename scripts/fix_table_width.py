"""补充修复：用 XML 设置表格列宽（python-docx 的 cell.width 改不了 gridCol）"""
import sys
try:
    import docx
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'python-docx', '--quiet'])
    import docx

from docx.oxml.ns import qn
from docx.shared import Cm

doc_path = r'd:\代码项目\毕业设计\钟靖120230730开题报告.docx'
doc = docx.Document(doc_path)
tbl = doc.tables[0]

# 目标列宽（EMU）：序号 1.2cm / 内容 5.0cm / 日期 4.0cm / 备注 2.5cm
target_widths = [int(Cm(1.2)), int(Cm(5.0)), int(Cm(4.0)), int(Cm(2.5))]
total_cm = sum(target_widths) / 914400
print(f'目标列宽: {[round(w/914400, 2) for w in target_widths]} cm, 总宽={total_cm:.1f} cm')

# 方法 1：修改 tblGrid 的 gridCol 元素
tbl_grid = tbl._element.find(qn('w:tblGrid'))
if tbl_grid is not None:
    cols = tbl_grid.findall(qn('w:gridCol'))
    print(f'当前 gridCol 数: {len(cols)}')
    for i, col in enumerate(cols):
        old_w = int(col.get(qn('w:w'), 0))
        col.set(qn('w:w'), str(target_widths[i]))
        print(f'  列{i}: {old_w/914400:.2f}cm → {target_widths[i]/914400:.2f}cm')
    print('✅ tblGrid 列宽已更新')
else:
    print('⚠️ 未找到 tblGrid，尝试方法 2')

# 方法 2：同时设置每行的 tcPr/tcW（保险起见）
for row in tbl.rows:
    for ci, cell in enumerate(row.cells):
        tc = cell._element
        tcPr = tc.get_or_add_tcPr()
        tcW = tcPr.find(qn('w:tcW'))
        if tcW is None:
            from docx.oxml import OxmlElement
            tcW = OxmlElement('w:tcW')
            tcPr.append(tcW)
        tcW.set(qn('w:w'), str(target_widths[ci]))
        tcW.set(qn('w:type'), 'dxa')  # dxa = twips（1/20 pt），但 EMU 也能用

# 保存
doc.save(doc_path)
print(f'\n✅ 已保存: {doc_path}')

import shutil
shutil.copy2(doc_path, r'd:\代码项目\毕业设计\docs\开题报告_更新版.docx')
print('✅ docs 版本已同步')

# 验证
doc2 = docx.Document(doc_path)
tbl2 = doc2.tables[0]
tbl_grid2 = tbl2._element.find(qn('w:tblGrid'))
if tbl_grid2 is not None:
    cols2 = tbl_grid2.findall(qn('w:gridCol'))
    print(f'\n=== 验证：tblGrid 列宽 ===')
    for i, col in enumerate(cols2):
        print(f'  列{i}: {int(col.get(qn("w:w")))/914400:.2f} cm')
