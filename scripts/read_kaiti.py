# -*- coding: utf-8 -*-
"""完整读取开题报告修改稿"""
import docx
import sys

doc_path = r'c:\Users\admin\xwechat_files\wxid_8851c1yvwxzj22_dfd4\msg\file\2026-09\开题报告_修改稿_钟靖(1).docx'
doc = docx.Document(doc_path)

print("=" * 70)
print(f"📄 开题报告修改稿 - 完整内容（共 {len(doc.paragraphs)} 段）")
print("=" * 70)

red_count = 0
for i, p in enumerate(doc.paragraphs):
    t = p.text.strip()
    if not t:
        continue
    
    # 检查标红
    has_red = False
    red_texts = []
    for r in p.runs:
        try:
            if r.font.color and r.font.color.rgb:
                c = str(r.font.color.rgb).upper()
                if c in ('FF0000', 'C00000', 'FF0001'):
                    has_red = True
                    red_texts.append(r.text)
                    red_count += 1
        except:
            pass
    
    marker = "🔴RED" if has_red else "    "
    style = p.style.name
    
    if has_red:
        print(f"\n{marker} [{i}] ({style}) 全文:")
        print(f"           {t}")
        print(f"           标红部分: {red_texts}")
    else:
        print(f"{marker} [{i}] ({style}) {t[:120]}")

# 表格
for ti, table in enumerate(doc.tables):
    print(f"\n📊 表格 {ti+1}: {len(table.rows)}行 × {len(table.columns)}列")
    for ri, row in enumerate(table.rows):
        cells = [cell.text.strip()[:30] for cell in row.cells]
        print(f"  行{ri}: {cells}")

print(f"\n{'='*70}")
print(f"🔴 标红 Run 总数: {red_count}")
print(f"📊 表格数: {len(doc.tables)}")
