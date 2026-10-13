-- ============================================================================
-- 后台看板演示种子数据（seed_dashboard.sql）
--
-- 目的：现有库中用户/行为/行程等样本极少（用户 1、行程 0、浏览 12…），
--       导致后台看板图表空得像"假数据"。本脚本按业务逻辑生成一批真实感样本，
--       所有看板指标仍由 AdminStatMapper 的真实聚合 SQL 统计得出。
--
-- 幂等：显式主键 + INSERT IGNORE，可重复执行（不会重复插入）。
-- 范围：仅新增 id 段 2001+ / 3001+ / 6001+ … 的数据，不动既有 id。
-- 编号：seq 数字表生成 0..9999 序号，用于批量构造。
-- ============================================================================
USE `shuyun`;

-- 数字表：0..9999（由四张 0..9 交叉连接生成）
-- 作为派生表在每条 INSERT 中复用，避免递归 CTE 的版本依赖。

-- ---------------------------------------------------------------------------
-- 1. 用户（id 2001..2200，密码统一为 123456 的 BCrypt）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user` (`id`, `username`, `email`, `password`, `bio`, `role`, `status`, `create_time`)
SELECT 2001 + n,
       CONCAT('traveler', 2001 + n),
       CONCAT('traveler', 2001 + n, '@example.com'),
       '$2a$10$Nqnze7pX3SQ00XLsRnq9.uwJXLJ5nGD3UYIifZCBkm.LYkO1NgUhq',
       '热爱蜀地风物的旅行者',
       'USER', 1,
       DATE_SUB(NOW(), INTERVAL (n % 120) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 + d.n * 1000 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d) seq
WHERE n < 200;

-- ---------------------------------------------------------------------------
-- 2. 用户画像（id 3001..3200，每人一条）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_profile`
(`id`, `user_id`, `age_range`, `gender`, `region`, `interests`, `companion`, `budget`, `create_time`)
SELECT 3001 + n, 2001 + n,
       ELT(1 + (n % 5), '18-25', '26-35', '36-45', '26-35', '46 岁以上'),
       ELT(1 + (n % 2), '男', '女'),
       ELT(1 + (n % 21), '成都', '绵阳', '德阳', '自贡', '攀枝花', '泸州', '广元', '遂宁', '内江', '乐山',
           '南充', '眉山', '宜宾', '广安', '达州', '雅安', '巴中', '资阳', '阿坝', '甘孜', '凉山'),
       ELT(1 + (n % 8), '自然风光,美食', '人文古迹,摄影', '亲子,古镇', '红色旅游,历史',
           '文化,养生', '美食,购物', '古镇,摄影', '自然风光,徒步'),
       ELT(1 + (n % 5), '独自', '情侣', '亲子', '父母', '朋友'),
       CONCAT(1000 + (n % 9) * 1000, ' 元'),
       DATE_SUB(NOW(), INTERVAL (n % 120) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
WHERE n < 200;

-- ---------------------------------------------------------------------------
-- 3. 浏览足迹 · 景点（id 6001+，分布于近 60 天，供访问趋势 / 最近活动）
--    先清掉本脚本自己的 id 段，支持反复重灌（不影响真实数据）
-- ---------------------------------------------------------------------------
DELETE FROM `user_view_history` WHERE `id` BETWEEN 6001 AND 7999;
INSERT IGNORE INTO `user_view_history`
(`id`, `user_id`, `target_type`, `target_id`, `target_name`, `cover_image`, `sub_title`, `view_time`, `create_time`)
SELECT 6001 + n, 2001 + (n % 200), 'SCENIC', s.id, s.name, s.cover_image, c.name,
       DATE_SUB(NOW(), INTERVAL FLOOR(POWER(RAND(), 1.6) * 60) DAY) - INTERVAL FLOOR(RAND() * 24) HOUR,
       DATE_SUB(NOW(), INTERVAL FLOOR(POWER(RAND(), 1.6) * 60) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 + d.n * 1000 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d) seq
JOIN `scenic_spot` s ON s.id = 1 + (((n % 200) + (n DIV 200) * 37) % 100)
LEFT JOIN `city` c ON c.id = s.city_id
WHERE n < 1000;

-- ---------------------------------------------------------------------------
-- 4. 浏览足迹 · 文化（id 7001+，兴趣分布来自此表 JOIN culture）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_view_history`
(`id`, `user_id`, `target_type`, `target_id`, `target_name`, `cover_image`, `sub_title`, `view_time`, `create_time`)
SELECT 7001 + n, 2001 + (n % 200), 'CULTURE', cu.id, cu.name, cu.cover_image, cu.category,
       DATE_SUB(NOW(), INTERVAL FLOOR(POWER(RAND(), 1.6) * 60) DAY) - INTERVAL FLOOR(RAND() * 24) HOUR,
       DATE_SUB(NOW(), INTERVAL FLOOR(POWER(RAND(), 1.6) * 60) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
JOIN `culture` cu ON cu.id = 1 + (((n % 200) + (n DIV 200) * 11) % 50)
WHERE n < 400;

-- ---------------------------------------------------------------------------
-- 5. 收藏（id 8001+）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_collection` (`id`, `user_id`, `target_type`, `target_id`, `create_time`)
SELECT 8001 + n, 2001 + (n % 200), 'SCENIC', 1 + (((n % 200) + (n DIV 200) * 29) % 100),
       DATE_SUB(NOW(), INTERVAL (n % 45) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
WHERE n < 320;

-- ---------------------------------------------------------------------------
-- 6. AI 对话（id 9001+，每人 2 段会话，近 30 天）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_ai_chat` (`id`, `user_id`, `session_id`, `role`, `content`, `tokens`, `mark_type`, `create_time`)
SELECT 9001 + n, 2001 + (n % 200),
       CONCAT('seed-', 2001 + (n % 200), '-', (n DIV 400)),
       IF(n % 2 = 0, 'user', 'assistant'),
       IF(n % 2 = 0,
          ELT(1 + (n % 8), '帮我规划成都三日游', '九寨沟什么时候去最美', '四川有哪些必吃川菜', '推荐一条川西自驾线',
              '三星堆怎么玩', '峨眉山看日出攻略', '带父母去四川怎么安排', '成都周边古镇推荐'),
          '好的，我来为你整理一份建议，记得结合季节与同行人调整节奏。'),
       0, 0,
       DATE_SUB(NOW(), INTERVAL (n % 30) DAY) - INTERVAL (n % 20) HOUR
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
WHERE n < 600;

-- ---------------------------------------------------------------------------
-- 7. 行程（id 10001..10160）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_trip` (`id`, `user_id`, `title`, `days`, `companion`, `interests`, `budget`, `status`, `create_time`)
SELECT 10001 + n, 2001 + (n % 200),
       CONCAT(ELT(1 + (n % 6), '成都', '川西', '乐山', '九寨沟', '稻城亚丁', '峨眉山'), ' ', 2 + (n % 5), ' 日游'),
       2 + (n % 5),
       ELT(1 + (n % 5), '独自', '情侣', '亲子', '父母', '朋友'),
       '自然风光,人文古迹,古镇',
       2000 + (n % 10) * 500, 1,
       DATE_SUB(NOW(), INTERVAL (n % 50) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
WHERE n < 160;

-- ---------------------------------------------------------------------------
-- 8. 行程每日明细（id 11001+，每行程 4 天）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_trip_day`
(`id`, `trip_id`, `day_index`, `date`, `scenic_id`, `scenic_name`, `stay_duration`, `transport`, `food`, `tips`)
SELECT 11001 + n, 10001 + ((n DIV 4) % 160), 1 + (n % 4),
       DATE_ADD(CURDATE(), INTERVAL (n % 4) DAY),
       s.id, s.name,
       ELT(1 + (n % 3), '2 小时', '3 小时', '半天'),
       ELT(1 + (n % 3), '自驾', '城际高铁', '步行'),
       '当地特色小吃', '早晚温差大，注意增减衣物'
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
JOIN `scenic_spot` s ON s.id = 1 + (n % 100)
WHERE n < 640;

-- ---------------------------------------------------------------------------
-- 9. 旅行笔记（id 12001+，三态审核交错）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user_note` (`id`, `user_id`, `title`, `content`, `tags`, `audit_status`, `create_time`)
SELECT 12001 + n, 2001 + (n % 200),
       CONCAT('旅行随笔 ', n),
       '这次出行收获很多，沿途的风景与美食都值得记录。成都的慢生活、都江堰的壮阔，都让人印象深刻。',
       '旅行,随笔', (n % 3),
       DATE_SUB(NOW(), INTERVAL (n % 40) DAY)
FROM (SELECT a.n + b.n * 10 + c.n * 100 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c) seq
WHERE n < 160;
