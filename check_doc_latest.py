#!/usr/bin/env python
# -*- coding: utf-8 -*-
import zipfile
import sys
import xml.etree.ElementTree as ET

if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

DOC_PATH = r"D:\工作\合同审查系统开发\vue-element-plus-admin-master-1.1.3\vue-element-plus-admin-master-1.1.3\Contract_review\文档中心\已生成的审查报告\创意新材料-新增需求补充协议1022_一键审查_B_20251031_133914.docx"

NS = {
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
}

def main():
    print("=" * 70)
    print("检查批注加粗情况")
    print("目标文件:", DOC_PATH)
    print("=" * 70)

    try:
        with zipfile.ZipFile(DOC_PATH, "r") as zf:
            if "word/comments.xml" not in zf.namelist():
                print("❌ 文件不包含 comments.xml")
                return

            comments_xml = zf.read("word/comments.xml")
            root = ET.fromstring(comments_xml)

            comments = root.findall(".//w:comment", NS)
            print("批注总数:", len(comments))

            author_attr = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}author"
            ai_comments = [
                c for c in comments
                if c.get(author_attr) in ("AI审查助手", "AI Review Assistant")
            ]
            print("AI批注数量:", len(ai_comments))

            if not ai_comments:
                print("⚠️ 未找到 AI审查助手/AI Review Assistant 批注")
                return

            comment = ai_comments[0]
            ET.indent(comment, space="  ")
            xml_str = ET.tostring(comment, encoding="unicode")
            print("\n第一个AI批注 XML:")
            print(xml_str)

            runs = comment.findall(".//w:r", NS)
            print("\nRun 数量:", len(runs))
            bold_runs = 0
            for idx, run in enumerate(runs, start=1):
                rPr = run.find("w:rPr", NS)
                b = run.find(".//w:b", NS)
                bcs = run.find(".//w:bCs", NS)
                fonts = run.find(".//w:rFonts", NS)
                sz = run.find(".//w:sz", NS)
                text = run.find("w:t", NS)
                txt = text.text if text is not None else ""

                if b is not None:
                    bold_runs += 1

                print(f"Run{idx}: 加粗={b is not None}, bCs={bcs is not None}, rFonts={fonts.attrib if fonts is not None else None}, sz={sz.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val') if sz is not None else None}, 文本='{txt}'")

            print("\n包含加粗的 runs:", bold_runs)

    except Exception as exc:
        print("❌ 检查失败:", exc)


if __name__ == "__main__":
    main()
