# 本地字体目录

默认**全部走系统已安装字体**（`local()`），因此不会产生 404，也不会拖慢首屏；
系统没装时自动回退到 `variables.scss` / `fonts.scss` 里的一串后备字体。

## 想用本地字体文件时

把文件放到这个目录，然后打开 `src/styles/fonts.scss`，**取消对应 `url(...)` 行的注释**。

| 文件名 | 对应 @font-face | 用途 |
| --- | --- | --- |
| `SourceHanSerifSC-Heavy.woff2` | `Source Han Serif SC` / 900 | 中文标题（思源宋体 Heavy） |
| `SourceHanSerifSC-Regular.woff2` | `Source Han Serif SC` / 400 | 中文正文备选 |
| `LXGWWenKai-Regular.woff2` | `LXGW WenKai` / 400 | 中文正文（霞鹜文楷） |
| `PlayfairDisplay-Variable.woff2` | `Playfair Display` | 英文标题 |
| `Inter-Variable.woff2` | `Inter` | 英文正文 |

> 英文标题/正文目前也通过 `index.html` 里的 Google Fonts `<link>` 加载（有网即可用）。

## 重要：中文字体一定要做子集化

思源宋体 / 霞鹜文楷全量包 **10MB 以上**，直接引入会严重拖垮 LCP。
建议用 `fonttools` 的 `pyftsubset` 按用到的字做子集：

```bash
pip install fonttools brotli
pyftsubset LXGWWenKai-Regular.ttf --text-file=chars.txt --flavor=woff2 --output-file=LXGWWenKai-Regular.woff2
```

`chars.txt` 里放全站会用到的汉字（可先用脚本扫一遍 `.vue` 提取）。
