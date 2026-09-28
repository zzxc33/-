"""读取学校 .doc 开题报告模板的格式细节"""
import sys
import os

doc_path = r'c:\Users\admin\xwechat_files\wxid_8851c1yvwxzj22_dfd4\msg\file\2026-09\2.毕业论文（设计）开题报告.doc'

# 方法 1：用 Word COM 读取
try:
    import win32com.client
    word = win32com.client.Dispatch('Word.Application')
    word.Visible = False
    doc = word.Documents.Open(doc_path)
    
    print('=' * 60)
    print('  学校开题报告 .doc 模板 · 格式诊断')
    print('=' * 60)
    
    # 1. 段落格式
    print(f'\n[1] 段落总数: {doc.Paragraphs.Count}')
    for i in range(1, min(doc.Paragraphs.Count + 1, 40)):
        p = doc.Paragraphs(i)
        txt = p.Range.Text.strip()[:60]
        if txt:
            style = p.Style.NameLocal if p.Style else 'None'
            font = p.Range.Font
            print(f'  [{i:2d}] style={style:12s} font={font.Name} size={font.Size}pt bold={font.Bold} align={p.Alignment} text="{txt}"')
    
    # 2. 表格格式
    print(f'\n[2] 表格总数: {doc.Tables.Count}')
    for ti in range(1, doc.Tables.Count + 1):
        t = doc.Tables(ti)
        print(f'\n  Table {ti}: {t.Rows.Count} 行 x {t.Columns.Count} 列')
        print(f'    整体对齐: {t.Alignment}')
        
        # 边框
        try:
            b = t.Borders
            print(f'    边框: Top={b(1).LineStyle}, Bottom={b(2).LineStyle}, Left={b(3).LineStyle}, Right={b(4).LineStyle}, InsideH={b(5).LineStyle}, InsideV={b(6).LineStyle}')
            print(f'    线宽: Top={b(1).LineWidth}, Bottom={b(2).LineWidth}, InsideH={b(5).LineWidth}, InsideV={b(6).LineWidth}')
        except Exception as e:
            print(f'    边框读取错误: {e}')
        
        # 列宽
        for ci in range(1, t.Columns.Count + 1):
            c = t.Columns(ci)
            print(f'    列{ci}: {c.Width} pt = {c.Width * 25.4 / 72:.2f} cm')
        
        # 前 2 行的字体
        for ri in range(1, min(3, t.Rows.Count + 1)):
            for ci in range(1, t.Columns.Count + 1):
                cell = t.Cell(ri, ci)
                font = cell.Range.Font
                print(f'    [{ri},{ci}] "{cell.Range.Text.strip()[:20]}" font={font.Name} size={font.Size}pt bold={font.Bold} align={cell.Range.ParagraphFormat.Alignment}')
    
    # 3. 页面设置
    ps = doc.PageSetup
    print(f'\n[3] 页面设置')
    print(f'    页面: {ps.PageWidth * 25.4/72:.2f} x {ps.PageHeight * 25.4/72:.2f} cm')
    print(f'    边距: 上{ps.TopMargin*25.4/72:.2f} 下{ps.BottomMargin*25.4/72:.2f} 左{ps.LeftMargin*25.4/72:.2f} 右{ps.RightMargin*25.4/72:.2f} cm')
    
    doc.Close(False)
    word.Quit()
    print('\n✅ 读取完成')
    
except ImportError:
    print('⚠️ pywin32 未安装，尝试安装...')
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'pywin32', '--quiet'])
    print('请重新运行脚本')
except Exception as e:
    print(f'❌ 错误: {e}')
    try:
        word.Quit()
    except:
        pass
