# -*- coding: utf-8 -*-
"""
从 Noto Serif SC（思源宋体的开源版，SIL OFL 1.1）提取「蜀韵智游」字形轮廓，
生成 shuyun-vue/src/components/home/title-paths.ts，供首页主标题做 SVG path 排版 + 描边书写动画。

依赖：pip install fonttools
用法：python tools/extract-glyphs.py
"""
import os

from fontTools.misc.roundTools import otRound
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

# ---------- 配置 ----------
FONT = r'C:\Windows\Fonts\NotoSerifSC-VF.ttf'
WEIGHT = 900
TEXT = '蜀韵智游'
OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    'src', 'components', 'home', 'title-paths.ts',
)

font = TTFont(FONT)
if 'fvar' in font:
    # 可变字体：实例化到 Heavy
    instantiateVariableFont(font, {'wght': WEIGHT}, inplace=True)

glyphset = font.getGlyphSet()
cmap = font.getBestCmap()


def ntos(v):
    """坐标取整，显著减小 path 体积（1000 upem 下视觉无损）"""
    return str(otRound(v))


items = []
for ch in TEXT:
    gname = cmap[ord(ch)]
    glyph = glyphset[gname]

    # 1) 字形外框（字体坐标，Y 向上）
    bounds_pen = BoundsPen(glyphset)
    glyph.draw(bounds_pen)
    x0, y0, x1, y1 = bounds_pen.bounds
    width = otRound(x1 - x0)
    height = otRound(y1 - y0)

    # 2) 轮廓路径（字体原始坐标；SVGPathPen 不做 Y 翻转，需自己 scale(1,-1)）
    path_pen = SVGPathPen(glyphset, ntos=ntos)
    glyph.draw(path_pen)
    d = path_pen.getCommands()

    # 3) 变换：先平移到原点再翻转 Y —— translate(-x0, y1) scale(1,-1)
    items.append(
        {
            'char': ch,
            'viewBox': '0 0 %d %d' % (width, height),
            'transform': 'translate(%d %d) scale(1 -1)' % (-x0, y1),
            'd': d,
        }
    )
    print('%s %-8s viewBox=%dx%d  d=%d chars' % (ch, gname, width, height, len(d)))

# ---------- 输出 TS ----------
L = []
L.append('/**')
L.append(' * 首页主标题「蜀韵智游」字形轮廓（SVG path）')
L.append(' *')
L.append(' * 字体来源：Noto Serif SC（思源宋体的开源版，SIL OFL 1.1），Variable Font wght=%d' % WEIGHT)
L.append(' * 生成脚本：shuyun-vue/tools/extract-glyphs.py（换字体只需改脚本重跑，不要手改本文件）')
L.append(' *')
L.append(' * 坐标系：字体 Y 向上、SVG Y 向下，因此每个字都带一个 transform 做翻转；')
L.append(' *        viewBox 按各字自身外框归一化，保证四个字的光学大小一致。')
L.append(' */')
L.append('')
L.append('export interface TitleGlyph {')
L.append('  /** 对应汉字 */')
L.append('  char: string')
L.append('  /** SVG viewBox（按字形外框归一化） */')
L.append('  viewBox: string')
L.append('  /** 把字体坐标（Y 向上）转为 SVG 坐标（Y 向下）的变换 */')
L.append('  transform: string')
L.append('  /** SVG path 数据（字体原始坐标，需配合 transform 使用） */')
L.append('  d: string')
L.append('}')
L.append('')
L.append('export const TITLE_GLYPHS: TitleGlyph[] = [')
for it in items:
    L.append('  {')
    L.append("    char: '%s'," % it['char'])
    L.append("    viewBox: '%s'," % it['viewBox'])
    L.append("    transform: '%s'," % it['transform'])
    L.append("    d: '%s'," % it['d'])
    L.append('  },')
L.append(']')
L.append('')

with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(L))

print('written ->', OUT)
