# -*- coding: utf-8 -*-
"""Markdown -> HTML -> PDF 转换脚本（零安装方案 + 图片降采样 + PDF 元数据）。

流程：
  0. 图片降采样：docs/images/ 的图缩到 ≤1400px 宽，输出到 docs/images_compressed/（不覆盖原图）
  1. 读取 Markdown 与 YAML frontmatter（标题页信息）
  2. python-markdown 转 HTML（tables / fenced_code / toc / attr_list），图片指向压缩目录
  3. 生成封面页 + 目录 + 正文 HTML，内嵌中文友好 CSS
  4. 写出中间产物 docs/项目策划设计文档.html
  5. 调用 Microsoft Edge(Chromium) headless --print-to-pdf 生成 PDF
  6. 用 pymupdf 写入 PDF 元数据

依赖：markdown、PyYAML、Pillow、pymupdf（均可 pip 安装），无需 pandoc / wkhtmltopdf。
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import markdown
import yaml

# ---------------------------------------------------------------------------
# 路径与参数
# ---------------------------------------------------------------------------
ROOT = Path(__file__).resolve().parent.parent            # 仓库根目录
MD_PATH = ROOT / "docs" / "项目策划设计文档.md"
HTML_PATH = ROOT / "docs" / "项目策划设计文档.html"
PDF_PATH = ROOT / "docs" / "蜀韵管家-智能体产品设计报告-AI旅皇联盟.pdf"
IMG_DIR = ROOT / "docs" / "images"
IMG_OUT_DIR = ROOT / "docs" / "images_compressed"

MAX_WIDTH = 1200          # 图片最大宽度（像素）
MAX_HEIGHT = 1700         # 图片最大高度（像素，兼顾整页长截图）
JPEG_QUALITY = 78         # JPG 重编码质量

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

PDF_METADATA = {
    "title": "蜀韵管家 · 智能体产品设计报告",
    "author": "AI旅皇联盟",
    "subject": "一个懂巴蜀文化的 AI 旅行智能体",
    "keywords": "蜀韵管家, AI 智能体, 女娲智能体, 文化旅游, SSE",
}

# ---------------------------------------------------------------------------
# 内嵌 CSS：保证 PDF 排版好看且中文不乱码
# ---------------------------------------------------------------------------
CSS = r"""
* { box-sizing: border-box; }
html, body { print-color-adjust: exact; -webkit-print-color-adjust: exact; }
body {
  font-family: "Source Han Serif SC","思源宋体","Noto Serif SC","Microsoft YaHei","微软雅黑",serif;
  font-size: 14px;
  line-height: 1.9;
  color: #1A1A1A;
  margin: 0;
}
@page { size: A4; margin: 2cm 2.5cm; }

