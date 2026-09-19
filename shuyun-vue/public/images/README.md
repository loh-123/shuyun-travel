# 图片资源目录

把首页需要的**实景图**放在这里（推荐 **WebP**，每张准备 1920 / 2560 两档宽度）。

## 需要准备的文件

| 文件名 | 用在哪 | 建议尺寸 | 内容建议 |
| --- | --- | --- | --- |
| `hero-jiuzhaigou.webp` | 首屏背景 | 2560×1440 | 九寨沟 / 峨眉云海 |
| `hero-jiuzhaigou@2x.webp` | 首屏 2x | 3840×2160 | 同上，高清版 |
| `emei-cloud.webp` | 第二屏（数据概览） | 2560×1440 | 峨眉云海 / 青城山 |
| `season-dujiangyan.webp` | 第三屏 · 春 | 2560×1440 | 都江堰 |
| `season-jiuzhaigou.webp` | 第三屏 · 夏 | 2560×1440 | 九寨沟 |
| `season-daocheng.webp` | 第三屏 · 秋 | 2560×1440 | 稻城亚丁 |
| `season-xiling.webp` | 第三屏 · 冬 | 2560×1440 | 西岭雪山 |
| `ink-night.webp` | 第五屏（AI 管家） | 2560×1440 | 蜀地夜色 / 山水水墨 |

## 怎么切换成本地图

目前代码里用的是 Unsplash 实拍占位图（可直接跑起来看效果）。
放好图片后，把对应组件里的常量改成本地路径即可：

`src/components/home/HeroSection.vue`
```ts
const HERO_SRC = '/images/hero-jiuzhaigou.webp'
const HERO_SRC_2X = '/images/hero-jiuzhaigou@2x.webp'
```

`src/components/home/DataSection.vue` → `BG_SRC` / `BG_SRC_2X`
`src/components/home/AiSection.vue` → `BG_SRC` / `BG_SRC_2X`
`src/components/home/SeasonSection.vue` → `SEASONS[]` 每一项的 `img` / `img2x`

> 放在 `public/` 下的文件会原样发布，路径以 `/` 开头（如 `/images/xxx.webp`）。
