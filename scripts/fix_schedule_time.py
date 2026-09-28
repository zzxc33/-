"""把桌面上的开题报告「研究进程安排」从周次改为日期"""
import sys
try:
    import docx
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'python-docx', '--quiet'])
    import docx

from docx.oxml.ns import qn
from docx.oxml import OxmlElement

doc_path = r'c:\Users\admin\Desktop\实习证明\钟靖120230730开题报告.docx'
doc = docx.Document(doc_path)

# Table 1, Row 1
tbl = doc.tables[1]
cell = tbl.rows[1].cells[0]

new_schedule = """研究进程安排（包括一稿、二稿、定稿起讫时间）

2026年9月10日——9月20日 选题工作（已完成）
2026年9月21日——9月30日 开题报告撰写（进行中）
2026年10月1日——10月15日 开题答辩
2026年10月16日——11月30日 系统设计与开发（骨架搭建、推荐引擎实现、双端开发）
2026年12月1日——12月31日 系统优化与测试（性能优化、自动化测试、推荐质量评估）
2027年1月1日——1月31日 论文初稿撰写（一稿）
2027年2月1日——2月7日 中期检查
2027年2月8日——3月7日 论文二稿（导师意见修改）
2027年3月8日——3月28日 论文定稿
2027年3月29日——4月4日 论文检测（查重）
2027年4月5日——4月11日 论文评阅
2027年4月12日——4月16日 答辩资格审查
2027年4月17日——4月27日 毕业论文答辩
2027年5月6日——5月8日 成绩录入"""

# 方法：清空 cell 的所有段落，然后逐个新建保留字体
# 先备份原字体
cell_xml = cell._element
# 拿到第一段第一个 run 的格式模板
template_run = None
for para in cell.paragraphs:
    if para.runs and para.runs[0].text.strip():
        template_run = para.runs[0]
        break

print(f'模板字体: name={template_run.font.name} size={template_run.font.size} bold={template_run.font.bold}')
if template_run._element.find(qn('w:rPr')) is not None:
    ea = template_run._element.find(qn('w:rPr')).find(qn('w:ea'))
    if ea is not None:
        print(f'模板东亚字体: {ea.get(qn("w:eastAsia"))}')

# 清空现有段落（保留第一个 paragraph，删掉其余）
while len(cell.paragraphs) > 1:
    p = cell.paragraphs[-1]._element
    p.getparent().remove(p)
# 清空第一个 paragraph 的内容
for run in list(cell.paragraphs[0].runs):
    run._element.getparent().remove(run._element)

# 逐行写入
lines = new_schedule.strip().split('\n')
current_para = cell.paragraphs[0]

for li, line in enumerate(lines):
    if line == '':
        # 空行 → 新建空 paragraph
        new_p = OxmlElement('w:p')
        current_para._element.addnext(new_p)
        from docx.text.paragraph import Paragraph
        current_para = Paragraph(new_p, current_para._parent)
        continue

    if li == 0:
        # 第一行：标题（可以加粗或保持原样）
        run = current_para.add_run(line)
    else:
        # 后续行：如果当前 paragraph 已有内容，新建一个
        if current_para.runs:
            new_p = OxmlElement('w:p')
            current_para._element.addnext(new_p)
            from docx.text.paragraph import Paragraph
            current_para = Paragraph(new_p, current_para._parent)
        run = current_para.add_run(line)

    # 复制模板格式
    if template_run is not None:
        run.font.name = template_run.font.name
        run.font.size = template_run.font.size
        run.font.bold = template_run.font.bold
        # 东亚字体
        rpr_src = template_run._element.find(qn('w:rPr'))
        if rpr_src is not None:
            ea_src = rpr_src.find(qn('w:ea'))
            if ea_src is not None:
                rpr_dst = run._element.get_or_add_rPr()
                ea_dst = OxmlElement('w:ea')
                ea_dst.set(qn('w:eastAsia'), ea_src.get(qn('w:eastAsia')))
                rpr_dst.append(ea_dst)

doc.save(doc_path)
print(f'\n✅ 已保存: {doc_path}')

# 验证
doc2 = docx.Document(doc_path)
cell2 = doc2.tables[1].rows[1].cells[0]
print('\n=== 验证：新的研究进程安排 ===')
for para in cell2.paragraphs:
    if para.text.strip():
        print(f'  {para.text.strip()[:85]}')
