-- ============================================================================
-- 蜀韵·智游（shuyun-travel）景点视频链接回填脚本  update_scenic_video.sql
-- 由 tools/excel2sql.py 自动生成，请勿手工修改。
-- 生成时间：2026-09-21 16:03:57
-- 来源：shuyun-data/01-景点.xlsx 的「视频网址」列（R 列，按「名称」匹配）
-- 用途：为 scenic_spot.video_url 回填景点视频链接（B 站 / 央视网等）。
--
-- 前置条件：scenic_spot 表须已存在 video_url 字段（见 db/fix_columns.sql，或手工执行：
--   ALTER TABLE scenic_spot ADD COLUMN video_url VARCHAR(500) DEFAULT NULL COMMENT '景点视频链接' AFTER recommend_reason;）
--
-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 shuyun < update_scenic_video.sql
-- 重新生成：python tools/excel2sql.py
--
-- 记录数统计：
--   scenic_spot.video_url 回填 100 条
--
-- 说明：本脚本仅 UPDATE，不新增 / 不删除任何行；按 name 匹配，未命中（名称不一致）
--       的行不会被修改，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1wC4y1t7us/' WHERE `name` = '都江堰';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV14yLG6REtj/' WHERE `name` = '青城山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1zG4y1D7NB/' WHERE `name` = '成都大熊猫繁育研究基地';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1NehG64Ej2/' WHERE `name` = '成都武侯祠博物馆';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1yrDnYAEyj/' WHERE `name` = '成都杜甫草堂博物馆';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV12hCLBbEK6/' WHERE `name` = '金沙遗址博物馆';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1uz4y1g7VH/' WHERE `name` = '宽窄巷子';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1XF411k7pZ/' WHERE `name` = '锦里古街';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1SSRAYBE3T/' WHERE `name` = '文殊院';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1y44y1f7Rq/' WHERE `name` = '西岭雪山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1P84y1N7cj/' WHERE `name` = '安仁古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1v54y1A7WF/' WHERE `name` = '黄龙溪古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1eraGzREvx/' WHERE `name` = '街子古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1hmaGzDEUQ/' WHERE `name` = '平乐古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1bV411b78x/' WHERE `name` = '洛带古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1NV4y1G7Jm/' WHERE `name` = '五凤溪古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1EKtKe3EWZ/' WHERE `name` = '天台山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1rv4y1n7ZG/' WHERE `name` = '三圣花乡';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Kz4y127NY/' WHERE `name` = '花舞人间';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1SPKp68EEK/' WHERE `name` = '成都欢乐谷';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1p1mvBxEUD/' WHERE `name` = '宝光寺·桂湖';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV19t4y1M7LF/' WHERE `name` = '崇州竹艺村';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1bEe6zwEMU/' WHERE `name` = '九寨沟风景名胜区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1NDYqzSEoa/' WHERE `name` = '黄龙风景名胜区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1w841127TG/' WHERE `name` = '稻城亚丁';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Lg4y1w71u/' WHERE `name` = '四姑娘山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV17WQHYtE9Y/' WHERE `name` = '海螺沟冰川森林公园';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV15c411M7de/' WHERE `name` = '康定情歌（木格措）风景区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1wt421j7Ei/' WHERE `name` = '色达五明佛学院';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1veh36qEvK/' WHERE `name` = '新都桥';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1KN4y177vk/' WHERE `name` = '塔公草原';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1q4421D7ZB/' WHERE `name` = '木雅圣地';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1DT4y1f7Ms/' WHERE `name` = '达古冰川';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1o8pTzsEvc/' WHERE `name` = '毕棚沟';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Xk4y1Y7uw/' WHERE `name` = '奶子沟彩林';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1c94y1s7T2/' WHERE `name` = '卓克基土司官寨';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1XEeg6zE5G/' WHERE `name` = '甲居藏寨';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1nA1gYDEHC/' WHERE `name` = '泸定桥';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1LhyaBtEQ5/' WHERE `name` = '德格印经院';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV18z421Y7za/' WHERE `name` = '伍须海';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV17VL46VEHw/' WHERE `name` = '措普沟';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1oQ3mzGEii/' WHERE `name` = '花湖生态旅游区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Ci4y1g7ja/' WHERE `name` = '黄河九曲第一湾';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1e9VvzgEdQ/' WHERE `name` = '若尔盖草原';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1VSYr6dEnr/' WHERE `name` = '松潘古城';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Agg36aEX5/' WHERE `name` = '莲宝叶则·石头山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Bt4y1K7yv/' WHERE `name` = '亚拉雪山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1E784e3EtF/' WHERE `name` = '八美墨石公园';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1J72pYXEAk/' WHERE `name` = '甘孜格萨尔王城';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1pATpzNERw/' WHERE `name` = '巴格嘛呢石经墙';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1iu411r7iU/' WHERE `name` = '峨眉山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1yb411q7Fz/' WHERE `name` = '乐山大佛';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1RsxpeLEpu/' WHERE `name` = '蜀南竹海';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1quewe5EdG/' WHERE `name` = '自贡恐龙博物馆';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1F2eY6SEZD/' WHERE `name` = '李庄古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1tz421h74e/' WHERE `name` = '兴文石海';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1PM411B7Lz/' WHERE `name` = '中华彩灯大世界';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1ZWzRYpEMX/' WHERE `name` = '燊海井';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1U1tBzVE1T/' WHERE `name` = '泸州老窖旅游区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Tt421L7Xw/' WHERE `name` = '尧坝古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1En2gBkEhD/' WHERE `name` = '罗城古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1hw411f7xp/' WHERE `name` = '柳江古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Cb7Y6VEGj/' WHERE `name` = '瓦屋山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Pb4y1U7Mw/' WHERE `name` = '三苏祠';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1tXzUBUE9B/' WHERE `name` = '蒙顶山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1iJ4m1E77k/' WHERE `name` = '碧峰峡';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV19a4y1N7Cj/' WHERE `name` = '上里古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1TV411B7Za/' WHERE `name` = '中国死海';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1aLNi6iEid/' WHERE `name` = '安岳石刻·圆觉洞';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1334y1T7mX/' WHERE `name` = '陈毅故里';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1314y137Se/' WHERE `name` = '隆昌石牌坊';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1JFeb6vEzS/' WHERE `name` = '嘉阳·桫椤湖';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1gy4y167yH/' WHERE `name` = '剑门关';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1DELVzrEgp/' WHERE `name` = '昭化古城';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1tACvBnE1V/' WHERE `name` = '皇泽寺';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1nkAEz7ERb/' WHERE `name` = '明月峡';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Dr4y1Y7zq/' WHERE `name` = '唐家河';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV13N4y1C7ei/' WHERE `name` = '曾家山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1g7421N7Wx/' WHERE `name` = '阆中古城';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1vM4y12785/' WHERE `name` = '朱德故里';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1fV411B7mm/' WHERE `name` = '七曲山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV17i421e7G6/' WHERE `name` = '平武报恩寺';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1AK4y1g7Uy/' WHERE `name` = '窦圌山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1kzQwY1ExY/' WHERE `name` = '九皇山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1Ys4y1c7rn/' WHERE `name` = '江油李白故居';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1EcxizUEY3/' WHERE `name` = '两弹城';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1dH4y1X7KG/' WHERE `name` = '光雾山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1P44y1w7bq/' WHERE `name` = '诺水河';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1dx411D7u5/' WHERE `name` = '恩阳古镇';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1bD4y1k7Mg/' WHERE `name` = '巴山大峡谷';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1fY4y1D7iB/' WHERE `name` = '八台山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1uT411e7aC/' WHERE `name` = '华蓥山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1qCN4ziEx5/' WHERE `name` = '邓小平故里';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1JPSuBLETk/' WHERE `name` = '汶川特别旅游区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1b44y1c7mK/' WHERE `name` = '北川羌城旅游区';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV15N411L7tu/' WHERE `name` = '泸沽湖';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV19h21YGESx/' WHERE `name` = '邛海泸山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV13P411y71h/' WHERE `name` = '螺髻山';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV1N6Y46iEVx/' WHERE `name` = '会理古城';
UPDATE `scenic_spot` SET `video_url` = 'https://www.bilibili.com/video/BV16d4y1W7EX/' WHERE `name` = '彝海';