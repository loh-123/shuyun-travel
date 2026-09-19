-- ============================================================================
-- 蜀韵·智游（shuyun-travel）数据库初始化脚本
-- 数据库：shuyun    字符集：utf8mb4    引擎：InnoDB
-- 说明：所有表均包含公共字段 id / create_time / update_time / deleted
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `shuyun`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `shuyun`;

-- ----------------------------------------------------------------------------
-- 1. user 用户表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `email`       VARCHAR(100) NOT NULL                COMMENT '邮箱',
    `password`    VARCHAR(255) NOT NULL                COMMENT '密码',
    `avatar`      VARCHAR(255)          DEFAULT NULL   COMMENT '头像',
    `role`        VARCHAR(20)           DEFAULT 'USER' COMMENT '角色',
    `status`      TINYINT               DEFAULT 1      COMMENT '状态：0-禁用，1-正常',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ----------------------------------------------------------------------------
-- 2. city 市州表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `city` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(50)    NOT NULL                COMMENT '市州名称',
    `code`        VARCHAR(20)            DEFAULT NULL    COMMENT '行政区划代码',
    `description` TEXT                                   COMMENT '简介',
    `cover_image` VARCHAR(255)           DEFAULT NULL    COMMENT '封面图',
    `longitude`   DECIMAL(10, 6)         DEFAULT NULL    COMMENT '经度',
    `latitude`    DECIMAL(10, 6)         DEFAULT NULL    COMMENT '纬度',
    `create_time` DATETIME               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT                DEFAULT 0       COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '市州表';

