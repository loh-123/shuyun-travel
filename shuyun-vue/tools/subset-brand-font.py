# -*- coding: utf-8 -*-
"""
从书法艺术字体生成品牌字子集 woff2（只保留品牌文案用到的字）。

为什么做子集：全量中文书法字体动辄 1.5MB+，而它只用在
导航栏 / 首屏 / 登录页 / 页脚 / 后台的品牌名上，文案是固定字符串；
子集化后只有几 KB，全站加载几乎无成本。

依赖：pip install fonttools brotli
用法：python tools/subset-brand-font.py
"""
import os

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_FONT_DIR = os.path.join(ROOT, 'src', 'assets', 'fonts')
OUT_DIR = os.path.join(ROOT, 'public', 'fonts')

# ---------- 配置 ----------
# 源字体：优先用 SRC_FONT；若该路径不存在，则自动取 src/assets/fonts 下的第一个 ttf/otf
SRC_FONT = r'C:\Windows\Fonts\STXINGKA.TTF'  # 华文行楷（书写体）
OUT_NAME = 'brand-xingkai.woff2'

# 品牌文案用到的字：蜀韵·智游 / 蜀韵·智游后台
BRAND_TEXT = '蜀韵智游后台'

chars = set(BRAND_TEXT)
# 保底：ASCII 可见字符（英文品牌名等）+ 常用中文标点
chars.update(chr(c) for c in range(0x20, 0x7F))
chars.update('·，。、；：？！“”‘’（）《》〈〉【】〔〕—…「」『』￥℃±×÷％°№')

# 定位源字体
src = SRC_FONT if os.path.isfile(SRC_FONT) else None
if src is None and os.path.isdir(ASSETS_FONT_DIR):
    for name in sorted(os.listdir(ASSETS_FONT_DIR)):
        if name.lower().endswith(('.ttf', '.otf')):
            src = os.path.join(ASSETS_FONT_DIR, name)
            break
if src is None:
    raise SystemExit('没找到源字体：请把字体放进 src/assets/fonts/，或修改脚本里的 SRC_FONT')

font = TTFont(src)
cmap = font.getBestCmap()
missing = [c for c in BRAND_TEXT if ord(c) not in cmap]

print('source  : %s  (%.2f MB)' % (os.path.basename(src), os.path.getsize(src) / 1024 / 1024))
print('family  :', font['name'].getDebugName(1))
print('missing :', missing or '无，品牌字全部覆盖')

options = Options()
options.flavor = 'woff2'  # 直接输出 woff2
options.layout_features = ['*']  # 保留 OpenType 特性
options.drop_tables += ['DSIG']
options.notdef_outline = False
options.recalc_bounds = False

subsetter = Subsetter(options=options)
subsetter.populate(text=''.join(sorted(chars)))
subsetter.subset(font)

out = os.path.join(OUT_DIR, OUT_NAME)
os.makedirs(OUT_DIR, exist_ok=True)
font.save(out)

print('written :', out)
print('size    : %.1f KB' % (os.path.getsize(out) / 1024))
