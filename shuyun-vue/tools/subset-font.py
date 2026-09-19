# -*- coding: utf-8 -*-
"""
从 LXGW WenKai 全量 TTF 生成项目子集（woff2）。

中文全量字体 >20MB，直接给浏览器会严重拖垮首屏；这里扫描项目源码与 SQL 种子数据，
只保留实际出现过的字符（外加 ASCII 与常用中文标点保底），体积可降到几百 KB。

依赖：pip install fonttools brotli
用法：python tools/subset-font.py
"""
import os

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont

ROOT = r'E:\traval program\shuyun-travel'
SRC_TTF = os.path.join(ROOT, 'shuyun-web', 'target', 'LXGWWenKai-Regular.ttf')
OUT = os.path.join(ROOT, 'shuyun-vue', 'public', 'fonts', 'LXGWWenKai-Regular.woff2')

# 需要扫描的目录与后缀
SCAN = [
    (os.path.join(ROOT, 'shuyun-vue', 'src'), ('.vue', '.ts', '.scss', '.css', '.html')),
    (os.path.join(ROOT, 'shuyun-vue'), ('.html',)),
    (os.path.join(ROOT, 'shuyun-web', 'src', 'main', 'resources'), ('.sql', '.yml')),
]

SKIP_DIRS = ('node_modules', 'dist', '.git', 'target')

chars = set()

for base, exts in SCAN:
    for dirpath, dirnames, files in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in files:
            if name.lower().endswith(exts):
                path = os.path.join(dirpath, name)
                try:
                    with open(path, encoding='utf-8', errors='ignore') as f:
                        chars.update(f.read())
                except OSError:
                    pass

# 保底：ASCII 可见字符 + 常用中文标点，避免动态内容（用户名等）出现豆腐块
chars.update(chr(c) for c in range(0x20, 0x7F))
chars.update('，。、；：？！“”‘’（）《》〈〉【】〔〕—…·「」『』￥℃±×÷≈％°№')

text = ''.join(sorted(chars))
print('unique chars =', len(text))

font = TTFont(SRC_TTF)
options = Options()
options.flavor = 'woff2'  # 直接输出 woff2
options.layout_features = ['*']  # 保留 OpenType 特性
options.drop_tables += ['DSIG']
options.notdef_outline = False
options.recalc_bounds = False

subsetter = Subsetter(options=options)
subsetter.populate(text=text)
subsetter.subset(font)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
font.save(OUT)

print('written ->', OUT)
print('size    =', round(os.path.getsize(OUT) / 1024, 1), 'KB')