-- ----------------------------------------------------------------------------
-- 3. scenic_spot 景点表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scenic_spot` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(100)   NOT NULL                COMMENT '景点名称',
    `city_id`     BIGINT                 DEFAULT NULL    COMMENT '所属市州ID',
    `type`        VARCHAR(50)            DEFAULT NULL    COMMENT '景点类型',
    `level`       VARCHAR(20)            DEFAULT NULL    COMMENT '景区等级',
    `address`     VARCHAR(255)           DEFAULT NULL    COMMENT '地址',
    `longitude`   DECIMAL(10, 6)         DEFAULT NULL    COMMENT '经度',
    `latitude`    DECIMAL(10, 6)         DEFAULT NULL    COMMENT '纬度',
    `cover_image` VARCHAR(255)           DEFAULT NULL    COMMENT '封面图',
    `description` TEXT                                   COMMENT '景点介绍',
    `open_time`   VARCHAR(100)           DEFAULT NULL    COMMENT '开放时间',
    `ticket_info` VARCHAR(255)           DEFAULT NULL    COMMENT '门票信息',
    `score`       DECIMAL(3, 1)          DEFAULT 5.0     COMMENT '评分',
    `tags`        VARCHAR(255)           DEFAULT NULL    COMMENT '标签（逗号分隔）',
    `view_count`  INT                    DEFAULT 0       COMMENT '浏览次数（人气）',
    `create_time` DATETIME               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT                DEFAULT 0       COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_city_id` (`city_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '景点表';

-- ----------------------------------------------------------------------------
-- 4. scenic_image 景点图片表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scenic_image` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `scenic_id`   BIGINT       NOT NULL                COMMENT '景点ID',
    `image_url`   VARCHAR(255) NOT NULL                COMMENT '图片地址',
    `sort_order`  INT                   DEFAULT 0      COMMENT '排序',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '景点图片表';

-- ----------------------------------------------------------------------------
-- 5. culture 文化表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `culture` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(50)  NOT NULL                COMMENT '名称',
    `category`    VARCHAR(50)           DEFAULT NULL   COMMENT '分类',
    `cover_image` VARCHAR(255)          DEFAULT NULL   COMMENT '封面图',
    `summary`     VARCHAR(500)          DEFAULT NULL   COMMENT '摘要',
    `content`     TEXT                                 COMMENT '正文',
    `video_url`   VARCHAR(255)          DEFAULT NULL   COMMENT '视频地址',
    `tags`        VARCHAR(255)          DEFAULT NULL   COMMENT '标签（逗号分隔）',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文化表';

-- ----------------------------------------------------------------------------
-- 6. user_collection 用户收藏表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_collection` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT      NOT NULL                COMMENT '用户ID',
    `target_type` VARCHAR(20) NOT NULL                COMMENT '收藏对象类型',
    `target_id`   BIGINT      NOT NULL                COMMENT '收藏对象ID',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT              DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户收藏表';

-- ----------------------------------------------------------------------------
-- 7. user_ai_chat AI 对话记录表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_ai_chat` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT      NOT NULL                COMMENT '用户ID',
    `session_id`  VARCHAR(64)          DEFAULT NULL   COMMENT '会话ID',
    `role`        VARCHAR(20)          DEFAULT NULL   COMMENT '角色：user/assistant',
    `content`     TEXT                                COMMENT '消息内容',
    `tokens`      INT                  DEFAULT 0      COMMENT '消耗 token 数',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT              DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 对话记录表';

-- ----------------------------------------------------------------------------
-- 8. user_trip 用户行程表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_trip` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT         NOT NULL                COMMENT '用户ID',
    `title`       VARCHAR(100)            DEFAULT NULL   COMMENT '行程标题',
    `days`        INT                     DEFAULT NULL   COMMENT '行程天数',
    `companion`   VARCHAR(50)             DEFAULT NULL   COMMENT '同行人',
    `interests`   VARCHAR(255)            DEFAULT NULL   COMMENT '兴趣偏好',
    `budget`      DECIMAL(10, 2)          DEFAULT NULL   COMMENT '预算',
    `status`      TINYINT                 DEFAULT 1      COMMENT '状态：0-草稿，1-正常',
    `cover_image` VARCHAR(255)            DEFAULT NULL   COMMENT '封面图',
    `create_time` DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT                 DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户行程表';

-- ----------------------------------------------------------------------------
-- 9. user_trip_day 行程每日明细表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_trip_day` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trip_id`       BIGINT       NOT NULL                COMMENT '行程ID',
    `day_index`     INT                   DEFAULT NULL   COMMENT '第几天',
    `date`          DATE                  DEFAULT NULL   COMMENT '日期',
    `scenic_id`     BIGINT                DEFAULT NULL   COMMENT '景点ID',
    `scenic_name`   VARCHAR(100)          DEFAULT NULL   COMMENT '景点名称',
    `stay_duration` VARCHAR(50)           DEFAULT NULL   COMMENT '停留时长',
    `transport`     VARCHAR(100)          DEFAULT NULL   COMMENT '交通方式',
    `food`          VARCHAR(255)          DEFAULT NULL   COMMENT '美食推荐',
    `tips`          TEXT                                 COMMENT '温馨提示',
    `create_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_trip_id` (`trip_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '行程每日明细表';

-- ============================================================================
-- 初始化数据
-- 说明：使用 INSERT IGNORE + 显式主键，脚本可重复执行，已存在的记录会被跳过
-- ============================================================================

-- 21 个四川市州
INSERT IGNORE INTO `city` (`id`, `name`, `code`, `description`, `cover_image`) VALUES
(1,  '成都',  '510100', '四川省省会，天府之国，美食与休闲之都',       'https://picsum.photos/seed/shuyun-city-1/800/600'),
(2,  '绵阳',  '510700', '中国科技城，拥有越王楼与富乐山',             'https://picsum.photos/seed/shuyun-city-2/800/600'),
(3,  '德阳',  '510600', '重装之都，三星堆遗址所在地',               'https://picsum.photos/seed/shuyun-city-3/800/600'),
(4,  '自贡',  '510300', '千年盐都、恐龙之乡、南国灯城',             'https://picsum.photos/seed/shuyun-city-4/800/600'),
(5,  '攀枝花', '510400', '阳光花城，康养胜地',                       'https://picsum.photos/seed/shuyun-city-5/800/600'),
(6,  '泸州',  '510500', '中国酒城，长江与沱江交汇',                 'https://picsum.photos/seed/shuyun-city-6/800/600'),
(7,  '广元',  '510800', '女皇故里，剑门关所在地',                   'https://picsum.photos/seed/shuyun-city-7/800/600'),
(8,  '遂宁',  '510900', '观音故里，涪江之畔的宜居之城',             'https://picsum.photos/seed/shuyun-city-8/800/600'),
(9,  '内江',  '511000', '甜城，张大千故里',                         'https://picsum.photos/seed/shuyun-city-9/800/600'),
(10, '乐山',  '511100', '乐山大佛与峨眉山所在，山水人文名城',       'https://picsum.photos/seed/shuyun-city-10/800/600'),
(11, '南充',  '511300', '丝绸之都，三国文化名城',                   'https://picsum.photos/seed/shuyun-city-11/800/600'),
(12, '眉山',  '511400', '三苏故里，东坡文化之乡',                   'https://picsum.photos/seed/shuyun-city-12/800/600'),
(13, '宜宾',  '511500', '中国酒都，长江首城',                       'https://picsum.photos/seed/shuyun-city-13/800/600'),
(14, '广安',  '511600', '邓小平故里，红色旅游之城',                 'https://picsum.photos/seed/shuyun-city-14/800/600'),
(15, '达州',  '511700', '巴人故里，川东门户',                       'https://picsum.photos/seed/shuyun-city-15/800/600'),
(16, '雅安',  '511800', '雨城，熊猫故乡',                           'https://picsum.photos/seed/shuyun-city-16/800/600'),
(17, '巴中',  '511900', '川陕革命根据地中心，红叶之乡',             'https://picsum.photos/seed/shuyun-city-17/800/600'),
(18, '资阳',  '512000', '蜀人原乡，三贤故里',                       'https://picsum.photos/seed/shuyun-city-18/800/600'),
(19, '阿坝',  '513200', '阿坝藏族羌族自治州，九寨沟与黄龙所在',     'https://picsum.photos/seed/shuyun-city-19/800/600'),
(20, '甘孜',  '513300', '甘孜藏族自治州，稻城亚丁与贡嘎山所在',     'https://picsum.photos/seed/shuyun-city-20/800/600'),
(21, '凉山',  '513400', '中国最大的彝族聚居区，邛海与泸沽湖风光秀丽', 'https://picsum.photos/seed/shuyun-city-21/800/600');

-- 10 个景点
INSERT IGNORE INTO `scenic_spot` (`id`, `name`, `city_id`, `type`, `level`, `score`, `description`, `cover_image`, `tags`) VALUES
(1,  '九寨沟',   19, '自然风光', '5A', 4.9, '世界自然遗产，以翠海、叠瀑、彩林、雪峰与藏族风情闻名，被誉为童话世界。', 'https://picsum.photos/seed/shuyun-scenic-1/800/600',  '世界自然遗产,山水,摄影'),
(2,  '都江堰',   1,  '人文古迹', '5A', 4.7, '两千多年前修建的宏大水利工程，至今仍在灌溉成都平原，是世界文化遗产。',   'https://picsum.photos/seed/shuyun-scenic-2/800/600',  '水利工程,世界文化遗产,亲子'),
(3,  '峨眉山',   10, '自然风光', '5A', 4.8, '中国佛教四大名山之一，金顶云海、日出与佛光闻名天下。',                   'https://picsum.photos/seed/shuyun-scenic-3/800/600',  '佛教名山,云海,日出'),
(4,  '乐山大佛', 10, '人文古迹', '5A', 4.7, '依岷江崖壁凿成的唐代弥勒佛坐像，通高七十一米，世界文化遗产。',           'https://picsum.photos/seed/shuyun-scenic-4/800/600',  '世界文化遗产,石刻,江河'),
(5,  '三星堆',   3,  '博物馆',   '4A', 4.6, '古蜀文明遗址，青铜神树与纵目面具神秘震撼，是探索古蜀文明的第一现场。',   'https://picsum.photos/seed/shuyun-scenic-5/800/600',  '古蜀文明,考古,博物馆'),
(6,  '稻城亚丁', 20, '自然风光', '5A', 4.9, '被誉为蓝色星球上最后一片净土，雪山、草甸与海子构成的秘境。',             'https://picsum.photos/seed/shuyun-scenic-6/800/600',  '雪山,徒步,秘境'),
(7,  '西岭雪山', 1,  '自然风光', '4A', 4.5, '成都近郊雪山，冬季滑雪、夏季避暑，杜甫笔下窗含西岭千秋雪即指此山。',     'https://picsum.photos/seed/shuyun-scenic-7/800/600',  '滑雪,雪山,温泉'),
(8,  '青城山',   1,  '自然风光', '5A', 4.6, '中国道教发源地之一，山林幽深、宫观古朴，世界文化遗产。',                 'https://picsum.photos/seed/shuyun-scenic-8/800/600',  '道教名山,世界文化遗产,幽静'),
(9,  '武侯祠',   1,  '人文古迹', '4A', 4.6, '纪念诸葛亮与蜀汉英雄的祠庙，三国文化圣地。',                             'https://picsum.photos/seed/shuyun-scenic-9/800/600',  '三国文化,祠堂,历史'),
(10, '杜甫草堂', 1,  '人文古迹', '4A', 4.5, '唐代诗人杜甫寓居成都的故居，园林清幽，诗意盎然。',                       'https://picsum.photos/seed/shuyun-scenic-10/800/600', '诗意,园林,博物馆');

-- 8 大文化
INSERT IGNORE INTO `culture` (`id`, `name`, `category`, `cover_image`, `summary`, `content`, `tags`) VALUES
(1, '川剧变脸', '戏曲',     'https://picsum.photos/seed/shuyun-culture-1/800/600', '川剧独有的绝技，瞬间变换脸谱，令人叹为观止。', '变脸是川剧表演的特技之一，用于揭示剧中人物内心及思想感情的变化。演员在挥手、转身之间即可变换出不同脸谱，技法包括抹脸、吹脸、扯脸等，被誉为东方艺术瑰宝。', '非遗,戏曲,绝技'),
(2, '蜀绣',     '传统技艺', 'https://picsum.photos/seed/shuyun-culture-2/800/600', '中国四大名绣之一，针法严谨、色彩明快。',       '蜀绣又称川绣，起源于四川，与苏绣、湘绣、粤绣并称中国四大名绣。其针法严谨、片线光亮、色彩明快，代表作有芙蓉鲤鱼与熊猫等。',                            '非遗,四大名绣,手工艺'),
(3, '川菜',     '饮食文化', 'https://picsum.photos/seed/shuyun-culture-3/800/600', '中国八大菜系之一，以麻辣鲜香著称。',           '川菜起源于巴蜀，是中国八大菜系之一，讲究麻辣鲜香，追求一菜一格、百菜百味。代表菜品有麻婆豆腐、回锅肉、宫保鸡丁、水煮鱼等。',                       '美食,麻辣,八大菜系'),
(4, '茶马古道', '历史文化', 'https://picsum.photos/seed/shuyun-culture-4/800/600', '古代西南地区以茶叶与马匹贸易为主的交通网络。', '茶马古道是唐宋以来汉、藏等民族之间以茶马互市为主的商贸通道，穿越川、滇、藏的高山峡谷，不仅是商路，也是民族文化交流的走廊。',                         '古道,商贸,民族文化'),
(5, '三国文化', '历史文化', 'https://picsum.photos/seed/shuyun-culture-5/800/600', '以蜀汉为核心的三国历史文化，在四川留下深厚印记。', '四川是三国时期蜀汉政权的中心，留下武侯祠、刘备墓、剑门关等众多遗迹。诸葛亮、关羽、赵云等人物与故事深入人心，形成独特的三国文化景观。',              '三国,蜀汉,历史遗迹'),
(6, '三星堆',   '考古文明', 'https://picsum.photos/seed/shuyun-culture-6/800/600', '古蜀文明的代表性遗址，出土文物神秘而震撼。',   '三星堆遗址位于四川广汉，是距今约三千至五千年的古蜀文化遗址。青铜神树、纵目面具、黄金权杖等文物造型奇特，展现了与中原文明迥异而璀璨的古蜀文明。',       '古蜀文明,考古,青铜器'),
(7, '大熊猫',   '自然生态', 'https://picsum.photos/seed/shuyun-culture-7/800/600', '中国的国宝，四川是其主要栖息地。',             '大熊猫是中国的国宝与珍稀物种，四川是其最主要的栖息地，拥有成都大熊猫繁育研究基地、卧龙自然保护区等。憨态可掬的形象让大熊猫成为四川乃至中国的文化名片。',   '国宝,自然保护,生态'),
(8, '酒文化',   '饮食文化', 'https://picsum.photos/seed/shuyun-culture-8/800/600', '四川是名酒之乡，酿酒与饮酒文化源远流长。',     '四川是中国白酒的重要产区，五粮液、泸州老窖、剑南春、郎酒等名酒享誉全国。酒在四川的饮食、社交与节庆中占据重要地位，形成独特的酒文化。',                 '白酒,名酒,饮食文化');

-- 景点图片（每个景点 3 张示例图，供详情页轮播与图片墙使用）
INSERT IGNORE INTO `scenic_image` (`id`, `scenic_id`, `image_url`, `sort_order`) VALUES
(1,  1,  'https://picsum.photos/seed/shuyun-s1-a/1200/800', 0),
(2,  1,  'https://picsum.photos/seed/shuyun-s1-b/1200/800', 1),
(3,  1,  'https://picsum.photos/seed/shuyun-s1-c/1200/800', 2),
(4,  2,  'https://picsum.photos/seed/shuyun-s2-a/1200/800', 0),
(5,  2,  'https://picsum.photos/seed/shuyun-s2-b/1200/800', 1),
(6,  2,  'https://picsum.photos/seed/shuyun-s2-c/1200/800', 2),
(7,  3,  'https://picsum.photos/seed/shuyun-s3-a/1200/800', 0),
(8,  3,  'https://picsum.photos/seed/shuyun-s3-b/1200/800', 1),
(9,  3,  'https://picsum.photos/seed/shuyun-s3-c/1200/800', 2),
(10, 4,  'https://picsum.photos/seed/shuyun-s4-a/1200/800', 0),
(11, 4,  'https://picsum.photos/seed/shuyun-s4-b/1200/800', 1),
(12, 4,  'https://picsum.photos/seed/shuyun-s4-c/1200/800', 2),
(13, 5,  'https://picsum.photos/seed/shuyun-s5-a/1200/800', 0),
(14, 5,  'https://picsum.photos/seed/shuyun-s5-b/1200/800', 1),
(15, 5,  'https://picsum.photos/seed/shuyun-s5-c/1200/800', 2),
(16, 6,  'https://picsum.photos/seed/shuyun-s6-a/1200/800', 0),
(17, 6,  'https://picsum.photos/seed/shuyun-s6-b/1200/800', 1),
(18, 6,  'https://picsum.photos/seed/shuyun-s6-c/1200/800', 2),
(19, 7,  'https://picsum.photos/seed/shuyun-s7-a/1200/800', 0),
(20, 7,  'https://picsum.photos/seed/shuyun-s7-b/1200/800', 1),
(21, 7,  'https://picsum.photos/seed/shuyun-s7-c/1200/800', 2),
(22, 8,  'https://picsum.photos/seed/shuyun-s8-a/1200/800', 0),
(23, 8,  'https://picsum.photos/seed/shuyun-s8-b/1200/800', 1),
(24, 8,  'https://picsum.photos/seed/shuyun-s8-c/1200/800', 2),
(25, 9,  'https://picsum.photos/seed/shuyun-s9-a/1200/800', 0),
(26, 9,  'https://picsum.photos/seed/shuyun-s9-b/1200/800', 1),
(27, 9,  'https://picsum.photos/seed/shuyun-s9-c/1200/800', 2),
(28, 10, 'https://picsum.photos/seed/shuyun-s10-a/1200/800', 0),
(29, 10, 'https://picsum.photos/seed/shuyun-s10-b/1200/800', 1),
(30, 10, 'https://picsum.photos/seed/shuyun-s10-c/1200/800', 2);

-- 8. 文化正文补全（详情页展示用；使用 SET 全量赋值，可重复执行）
UPDATE `culture` SET `content` = '变脸是川剧表演的特技之一，用于揭示剧中人物内心及思想感情的变化。演员在挥手、转身之间即可变换出不同脸谱，技法包括抹脸、吹脸、扯脸等，被誉为东方艺术瑰宝。\n川剧形成于清代中叶，融合昆、高、胡、弹、灯五种声腔，除变脸外还有吐火、滚灯等绝活。成都的锦江剧场等处常有演出，一晚便可看尽川剧的绝技与幽默。' WHERE `id` = 1;
UPDATE `culture` SET `content` = '蜀绣又称川绣，起源于四川，与苏绣、湘绣、粤绣并称中国四大名绣。其针法严谨、片线光亮、色彩明快，代表作有芙蓉鲤鱼与熊猫等。\n蜀绣针法多达百余种，晕针、铺针、滚针、截针各有讲究，双面绣尤为难成——正反两面皆成完整画面。成都蜀锦织绣博物馆与郫都区安靖一带，仍能看到绣娘一针一线的工作现场。' WHERE `id` = 2;
UPDATE `culture` SET `content` = '川菜起源于巴蜀，是中国八大菜系之一，讲究麻辣鲜香，追求一菜一格、百菜百味。代表菜品有麻婆豆腐、回锅肉、宫保鸡丁、水煮鱼等。\n川菜的味型远不止「麻辣」，鱼香、怪味、糊辣、荔枝味等复合味型多达二十余种。成都讲究调和，自贡盐帮菜擅用鲜辣，乐山则以甜皮鸭、钵钵鸡等小吃见长，一地一味。' WHERE `id` = 3;
UPDATE `culture` SET `content` = '茶马古道是唐宋以来汉、藏等民族之间以茶马互市为主的商贸通道，穿越川、滇、藏的高山峡谷，不仅是商路，也是民族文化交流的走廊。\n川藏一线的起点在雅安，蒙顶山茶与边茶由此西行，背夫们以人力将茶包驮过二郎山。今天在上里古镇、蒙顶山一带，仍能找到古道、茶号与背夫文化的痕迹。' WHERE `id` = 4;
UPDATE `culture` SET `content` = '四川是三国时期蜀汉政权的中心，留下武侯祠、刘备墓、剑门关等众多遗迹。诸葛亮、关羽、赵云等人物与故事深入人心，形成独特的三国文化景观。\n成都武侯祠与锦里相连，是全国影响最大的三国遗迹博物馆，惠陵、三义庙与《出师表》碑刻皆在其中。北出剑门关，古蜀道上的关隘与栈道让三国故事几乎步步可寻。' WHERE `id` = 5;
UPDATE `culture` SET `content` = '三星堆遗址位于四川广汉，是距今约三千至五千年的古蜀文化遗址。青铜神树、纵目面具、黄金权杖等文物造型奇特，展现了与中原文明迥异而璀璨的古蜀文明。\n三星堆博物馆新馆已开放，青铜大立人、青铜神树与纵目面具是必看之作。它与成都金沙遗址一脉相承，太阳神鸟金饰与青铜立人共同勾勒出古蜀文明的神秘轮廓。' WHERE `id` = 6;
UPDATE `culture` SET `content` = '大熊猫是中国的国宝与珍稀物种，四川是其最主要的栖息地，拥有成都大熊猫繁育研究基地、卧龙自然保护区等。憨态可掬的形象让大熊猫成为四川乃至中国的文化名片。\n看熊猫宜在清晨，此时它们最为活跃；成都大熊猫繁育研究基地交通便利，都江堰的熊猫谷与卧龙中华大熊猫苑则能看到更接近野外的状态。四川的竹海与山地生态，正是这一物种得以延续的底气。' WHERE `id` = 7;
UPDATE `culture` SET `content` = '四川是中国白酒的重要产区，五粮液、泸州老窖、剑南春、郎酒等名酒享誉全国。酒在四川的饮食、社交与节庆中占据重要地位，形成独特的酒文化。\n宜宾、泸州、绵竹、古蔺串联起川酒版图：宜宾五粮液取五粮之香，泸州老窖以百年窖池群著称，绵竹剑南春、古蔺郎酒各有风土。泸州老窖的窖池与宜宾的窖池群至今仍在持续酿造，被称作「活文物」。' WHERE `id` = 8;

-- 9. 景点补充信息（开放时间 / 门票 / 地址 / 经纬度）
--    整理自公开信息的示例值，实际以景区公告为准；用 SET 全量赋值，可重复执行
UPDATE `scenic_spot` SET `address` = '四川省阿坝州九寨沟县漳扎镇', `open_time` = '07:00-18:00', `ticket_info` = '旺季 169 元 / 淡季 80 元', `longitude` = 103.918000, `latitude` = 33.260000 WHERE `id` = 1;
UPDATE `scenic_spot` SET `address` = '四川省成都市都江堰市公园路', `open_time` = '08:00-18:00', `ticket_info` = '80 元', `longitude` = 103.606000, `latitude` = 31.006000 WHERE `id` = 2;
UPDATE `scenic_spot` SET `address` = '四川省乐山市峨眉山市黄湾镇', `open_time` = '06:00-18:30', `ticket_info` = '160 元', `longitude` = 103.484000, `latitude` = 29.601000 WHERE `id` = 3;
UPDATE `scenic_spot` SET `address` = '四川省乐山市市中区凌云路', `open_time` = '07:30-18:30', `ticket_info` = '80 元', `longitude` = 103.771000, `latitude` = 29.544000 WHERE `id` = 4;
UPDATE `scenic_spot` SET `address` = '四川省德阳市广汉市西安路 133 号', `open_time` = '08:30-18:00', `ticket_info` = '72 元', `longitude` = 104.199000, `latitude` = 31.010000 WHERE `id` = 5;
UPDATE `scenic_spot` SET `address` = '四川省甘孜州稻城县香格里拉镇', `open_time` = '06:30-18:00', `ticket_info` = '146 元（含观光车）', `longitude` = 100.331000, `latitude` = 28.435000 WHERE `id` = 6;
UPDATE `scenic_spot` SET `address` = '四川省成都市大邑县西岭镇', `open_time` = '08:30-17:30', `ticket_info` = '120 元', `longitude` = 103.190000, `latitude` = 30.800000 WHERE `id` = 7;
UPDATE `scenic_spot` SET `address` = '四川省成都市都江堰市青城山镇', `open_time` = '08:00-18:00', `ticket_info` = '80 元', `longitude` = 103.568000, `latitude` = 30.902000 WHERE `id` = 8;
UPDATE `scenic_spot` SET `address` = '四川省成都市武侯区武侯祠大街 231 号', `open_time` = '08:00-20:00', `ticket_info` = '50 元', `longitude` = 104.048000, `latitude` = 30.645000 WHERE `id` = 9;
UPDATE `scenic_spot` SET `address` = '四川省成都市青羊区青华路 37 号', `open_time` = '08:00-18:30', `ticket_info` = '50 元', `longitude` = 104.030000, `latitude` = 30.665000 WHERE `id` = 10;

-- 10. 新增人气字段 view_count（仅首次执行时新增列，已存在则跳过）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scenic_spot' AND COLUMN_NAME = 'view_count');
SET @ddl := IF(@has_col = 0,
               'ALTER TABLE `scenic_spot` ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT ''浏览次数（人气）'' AFTER `tags`',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11. 原 10 个景点的人气初始化（可重复执行）
UPDATE `scenic_spot` SET `view_count` = 1860 WHERE `id` = 1;
UPDATE `scenic_spot` SET `view_count` = 1350 WHERE `id` = 2;
UPDATE `scenic_spot` SET `view_count` = 1480 WHERE `id` = 3;
UPDATE `scenic_spot` SET `view_count` = 1210 WHERE `id` = 4;
UPDATE `scenic_spot` SET `view_count` = 1120 WHERE `id` = 5;
UPDATE `scenic_spot` SET `view_count` = 1620 WHERE `id` = 6;
UPDATE `scenic_spot` SET `view_count` = 760  WHERE `id` = 7;
UPDATE `scenic_spot` SET `view_count` = 980  WHERE `id` = 8;
UPDATE `scenic_spot` SET `view_count` = 870  WHERE `id` = 9;
UPDATE `scenic_spot` SET `view_count` = 690  WHERE `id` = 10;

-- 12. 扩充景点：红色旅游 / 亲子 / 古镇（把类型从 3 类扩到 6 类）
INSERT IGNORE INTO `scenic_spot`
(`id`, `name`, `city_id`, `type`, `level`, `address`, `longitude`, `latitude`, `cover_image`, `description`, `open_time`, `ticket_info`, `score`, `tags`, `view_count`) VALUES
(11, '泸定桥',   20, '红色旅游', '4A', '四川省甘孜州泸定县泸桥镇',     102.234000, 29.914000, 'https://picsum.photos/seed/shuyun-scenic-11/800/600', '康熙年间建成的铁索桥，因红军飞夺泸定桥而载入史册，十三根铁链横跨大渡河。', '08:00-18:00', '10 元', 4.6, '红色旅游,长征,铁索桥', 1420),
(12, '邓小平故里', 14, '红色旅游', '5A', '四川省广安市广安区协兴镇',     106.630000, 30.540000, 'https://picsum.photos/seed/shuyun-scenic-12/800/600', '邓小平同志出生地，保存完好的川东民居与陈列馆，是重要的爱国主义教育基地。', '08:30-17:30', '免费', 4.8, '红色旅游,名人故里,爱国教育', 980),
(13, '川陕革命根据地红军烈士陵园', 17, '红色旅游', '4A', '四川省巴中市通江县沙溪镇', 107.245000, 32.115000, 'https://picsum.photos/seed/shuyun-scenic-13/800/600', '全国最大的红军烈士陵园，依山而建的墓碑群安静而震撼，是川陕苏区的历史见证。', '09:00-17:00', '免费', 4.5, '红色旅游,烈士陵园,爱国教育', 460),
(14, '成都大熊猫繁育研究基地', 1, '亲子', '5A', '四川省成都市成华区熊猫大道 1375 号', 104.150000, 30.735000, 'https://picsum.photos/seed/shuyun-scenic-14/800/600', '离城市最近的大熊猫家园，清晨可以看到熊猫进食与爬树，亲子出行首选。', '07:30-18:00', '55 元', 4.8, '亲子,大熊猫,科普', 1980),
(15, '成都海昌极地海洋公园', 1, '亲子', '4A', '四川省成都市天府新区海洋路 68 号', 104.070000, 30.500000, 'https://picsum.photos/seed/shuyun-scenic-15/800/600', '极地动物与海洋生物主题乐园，白鲸、企鹅与海豚表演适合带小朋友花上一整天。', '09:00-18:00', '230 元', 4.5, '亲子,海洋馆,表演', 890),
(16, '国色天乡乐园', 1, '亲子', '4A', '四川省成都市温江区万春镇',   103.850000, 30.720000, 'https://picsum.photos/seed/shuyun-scenic-16/800/600', '成都近郊的综合性主题乐园，游乐设施与季节花海兼顾，周末亲子出游的热门去处。', '09:30-18:00', '180 元', 4.4, '亲子,乐园,游乐设施', 720),
(17, '阆中古城',   11, '古镇', '5A', '四川省南充市阆中市阆水中路',    106.005000, 31.558000, 'https://picsum.photos/seed/shuyun-scenic-17/800/600', '中国四大古城之一，风水格局完整，张飞庙、贡院与青瓦老街保留着明清气韵。', '08:30-18:00', '古城免费（景点联票 100 元）', 4.7, '古镇,古城,风水', 1330),
(18, '黄龙溪古镇', 1, '古镇', '4A', '四川省成都市双流区黄龙溪镇',    103.970000, 30.320000, 'https://picsum.photos/seed/shuyun-scenic-18/800/600', '川西水乡古镇，一条清溪穿镇而过，夏可戏水、冬可喝茶，小吃街热闹非常。', '全天开放', '免费', 4.5, '古镇,水乡,小吃', 1150),
(19, '李庄古镇',   13, '古镇', '4A', '四川省宜宾市翠屏区李庄镇',     104.790000, 28.785000, 'https://picsum.photos/seed/shuyun-scenic-19/800/600', '长江边的千年古镇，抗战时期同济大学西迁于此，李庄白肉与古建群是它的名片。', '全天开放', '免费', 4.6, '古镇,抗战文化,李庄白肉', 640);

-- 13. 新增景点的画廊图（每景点 3 张）
INSERT IGNORE INTO `scenic_image` (`id`, `scenic_id`, `image_url`, `sort_order`) VALUES
(31, 11, 'https://picsum.photos/seed/shuyun-s11-a/1200/800', 0),
(32, 11, 'https://picsum.photos/seed/shuyun-s11-b/1200/800', 1),
(33, 11, 'https://picsum.photos/seed/shuyun-s11-c/1200/800', 2),
(34, 12, 'https://picsum.photos/seed/shuyun-s12-a/1200/800', 0),
(35, 12, 'https://picsum.photos/seed/shuyun-s12-b/1200/800', 1),
(36, 12, 'https://picsum.photos/seed/shuyun-s12-c/1200/800', 2),
(37, 13, 'https://picsum.photos/seed/shuyun-s13-a/1200/800', 0),
(38, 13, 'https://picsum.photos/seed/shuyun-s13-b/1200/800', 1),
(39, 13, 'https://picsum.photos/seed/shuyun-s13-c/1200/800', 2),
(40, 14, 'https://picsum.photos/seed/shuyun-s14-a/1200/800', 0),
(41, 14, 'https://picsum.photos/seed/shuyun-s14-b/1200/800', 1),
(42, 14, 'https://picsum.photos/seed/shuyun-s14-c/1200/800', 2),
(43, 15, 'https://picsum.photos/seed/shuyun-s15-a/1200/800', 0),
(44, 15, 'https://picsum.photos/seed/shuyun-s15-b/1200/800', 1),
(45, 15, 'https://picsum.photos/seed/shuyun-s15-c/1200/800', 2),
(46, 16, 'https://picsum.photos/seed/shuyun-s16-a/1200/800', 0),
(47, 16, 'https://picsum.photos/seed/shuyun-s16-b/1200/800', 1),
(48, 16, 'https://picsum.photos/seed/shuyun-s16-c/1200/800', 2),
(49, 17, 'https://picsum.photos/seed/shuyun-s17-a/1200/800', 0),
(50, 17, 'https://picsum.photos/seed/shuyun-s17-b/1200/800', 1),
(51, 17, 'https://picsum.photos/seed/shuyun-s17-c/1200/800', 2),
(52, 18, 'https://picsum.photos/seed/shuyun-s18-a/1200/800', 0),
(53, 18, 'https://picsum.photos/seed/shuyun-s18-b/1200/800', 1),
(54, 18, 'https://picsum.photos/seed/shuyun-s18-c/1200/800', 2),
(55, 19, 'https://picsum.photos/seed/shuyun-s19-a/1200/800', 0),
(56, 19, 'https://picsum.photos/seed/shuyun-s19-b/1200/800', 1),
(57, 19, 'https://picsum.photos/seed/shuyun-s19-c/1200/800', 2);
