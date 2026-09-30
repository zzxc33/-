# -*- coding: utf-8 -*-
"""深入读取开题报告表格 - 每个单元格完整内容+标红"""
import docx

doc_path = r'c:\Users\admin\xwechat_files\wxid_8851c1yvwxzj22_dfd4\msg\file\2026-09\开题报告_修改稿_钟靖(1).docx'
doc = docx.Document(doc_path)

table = doc.tables[0]

print("=" * 70)
print("📄 开题报告表格 - 单元格详细内容")
print("=" * 70)

row_names = [
    "学生信息",
    "题目",
    "选题依据及研究意义",
    "选题研究现状",
    "研究内容",
    "论文提纲",
    "主要参阅文献",
    "研究进程安排",
    "其它说明",
    "指导教师意见",
    "学院教学负责人意见",
]

for ri, row in enumerate(table.rows):
    cells = row.cells
    # 每行 3 列内容相同（合并单元格），只看第一列
    cell = cells[0]
    print(f"\n{'─'*60}")
    print(f"📌 行{ri}: {row_names[ri]}")
    print(f"{'─'*60}")
    
    # 检查段落和 run
    for pi, p in enumerate(cell.paragraphs):
        t = p.text.strip()
        if not t:
            continue
        
        # 检查标红
        red_in_this_para = []
        for r in p.runs:
            try:
                if r.font.color and r.font.color.rgb:
                    c = str(r.font.color.rgb).upper()
                    if c in ('FF0000', 'C00000', 'FF0001'):
                        red_in_this_para.append((r.text, c))
            except:
                pass
        
        if red_in_this_para:
            print(f"  [🔴 标红] {t[:200]}")
            for rt, rc in red_in_this_para:
                print(f"      ↳ 标红文字: '{rt}' (RGB={rc})")
        else:
            # 正常文本，截断显示
            if len(t) > 500:
                print(f"  {t[:500]}...")
            else:
                print(f"  {t}")

print("\n" + "=" * 70)
print("🔍 标红汇总")
print("=" * 70)
total_red = 0
for ri, row in enumerate(table.rows):
    for ci, cell in enumerate(row.cells):
        for pi, p in enumerate(cell.paragraphs):
            for ri2, r in enumerate(p.runs):
                try:
                    if r.font.color and r.font.color.rgb:
                        c = str(r.font.color.rgb).upper()
                        if c in ('FF0000', 'C00000', 'FF0001'):
                            total_red += 1
                            print(f"  行{ri}({row_names[ri]}) 段{pi} Run{ri2}: '{r.text[:50]}' RGB={c}")
                except:
                    pass
print(f"\n共发现标红 Run: {total_red}")