p { margin: 0 0 16px; }
h1, h2, h3, h4 { font-family: "Source Han Serif SC","思源宋体","Microsoft YaHei",serif; line-height: 1.4; }
h1 { font-size: 32px; font-weight: 700; margin: 26px 0 16px; padding-bottom: 10px; border-bottom: 2px solid #8C1F28; }
h2 { font-size: 24px; font-weight: 700; margin: 22px 0 12px; padding-left: 12px; border-left: 5px solid #8C1F28; }
h3 { font-size: 19px; font-weight: 700; margin: 18px 0 10px; color: #8C1F28; }
h4 { font-size: 16px; font-weight: 700; margin: 16px 0 8px; color: #6B7280; }
a { color: #8C1F28; text-decoration: none; }

table { border-collapse: collapse; width: 100%; margin: 12px 0 20px; font-size: 13px; }
table th { background: #8C1F28; color: #fff; font-weight: 700; border: 1px solid #D4B374; padding: 8px 10px; text-align: left; }
table td { border: 1px solid #D4B374; padding: 7px 10px; }
table tr:nth-child(even) td { background: #F5F1E8; }

img { max-width: 100%; display: block; margin: 14px auto 6px; }
/* 图注：Markdown 里独占一段的 *...* 会渲染成 <p><em>..</em></p> */
p > em:only-child { display: block; text-align: center; font-style: normal; color: #6B7280; font-size: 12px; margin: 0 0 18px; }

code { font-family: "JetBrains Mono","Consolas","Courier New",monospace; background: #F5F1E8; padding: 1px 5px; border-radius: 3px; font-size: 12.5px; }
pre { background: #F5F1E8; padding: 12px 14px; border: 1px solid #E7DFCE; border-radius: 6px; overflow-x: auto; }
pre code { background: transparent; padding: 0; }
blockquote { margin: 12px 0; padding: 8px 16px; border-left: 4px solid #D4B374; background: #FBF8F2; color: #6B7280; }
hr { border: none; border-top: 1px solid #D4B374; margin: 20px 0; }

/* 封面页 */
.cover { height: 24cm; display: flex; align-items: center; justify-content: center; text-align: center; }
.cover-mark { font-size: 15px; letter-spacing: 0.5em; color: #D4B374; margin-bottom: 26px; }
.cover-title { font-size: 40px; font-weight: 700; color: #8C1F28; border: none; margin: 0 0 20px; padding: 0; }
.cover-subtitle { font-size: 22px; color: #1A1A1A; margin: 0 0 16px; }
.cover-competition { font-size: 15px; color: #6B7280; margin: 0 auto 26px; max-width: 22cm; line-height: 1.7; }
.cover-rule { width: 120px; height: 3px; background: linear-gradient(90deg, #8C1F28, #D4B374); margin: 0 auto 30px; }
.cover-author { font-size: 18px; color: #1A1A1A; margin-bottom: 12px; }
.cover-date { font-size: 16px; color: #6B7280; }

/* 目录页 */
.toc-title { border: none; border-bottom: 2px solid #8C1F28; padding: 0 0 8px; }
.toc ul { list-style: none; padding-left: 18px; margin: 0; }
.toc > ul { padding-left: 0; }
.toc li { margin: 3px 0; font-size: 13.5px; }
.toc a { color: #1A1A1A; }

/* 分页与避免断裂 */
.page-break { page-break-after: always; break-after: page; }
img, table, pre, blockquote { page-break-inside: avoid; }
h1, h2, h3 { page-break-after: avoid; }
"""


def human(nbytes: float) -> str:
    """把字节数格式化为易读字符串。"""
    for unit in ["B", "KB", "MB", "GB"]:
        if nbytes < 1024:
            return f"{nbytes:.1f} {unit}"
        nbytes /= 1024
    return f"{nbytes:.1f} TB"


def find_edge() -> str:
    """定位 msedge.exe。"""
    for p in EDGE_CANDIDATES:
        if Path(p).exists():
            return p
    found = shutil.which("msedge") or shutil.which("msedge.exe")
    if found:
        return found
    raise FileNotFoundError("未找到 Microsoft Edge(msedge.exe)，请检查安装路径")


def split_frontmatter(text: str):
    """拆分 YAML frontmatter 与正文，返回 (meta_dict, body)。"""
    m = re.match(r"^---[ \t]*\n(.*?)\n---[ \t]*\n", text, re.S)
    if not m:
        return {}, text
    return (yaml.safe_load(m.group(1)) or {}), text[m.end():]


def build_cover(meta: dict) -> str:
    """根据 frontmatter 生成封面页 HTML。"""
    title = str(meta.get("title", "") or "")
    subtitle = str(meta.get("subtitle", "") or "")
    competition = str(meta.get("competition", "") or "")
    author = str(meta.get("author", "") or "")
    return (
        '<section class="cover"><div class="cover-inner">'
        '<div class="cover-mark">蜀 韵 管 家</div>'
        f'<h1 class="cover-title">{title}</h1>'
        f'<div class="cover-subtitle">{subtitle}</div>'
        '<div class="cover-rule"></div>'
        f'<div class="cover-competition">{competition}</div>'
        f'<div class="cover-author">{author}</div>'
        '</div></section><div class="page-break"></div>'
    )


def compress_images() -> dict:
    """把 docs/images/ 的图片降采样到 docs/images_compressed/（不覆盖原图）。"""
    from PIL import Image

    if IMG_OUT_DIR.exists():
        shutil.rmtree(IMG_OUT_DIR)
    IMG_OUT_DIR.mkdir(parents=True, exist_ok=True)

    files = [p for p in IMG_DIR.iterdir()
             if p.is_file() and p.suffix.lower() in (".png", ".jpg", ".jpeg")]

    total_before = 0
    total_after = 0
    resized = 0
    untouched = 0
    failed = 0
    mapping = {}

    for p in files:
        before = p.stat().st_size
        total_before += before
        ext = p.suffix.lower()
        try:
            with Image.open(p) as im:
                scale = min(1.0, MAX_WIDTH / im.width, MAX_HEIGHT / im.height)
                if scale < 1.0:
                    im = im.resize((round(im.width * scale), round(im.height * scale)), Image.Resampling.LANCZOS)
                    resized += 1
                else:
                    untouched += 1

                has_alpha = (im.mode in ("RGBA", "LA")) or (im.mode == "P" and "transparency" in im.info)
                if ext in (".jpg", ".jpeg") or not has_alpha:
                    # 照片类 / 无透明通道 → JPEG，体积最小
                    if im.mode not in ("RGB", "L"):
                        im = im.convert("RGB")
                    out_name = p.stem + ".jpg"
                    im.save(IMG_OUT_DIR / out_name, format="JPEG", quality=JPEG_QUALITY, optimize=True)
                else:
                    # 需要透明通道 → 保留 PNG
                    out_name = p.stem + ".png"
                    im.save(IMG_OUT_DIR / out_name, format="PNG", optimize=True)

            mapping[p.name] = out_name
            total_after += (IMG_OUT_DIR / out_name).stat().st_size
        except Exception as e:  # 任何异常都退化为原样拷贝，保证不缺图
            shutil.copy(str(p), str(IMG_OUT_DIR / p.name))
            mapping[p.name] = p.name
            total_after += (IMG_OUT_DIR / p.name).stat().st_size
            failed += 1
            print(f"  [warn] 处理失败，已原样拷贝: {p.name} ({e})")

    return {
        "count": len(files),
        "resized": resized,
        "untouched": untouched,
        "failed": failed,
        "before": total_before,
        "after": total_after,
        "mapping": mapping,
    }


def set_pdf_metadata() -> bool:
    """用 pymupdf 给 PDF 写入元数据。"""
    try:
        import pymupdf
    except Exception as e:
        print(f"[warn] 未安装 pymupdf，跳过元数据写入: {e}")
        return False

    doc = pymupdf.open(str(PDF_PATH))
    doc.set_metadata(PDF_METADATA)
    try:
        doc.saveIncr()                       # 增量保存，快
    except Exception:
        tmp = str(PDF_PATH) + ".tmp"
        doc.save(tmp, garbage=3, deflate=True)
        doc.close()
        os.replace(tmp, str(PDF_PATH))
        return True
    doc.close()
    return True


def main() -> int:
    if not MD_PATH.exists():
        print(f"[FAIL] 找不到源文件: {MD_PATH}")
        return 1

    # 0) 图片降采样
    print("[0/4] 图片降采样 ...")
    st = compress_images()
    print(f"      共 {st['count']} 张（缩放 {st['resized']} / 原样 {st['untouched']} / 失败 {st['failed']}）："
          f"{human(st['before'])} -> {human(st['after'])}")

    # 1) 读 Markdown，图片改指向压缩目录
    text = MD_PATH.read_text(encoding="utf-8")
    meta, body = split_frontmatter(text)
    # 图片改指向压缩目录：按“原文件名 -> 压缩文件名”逐张替换，兼容扩展名变化
    for orig_name, new_name in st["mapping"].items():
        body = body.replace(f"./images/{orig_name}", f"./images_compressed/{new_name}")

    # 2) Markdown -> HTML
    md = markdown.Markdown(extensions=["tables", "fenced_code", "toc", "attr_list"])
    html_body = md.convert(body)

    toc_html = getattr(md, "toc", "") or ""
    if toc_html:
        toc_block = (
            '<section class="toc"><h2 class="toc-title">目 录</h2>'
            + toc_html
            + '</section><div class="page-break"></div>'
        )
    else:
        toc_block = ""

    cover = build_cover(meta)
    title_tag = str(meta.get("title", "项目策划设计文档") or "项目策划设计文档")

    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>{title_tag}</title>
<style>{CSS}</style>
</head>
<body>
{cover}
{toc_block}
<main class="doc">
{html_body}
</main>
</body>
</html>
"""
    HTML_PATH.write_text(html, encoding="utf-8")
    print(f"[2/4] HTML 已生成: {HTML_PATH}  ({HTML_PATH.stat().st_size / 1024:.1f} KB)")

    # 3) Edge 无头打印 PDF
    edge = find_edge()
    tmp_profile = Path(tempfile.mkdtemp(prefix="edge-pdf-"))
    cmd = [
        edge,
        "--headless=new",
        "--disable-gpu",
        "--no-first-run",
        "--no-default-browser-check",
        "--no-pdf-header-footer",
        f"--user-data-dir={tmp_profile}",
        f"--print-to-pdf={PDF_PATH}",
        HTML_PATH.resolve().as_uri(),
    ]
    print("[3/4] 调用 Edge 无头打印 PDF ...")
    proc = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace")
    shutil.rmtree(tmp_profile, ignore_errors=True)

    err = (proc.stderr or "").strip()
    if err:
        tail = "\n".join(err.splitlines()[-6:])
        print("[edge stderr]\n" + tail)

    if not (PDF_PATH.exists() and PDF_PATH.stat().st_size > 0):
        print(f"[FAIL] PDF 未生成，Edge 退出码 = {proc.returncode}")
        return 1

    # 4) 写入 PDF 元数据
    print("[4/4] 写入 PDF 元数据 ...")
    if set_pdf_metadata():
        print("      Title / Author / Subject / Keywords 已写入")
    else:
        print("      元数据未写入（可忽略）")

    print(f"[OK] PDF 生成成功: {PDF_PATH}  ({human(PDF_PATH.stat().st_size)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
