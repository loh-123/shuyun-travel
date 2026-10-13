-- ============================================================================
-- 蜀韵·智游（shuyun-travel）传承人数据导入脚本（upsert，非破坏性导入）
-- 由 tools/person2sql.py 自动生成，请勿手工修改。
-- 生成时间：2026-09-20 21:52:14
-- 输入：shuyun-data/05-传承人.xlsx
-- 输出：shuyun-web/src/main/resources/db/person.sql
--
-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 shuyun < person.sql
-- 重新生成：python tools/person2sql.py
--
-- 记录数统计：
--   culture_person   24 条
--
-- 说明：本脚本不 DROP / 不 TRUNCATE，仅 CREATE TABLE IF NOT EXISTS + upsert 导入，
--       可重复执行；culture_id 由文末 UPDATE 按 culture.name 自动回填。
-- ============================================================================

SET NAMES utf8mb4;

USE `shuyun`;

-- ----------------------------------------------------------------------------
-- 1. 传承人表：culture_person
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `culture_person` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name`         VARCHAR(50)  NOT NULL                COMMENT '姓名',
  `brief`        TEXT                                 COMMENT '简介',
  `culture_name` VARCHAR(50)           DEFAULT NULL   COMMENT '代表文化（Excel 原字段）',
  `culture_id`   BIGINT                DEFAULT NULL   COMMENT '关联到 culture 表的 id（后端自动匹配）',
  `video_url`    VARCHAR(255)          DEFAULT NULL   COMMENT '视频链接',
  `avatar`       VARCHAR(255)          DEFAULT NULL   COMMENT '头像路径，如 /images/person/pengdenghuai.jpg',
  `sort_order`   INT                   DEFAULT 0      COMMENT '排序',
  `create_time`  DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_culture_id` (`culture_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='川蜀文化传承人';

INSERT INTO `culture_person` (`id`, `name`, `brief`, `culture_name`, `culture_id`, `video_url`, `avatar`, `sort_order`) VALUES
(1, '彭登怀', '川剧变脸表演艺术家，国家级非遗「川剧」代表性传承人，素有「变脸大师」之誉，多次登上国际舞台，让川剧变脸名扬海内外。', '川剧变脸', NULL, 'https://www.bilibili.com/video/BV1F4411J7gH/', '/images/person/pengdenghuai.jpg', 1),
(2, '郝淑萍', '蜀绣大师，国家级非遗「蜀绣」代表性传承人，擅长双面绣、立体绣，作品多次作为国礼，被誉为蜀绣泰斗。', '蜀绣', NULL, 'https://www.bilibili.com/video/BV1i3411r7PD/', '/images/person/haoshuping.jpg', 2),
(3, '孟德芝', '蜀绣大师，国家级非遗「蜀绣」代表性传承人，创办蜀绣工坊，技艺精湛、桃李众多，推动了蜀绣的当代传承。', '蜀绣', NULL, 'https://www.bilibili.com/video/BV1W341167C5/', '/images/person/mengdezhi.jpg', 3),
(4, '史正良', '川菜泰斗，元老级中国烹饪大师，国家级非遗「川菜」代表性传承人，毕生致力于川菜技艺的传承与创新。', '川菜', NULL, 'https://www.bilibili.com/video/BV1Rpp2zYEXY/', '/images/person/shizhengliang.jpg', 4),
(5, '彭子渝', '川菜美食评论家、作家，长期从事川菜文化研究，著有饮食文化专著，是川菜文化的著名推广者。', '川菜', NULL, 'https://www.bilibili.com/video/BV1Vp4y1B7hx/', '/images/person/pengziyu.jpg', 5),
(6, '陈寿', '西晋著名史学家，巴蜀人，撰有纪传体史书《三国志》，为后世三国文化的形成奠定了史学基础。', '三国文化', NULL, 'https://www.bilibili.com/video/BV19z4Ze1E7L/', '/images/person/chenshou.jpg', 6),
(7, '胡锦矗', '著名大熊猫研究专家，被誉为「大熊猫之父」，长期从事大熊猫生态与保护研究，为中国的熊猫保护事业奠基。', '大熊猫', NULL, 'https://www.bilibili.com/video/BV1RA411U71R/', '/images/person/hujinchu.jpg', 7),
(8, '张和民', '大熊猫保护专家，曾任卧龙中国保护大熊猫研究中心主任，推动大熊猫繁育与野化放归，成果卓著。', '大熊猫', NULL, 'https://www.bilibili.com/video/BV1Fk4y1G7Sq/', '/images/person/zhanghemin.jpg', 8),
(9, '沈铁梅', '川剧表演艺术家，国家级非遗「川剧」代表性传承人，三度摘得中国戏剧梅花奖，以高腔演唱享誉剧坛。', '川剧', NULL, 'https://www.bilibili.com/video/BV1tW411s78D/', '/images/person/shentiemei.jpg', 9),
(10, '陈巧茹', '川剧表演艺术家，成都市川剧研究院副院长，中国戏剧梅花奖得主，是当代川剧旦角代表性人物。', '川剧', NULL, 'https://www.bilibili.com/video/BV1iypUe2Em1/', '/images/person/chenqiaoru.jpg', 10),
(11, '李月秋', '四川清音表演艺术家，国家级非遗代表性传承人，唱腔清亮婉转，被誉为「清音皇后」，代表作《布谷鸟儿咕咕叫》。', '四川清音', NULL, 'https://www.bilibili.com/video/BV1Gu411o7Vs/', '/images/person/liyueqiu.jpg', 11),
(12, '沈伐', '谐剧表演艺术家，师承谐剧创始人王永梭，是四川谐剧的代表性人物，亦擅长巴蜀曲艺的表演与传承。', '四川清音', NULL, 'https://www.bilibili.com/video/BV11S421A7Da/', '/images/person/shenfa.jpg', 12),
(13, '李德才', '四川扬琴名家，擅唱「德派」扬琴，唱腔醇厚细腻，为四川扬琴艺术的一代宗师。', '四川扬琴', NULL, 'https://www.bilibili.com/video/BV1wxoKYzEHD/', '/images/person/lidecai.jpg', 13),
(14, '道安', '女，成都银花丝制作技艺国家级非遗代表性传承人，四川省工艺美术大师，掌握银花丝全套流程，代表作《三英战吕布》。', '成都银花丝', NULL, 'https://www.bilibili.com/video/BV1oyFzzREA3/', '/images/person/daoan.jpg', 14),
(15, '倪成玉', '成都银花丝大师，四川省工艺美术大师，国家级非遗「成都银花丝」代表性传承人，从艺数十年，桃李满门。', '成都银花丝', NULL, 'https://www.zgbk.com/c/2022-12-22/89876.shtml', '/images/person/nichengyu.jpg', 15),
(16, '陈云华', '青神竹编大师，国家级非遗「青神竹编」代表性传承人，创「竹丝作画」绝技，作品曾作为国礼赠予外宾。', '青神竹编', NULL, 'https://www.bilibili.com/video/BV1nj411X7fS/', '/images/person/chenyunhua.jpg', 16),
(17, '赵思进', '道明竹编国家级非遗代表性传承人，从事竹编七十余年，创新不断，并长期在海内外传授技艺、培育新人。', '道明竹编', NULL, 'https://tv.cctv.com/2019/05/30/VIDErHdB1PiBAZyHDAmKxc9j190530.shtml', '/images/person/zhaosijin.jpg', 17),
(18, '李芳福', '绵竹年画国家级非遗代表性传承人，擅长传统门神、三星年画的手工彩绘，画风生动、乡土气息浓郁。', '绵竹年画', NULL, 'https://wodsy.com/character_2/1037.html', '/images/person/lifangfu.jpg', 18),
(19, '胡光葵', '绵竹年画代表性传承人、年画大师，致力于绵竹年画技艺的传承与创新，推动年画走近当代生活。', '绵竹年画', NULL, 'https://www.bilibili.com/video/BV1vc41167ok/?spm_id_from=333.337.search-card.all.click&vd_source=6cb77c5657e7125c98a7a11b307a514c', '/images/person/huguangkui.jpg', 19),
(20, '杨华珍', '羌绣国家级非遗代表性传承人，创办藏羌织绣品牌，带动绣娘就业，让古老羌绣走上时尚与国际舞台。', '羌绣', NULL, 'https://www.bilibili.com/video/BV1dW4y1t7c5/', '/images/person/yanghuazhen.jpg', 20),
(21, '汪国芳', '羌绣代表性传承人，阿坝羌绣能手，致力于羌绣技艺的传承、培训与推广，绣品绚丽夺目。', '羌绣', NULL, 'https://www.bilibili.com/video/BV1S54y1j7MJ/', '/images/person/wangguofang.jpg', 21),
(22, '毕六福', '分水油纸伞国家级非遗代表性传承人，泸州分水油纸伞技艺掌舵人，坚守近百道古法工序，为「油纸伞活化石」守护者。', '分水油纸伞', NULL, 'https://tv.cctv.com/2024/12/11/VIDEWBExGCllORVlnRIHkAQa241211.shtml', '/images/person/biliufu.jpg', 22),
(23, '樊德然', '成都糖画国家级非遗代表性传承人，将皮影、剪纸与戏曲融入糖画，能倒数百个戏曲人物，人称「樊糖饼」。', '糖画', NULL, 'http://ich.nlc.cn/video/4632', '/images/person/fanderan.jpg', 23),
(24, '李冰', '战国时期秦国蜀郡太守，主持修建无坝引水工程都江堰，使成都平原成为「天府之国」，泽被后世两千余年。', '都江堰水利文化', NULL, 'https://www.bilibili.com/video/BV1a9jhzvEvW/', '/images/person/libing.jpg', 24)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `brief` = VALUES(`brief`), `culture_name` = VALUES(`culture_name`), `culture_id` = VALUES(`culture_id`), `video_url` = VALUES(`video_url`), `avatar` = VALUES(`avatar`), `sort_order` = VALUES(`sort_order`);

-- ----------------------------------------------------------------------------
-- 2. 自动匹配 culture_id（按 culture_name = culture.name）
-- ----------------------------------------------------------------------------
UPDATE `culture_person` cp
LEFT JOIN `culture` c ON cp.`culture_name` = c.`name`
SET cp.`culture_id` = c.`id`
WHERE cp.`culture_id` IS NULL;