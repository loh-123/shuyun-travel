# -*- coding: utf-8 -*-
"""
从艺术字体提取「蜀韵智游」字形轮廓，并按「连笔字标」布局生成
shuyun-vue/src/components/home/title-paths.ts。

为什么要在这里做布局，而不是丢给 CSS：
  1. 连笔咬合需要知道**每个字的墨迹宽度**（字体的真实轮廓），CSS 拿不到；
  2. 四个字必须共用同一坐标系（同一 viewBox / 同一 userSpaceOnUse 渐变），
     交叠处取到完全相同的颜色，才能做到零接缝；
  3. 金属浮雕要分层（挤出 / 高光边 / 金属面），分层必须在一个 SVG 内才能保证
     相邻字的前后遮挡关系正确。

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
FONT = r'C:\Windows\Fonts\STXINGKA.TTF'  # 华文行楷（艺术书写体）
FONT_LABEL = '华文行楷（STXingkai）'
WEIGHT = 900  # 仅可变字体生效
TEXT = '蜀韵智游'

# 连笔咬合：相邻两字「墨迹」之间的平均间隙（字体单位）
#   < 0 咬合（笔画相接），= 0 刚好相切，> 0 留缝
INK_GAP = -6
# 画布四周留白（字体单位）：给描边 / 浮雕偏移留出空间，避免被 viewBox 裁掉
PAD = 24
# 逐字垂直起伏（字体单位，正数 = 抬高）：四个字里抬一个，视觉上就有高低错落
Y_RAISE = [0, 170, 0, 0]

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    'src', 'components', 'home', 'title-paths.ts',
)

font = TTFont(FONT)
if 'fvar' in font:
    instantiateVariableFont(font, {'wght': WEIGHT}, inplace=True)

glyphset = font.getGlyphSet()
cmap = font.getBestCmap()


def ntos(v):
    """坐标取整，显著减小 path 体积（1000 upem 下视觉无损）"""
    return str(otRound(v))


# ---------- 1) 量出每个字的墨迹外框（字体坐标，Y 向上） ----------
metrics = []
for ch in TEXT:
    gname = cmap[ord(ch)]
    glyph = glyphset[gname]
    pen = BoundsPen(glyphset)
    glyph.draw(pen)
    x0, y0, x1, y1 = pen.bounds
    metrics.append(
        {
            'char': ch,
            'gname': gname,
            'glyph': glyph,
            'x0': x0,
            'x1': x1,
            'cx': (x0 + x1) / 2.0,  # 墨迹水平中心
            'ink_w': x1 - x0,
            'y0': y0,
            'y1': y1,
        }
    )

# ---------- 2) 连笔布局：把各字墨迹中心摆到等步长网格上 ----------
# 相邻墨迹间隙 = 步长 -（左字墨宽 + 右字墨宽）/2，取其平均等于 INK_GAP
pair_w = [
    (metrics[k]['ink_w'] + metrics[k + 1]['ink_w']) / 2.0 for k in range(len(metrics) - 1)
]
step = sum(pair_w) / len(pair_w) + INK_GAP

# 垂直方向：四字共用一条基线（取并集下沿），保证大小与基线一致
Y0 = min(m['y0'] for m in metrics)
Y1 = max(m['y1'] for m in metrics)
# 上留白要额外容纳最大抬升量，否则抬高后的字会被 viewBox 裁掉
PAD_TOP = PAD + max(Y_RAISE)
canvas_h = otRound(Y1 - Y0) + PAD_TOP + PAD

# 水平方向：第一个字左留 PAD，之后按 step 递进
pos_x = []
cur = PAD + metrics[0]['ink_w'] / 2.0
for m in metrics:
    pos_x.append(cur)
    cur += step
canvas_w = otRound(pos_x[-1] + metrics[-1]['ink_w'] / 2.0 + PAD)

# ---------- 3) 逐字输出路径 + 摆放变换 ----------
items = []
for idx, (m, cx_target) in enumerate(zip(metrics, pos_x)):
    path_pen = SVGPathPen(glyphset, ntos=ntos)
    m['glyph'].draw(path_pen)
    d = path_pen.getCommands()

    # 字体坐标 Y 向上 → SVG Y 向下：垂直翻转，并整体摆到公共画布上
    dx = cx_target - m['cx']
    dy = Y1 + PAD_TOP - Y_RAISE[idx]
    transform = 'translate(%s %s) scale(1 -1)' % (ntos(dx), ntos(dy))

    items.append({'char': m['char'], 'transform': transform, 'd': d})
    print(
        '%s %-10s ink_w=%-5d raise=%d'
        % (m['char'], m['gname'], otRound(m['ink_w']), Y_RAISE[idx])
    )

print('canvas %dx%d  平均墨迹间隙=%.1f' % (canvas_w, canvas_h, step - sum(pair_w) / len(pair_w)))

# ---------- 输出 TS ----------
L = []
L.append('/**')
L.append(' * 首页主标题「蜀韵智游」字形轮廓（SVG path）')
L.append(' *')
L.append(' * 字体来源：%s' % FONT_LABEL)
L.append(' * 生成脚本：shuyun-vue/tools/extract-glyphs.py（换字体只需改脚本重跑，不要手改本文件）')
L.append(' *')
L.append(' * 布局：脚本已按「连笔字标」把四个字摆到同一公共画布上（等步长网格 + 平均墨迹间隙 %.0f），' % INK_GAP)
L.append(' *        因此四字共用同一 viewBox 与同一基线，交叠处颜色天然一致、无接缝。')
L.append(' * 坐标系：字体 Y 向上、SVG Y 向下，故每个字的 transform 内含一次 scale(1 -1) 翻转。')
L.append(' */')
L.append('')
L.append('export interface TitleGlyph {')
L.append('  /** 对应汉字 */')
L.append('  char: string')
L.append('  /** 把该字摆放到公共画布上的变换（含 Y 翻转） */')
L.append('  transform: string')
L.append('  /** SVG path 数据（字体原始坐标，需配合 transform 使用） */')
L.append('  d: string')
L.append('}')
L.append('')
L.append('/** 公共画布（四字共用），形如 "0 0 3006 906" */')
L.append("export const TITLE_VIEW_BOX = '0 0 %d %d'" % (canvas_w, canvas_h))
L.append('')
L.append('export const TITLE_GLYPHS: TitleGlyph[] = [')
for it in items:
    L.append('  {')
    L.append("    char: '%s'," % it['char'])
    L.append("    transform: '%s'," % it['transform'])
    L.append("    d: '%s'," % it['d'])
    L.append('  },')
L.append(']')
L.append('')

with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(L))

print('written ->', OUT)
