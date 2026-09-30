# -*- coding: utf-8 -*-
"""批量提取上传文件的参考文献和开发环境信息"""
import pdfplumber
import docx
import os

pdfs = [
    r'd:\新建文件夹\推荐系统冷启动问题解决方法研究综述_毛骞.pdf',
    r'd:\新建文件夹\一种解决协同过滤系统冷启动问题的新算法_李改.pdf',
    r'd:\新建文件夹\融合协同过滤的XGBoost在音乐推送上的应用研究_王方圆.pdf',
    r'd:\新建文件夹\基于协同过滤算法的个性化推荐系统研究_覃琼花.pdf',
]

for pdf_path in pdfs:
    name = os.path.basename(pdf_path)
    print(f"\n{'='*60}")
    print(f"📄 {name}")
    print('='*60)
    try:
        with pdfplumber.open(pdf_path) as pdf:
            # 读前 2 页 + 最后 2 页（摘要/关键词 + 参考文献）
            total = len(pdf.pages)
            pages_to_read = list(range(min(3, total))) + list(range(max(0, total-3), total))
            pages_to_read = list(dict.fromkeys(pages_to_read))  # 去重保持顺序
            
            full_text = []
            for i in pages_to_read:
                text = pdf.pages[i].extract_text() or ''
                full_text.append(f"--- 第{i+1}页 ---\n{text}")
            
            combined = '\n'.join(full_text)
            
            # 提取摘要/关键词
            for line in combined.split('\n'):
                line = line.strip()
                if any(kw in line for kw in ['摘要', '关键词', 'Abstract', 'Keywords']):
                    print(f"  {line[:100]}")
            
            # 提取参考文献（从最后几页）
            refs_text = '\n'.join(full_text[-2:])  # 最后两页
            print(f"\n  📚 参考文献（最后两页）:")
            for line in refs_text.split('\n'):
                line = line.strip()
                if line and any(c.isdigit() for c in line[:3]):
                    print(f"    {line[:120]}")
            
            print(f"\n  总页数: {total}")
    except Exception as e:
        print(f"  ❌ 读取失败: {e}")

# ===== 读取开题报告修改稿 =====
print(f"\n{'='*60}")
print("📄 开题报告修改稿")
print('='*60)
try:
    doc_path = r'c:\Users\admin\xwechat_files\wxid_8851c1yvwxzj22_dfd4\msg\file\2026-09\开题报告_修改稿_钟靖(1).docx'
    doc = docx.Document(doc_path)
    for i, p in enumerate(doc.paragraphs):
        t = p.text.strip()
        if t:
            # 找开发环境、技术栈相关内容
            if any(kw in t for kw in ['环境', '技术栈', '开发工具', '运行', '配置', 'Spring', 'MySQL', 'JDK', 'Node', '微信']):
                print(f"  [{i}] {t[:120]}")
except Exception as e:
    print(f"  ❌ 读取失败: {e}")
