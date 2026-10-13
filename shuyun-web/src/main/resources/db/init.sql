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
    `avatar`      VARCHAR(255)          DEFAULT NULL    COMMENT '头像',
    `bio`         VARCHAR(120)          DEFAULT NULL    COMMENT '一句话简介',
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

-- ============================================================================
-- 10. user_view_history 浏览足迹表
--     每个用户对同一对象只保留一条，重复浏览时更新时间（便于按最近浏览排序）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_view_history` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL                COMMENT '用户ID',
    `target_type` VARCHAR(20)  NOT NULL                COMMENT '对象类型：SCENIC/CULTURE',
    `target_id`   BIGINT       NOT NULL                COMMENT '对象ID',
    `target_name` VARCHAR(100)          DEFAULT NULL   COMMENT '对象名称（冗余）',
    `cover_image` VARCHAR(255)          DEFAULT NULL   COMMENT '封面图（冗余）',
    `sub_title`   VARCHAR(100)          DEFAULT NULL   COMMENT '副标题（市州 / 分类，冗余）',
    `view_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
    KEY `idx_user_time` (`user_id`, `view_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '浏览足迹表';

-- ============================================================================
-- 11. user_note 旅行笔记表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_note` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL                COMMENT '用户ID',
    `title`       VARCHAR(120)          DEFAULT NULL   COMMENT '标题',
    `content`     TEXT                                 COMMENT '正文',
    `target_type` VARCHAR(20)           DEFAULT NULL   COMMENT '关联对象类型',
    `target_id`   BIGINT                DEFAULT NULL   COMMENT '关联对象ID（可空）',
    `target_name` VARCHAR(100)          DEFAULT NULL   COMMENT '关联对象名称（可自由填写）',
    `tags`        VARCHAR(255)          DEFAULT NULL   COMMENT '标签（逗号分隔）',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '旅行笔记表';

-- ============================================================================
-- 14. culture 扩展字段：关联景点 / 传承人（仅首次执行时新增列，已存在则跳过）
-- ============================================================================
SET @has_c := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'culture' AND COLUMN_NAME = 'related_scenic_ids');
SET @ddl_c := IF(@has_c = 0,
                 'ALTER TABLE `culture` ADD COLUMN `related_scenic_ids` VARCHAR(100) DEFAULT NULL COMMENT ''关联景点ID（逗号分隔）'' AFTER `content`, ADD COLUMN `inheritors` TEXT COMMENT ''传承人（每行一条：姓名|身份|一句话）'' AFTER `related_scenic_ids`',
                 'SELECT 1');
PREPARE stmt_c FROM @ddl_c;
EXECUTE stmt_c;
DEALLOCATE PREPARE stmt_c;

-- 15. 八大文化的关联景点 / 传承人 / 视频（SET 全量赋值，可重复执行）
--     说明：关联景点与传承人为示例信息，实际以景区与官方公布为准；
--          video_url 先给 B 站搜索页占位，拿到真实 BV 号后改成
--          https://player.bilibili.com/player.html?bvid=BVxxxx 即可内嵌播放
UPDATE `culture` SET `related_scenic_ids` = '9,10,18', `video_url` = 'https://search.bilibili.com/all?keyword=%E5%B7%9D%E5%89%A7%E5%8F%98%E8%84%B8', `inheritors` = '康芷林|川剧宗师|三庆会创始人，被尊为川剧一代宗师\n沈铁梅|川剧表演艺术家|三度获得中国戏剧梅花奖' WHERE `id` = 1;
UPDATE `culture` SET `related_scenic_ids` = '10,9,17', `video_url` = 'https://search.bilibili.com/all?keyword=%E8%9C%80%E7%BB%A3', `inheritors` = '郝淑萍|蜀绣国家级代表性传承人|致力于蜀绣技艺的系统传承\n孟德芝|蜀绣大师|以双面异色绣见长' WHERE `id` = 2;
UPDATE `culture` SET `related_scenic_ids` = '18,19,17', `video_url` = 'https://search.bilibili.com/all?keyword=%E5%B7%9D%E8%8F%9C', `inheritors` = '蓝光鉴|现代川菜奠基人|奠定现代川菜宴席格局\n史正良|川菜大师|以刀工与味型创新著称' WHERE `id` = 3;
UPDATE `culture` SET `related_scenic_ids` = '11,6,1', `video_url` = 'https://search.bilibili.com/all?keyword=%E8%8C%B6%E9%A9%AC%E5%8F%A4%E9%81%93', `inheritors` = '背夫|古道运输者群体|以人力将茶包驮过二郎山\n茶商|以茶易马的商帮|川滇藏贸易的纽带' WHERE `id` = 4;
UPDATE `culture` SET `related_scenic_ids` = '9,17,4', `video_url` = 'https://search.bilibili.com/all?keyword=%E4%B8%89%E5%9B%BD%E6%96%87%E5%8C%96', `inheritors` = '诸葛亮|蜀汉丞相|鞠躬尽瘁，治蜀有方\n关羽|蜀汉名将|忠义形象深入人心' WHERE `id` = 5;
UPDATE `culture` SET `related_scenic_ids` = '5,9,10', `video_url` = 'https://search.bilibili.com/all?keyword=%E4%B8%89%E6%98%9F%E5%A0%86', `inheritors` = '冯汉骥|考古学家|三星堆早期考古研究的开拓者\n古蜀工匠|青铜文明创造者|留下青铜神树与纵目面具' WHERE `id` = 6;
UPDATE `culture` SET `related_scenic_ids` = '14,8,7', `video_url` = 'https://search.bilibili.com/all?keyword=%E5%A4%A7%E7%86%8A%E7%8C%AB', `inheritors` = '胡锦矗|大熊猫研究奠基人|开创中国大熊猫野外生态研究\n张和民|大熊猫保护专家|长期扎根卧龙保护区' WHERE `id` = 7;
UPDATE `culture` SET `related_scenic_ids` = '19,18,17', `video_url` = 'https://search.bilibili.com/all?keyword=%E5%9B%9B%E5%B7%9D%E7%99%BD%E9%85%92', `inheritors` = '赖高淮|酿酒大师|长期从事浓香型白酒酿造与技艺传承\n酿酒匠人|传统酿制技艺群体|以窖池与制曲工艺代代相传' WHERE `id` = 8;

-- 16. user 扩展字段：一句话简介（仅首次执行时新增列）
SET @has_bio := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'bio');
SET @ddl_bio := IF(@has_bio = 0,
                   'ALTER TABLE `user` ADD COLUMN `bio` VARCHAR(120) DEFAULT NULL COMMENT ''一句话简介'' AFTER `avatar`',
                   'SELECT 1');
PREPARE stmt_bio FROM @ddl_bio;
EXECUTE stmt_bio;
DEALLOCATE PREPARE stmt_bio;

-- 17. 用户简介示例（可重复执行；仅本人可在个人中心修改）
UPDATE `user` SET `bio` = '一步一步，把蜀地走成自己的地图' WHERE `bio` IS NULL;

-- 18. 川菜标签补充「火锅」：让顶部搜索的热门词「火锅」也能命中（幂等）
UPDATE `culture`
SET `tags` = CONCAT(IFNULL(`tags`, ''), ',火锅')
WHERE `id` = 3
  AND IFNULL(`tags`, '') NOT LIKE '%火锅%';

-- ============================================================================
-- 19. 默认管理员账号（用户名 XM）
--     密码为 BCrypt 加密后的值；首次登录后台后请尽快修改密码。
--     使用 INSERT ... ON DUPLICATE KEY UPDATE：可重复执行，并会修正密码与角色。
--     另先清理早期示例账号 admin（仅当其邮箱仍为初始值时），避免残留。
-- ============================================================================
DELETE FROM `user`
WHERE `username` = 'admin' AND `email` = 'admin@shuyun-travel.com';

INSERT INTO `user` (`username`, `email`, `password`, `role`, `status`, `bio`)
VALUES ('XM',
        'xm@shuyun-travel.com',
        '$2a$10$Nqnze7pX3SQ00XLsRnq9.uwJXLJ5nGD3UYIifZCBkm.LYkO1NgUhq',
        'ADMIN',
        1,
        '系统管理员')
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`),
                        `role`     = 'ADMIN',
                        `status`   = 1,
                        `deleted`  = 0;

-- ============================================================================
-- 20. user_feedback 问题反馈表
--     用户后台「问题反馈」提交，管理员后台查看与处理
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_feedback` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL                COMMENT '提交用户ID',
    `type`        VARCHAR(30)           DEFAULT NULL   COMMENT '反馈类型',
    `content`     TEXT                                  COMMENT '反馈内容',
    `contact`     VARCHAR(100)          DEFAULT NULL   COMMENT '联系方式（可选）',
    `status`      TINYINT               DEFAULT 0      COMMENT '状态：0-待处理，1-已处理',
    `reply`       VARCHAR(500)          DEFAULT NULL   COMMENT '管理员回复',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '问题反馈表';

-- ============================================================================
-- 21. scenic_spot 扩展字段：推荐理由（仅首次执行时新增列，已存在则跳过）
-- ============================================================================
SET @has_reason := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scenic_spot' AND COLUMN_NAME = 'recommend_reason');
SET @ddl_reason := IF(@has_reason = 0,
                      'ALTER TABLE `scenic_spot` ADD COLUMN `recommend_reason` VARCHAR(255) DEFAULT NULL COMMENT ''推荐理由'' AFTER `tags`',
                      'SELECT 1');
PREPARE stmt_reason FROM @ddl_reason;
EXECUTE stmt_reason;
DEALLOCATE PREPARE stmt_reason;

-- ============================================================================
-- 22. culture 扩展字段：浏览量（仅首次执行时新增列） + 示例浏览数据（可重复执行）
-- ============================================================================
SET @has_cview := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'culture' AND COLUMN_NAME = 'view_count');
SET @ddl_cview := IF(@has_cview = 0,
                     'ALTER TABLE `culture` ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT ''浏览次数（人气）'' AFTER `tags`',
                     'SELECT 1');
PREPARE stmt_cview FROM @ddl_cview;
EXECUTE stmt_cview;
DEALLOCATE PREPARE stmt_cview;

UPDATE `culture` SET `view_count` = 2140 WHERE `id` = 1;
UPDATE `culture` SET `view_count` = 1760 WHERE `id` = 2;
UPDATE `culture` SET `view_count` = 2580 WHERE `id` = 3;
UPDATE `culture` SET `view_count` = 940  WHERE `id` = 4;
UPDATE `culture` SET `view_count` = 1320 WHERE `id` = 5;
UPDATE `culture` SET `view_count` = 1680 WHERE `id` = 6;
UPDATE `culture` SET `view_count` = 2960 WHERE `id` = 7;
UPDATE `culture` SET `view_count` = 1080 WHERE `id` = 8;

-- ============================================================================
-- 23. user 扩展字段：最后登录时间（仅首次执行时新增列），登录成功时由后端更新
-- ============================================================================
SET @has_login := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'last_login_time');
SET @ddl_login := IF(@has_login = 0,
                     'ALTER TABLE `user` ADD COLUMN `last_login_time` DATETIME DEFAULT NULL COMMENT ''最后登录时间'' AFTER `update_time`',
                     'SELECT 1');
PREPARE stmt_login FROM @ddl_login;
EXECUTE stmt_login;
DEALLOCATE PREPARE stmt_login;

-- ============================================================================
-- 24. user_note 扩展字段：审核状态 / 拒绝理由（仅首次执行时新增列）
-- ============================================================================
SET @has_audit := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_note' AND COLUMN_NAME = 'audit_status');
SET @ddl_audit := IF(@has_audit = 0,
                     'ALTER TABLE `user_note` ADD COLUMN `audit_status` TINYINT DEFAULT 0 COMMENT ''审核状态：0-待审核，1-已通过，2-已拒绝'' AFTER `tags`, ADD COLUMN `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT ''拒绝理由'' AFTER `audit_status`',
                     'SELECT 1');
PREPARE stmt_audit FROM @ddl_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- ============================================================================
-- 25. user_ai_chat 扩展字段：标记类型（仅首次执行时新增列）
--     0-未标记，1-优质对话，2-训练语料
-- ============================================================================
SET @has_mark := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_ai_chat' AND COLUMN_NAME = 'mark_type');
SET @ddl_mark := IF(@has_mark = 0,
                    'ALTER TABLE `user_ai_chat` ADD COLUMN `mark_type` TINYINT DEFAULT 0 COMMENT ''标记类型：0-未标记，1-优质对话，2-训练语料'' AFTER `tokens`',
                    'SELECT 1');
PREPARE stmt_mark FROM @ddl_mark;
EXECUTE stmt_mark;
DEALLOCATE PREPARE stmt_mark;

-- ============================================================================
-- 26. email_subscription 邮箱订阅表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `email_subscription` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `email`       VARCHAR(120) NOT NULL                COMMENT '订阅邮箱',
    `source`      VARCHAR(50)           DEFAULT NULL   COMMENT '订阅来源',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '邮箱订阅表';

-- 27. 邮箱订阅示例数据（可重复执行，已存在则跳过）
INSERT IGNORE INTO `email_subscription` (`id`, `email`, `source`) VALUES
(1, 'traveler01@example.com', '首页订阅'),
(2, 'wenlv@example.com',      '首页订阅'),
(3, 'shuyun-fan@example.com', '文化详情页'),
(4, 'panda-lover@example.com','景点详情页'),
(5, 'scenic@example.com',     '首页订阅');

-- ============================================================================
-- 28. user_notification 站内信表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL                COMMENT '接收用户ID',
    `title`       VARCHAR(120)          DEFAULT NULL   COMMENT '标题',
    `content`     VARCHAR(500)          DEFAULT NULL   COMMENT '内容',
    `type`        VARCHAR(30)           DEFAULT NULL   COMMENT '类型：NOTE_AUDIT / SYSTEM',
    `is_read`     TINYINT               DEFAULT 0      COMMENT '是否已读：0-未读，1-已读',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内信表';

-- ============================================================================
-- 29. backup_history 备份历史表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `backup_history` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `filename`    VARCHAR(255) NOT NULL                COMMENT '文件名',
    `tables`      VARCHAR(500)          DEFAULT NULL   COMMENT '包含的表（逗号分隔）',
    `size`        BIGINT                DEFAULT 0      COMMENT '文件大小（字节）',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '备份历史表';

-- ============================================================================
-- 30. 后台展示用测试假数据（幂等：显式主键 + INSERT IGNORE，可重复执行）
--     说明：使用“数字表”交叉连接生成序号，避开对递归 CTE 的版本依赖
-- ============================================================================

-- 30.1 50 条用户（id 1001..1050，密码与管理员一致，均为 123456 对应的 BCrypt）
INSERT IGNORE INTO `user` (`id`, `username`, `email`, `password`, `bio`, `role`, `status`, `create_time`)
SELECT 1001 + seq.n,
       CONCAT('traveler', 1001 + seq.n),
       CONCAT('traveler', 1001 + seq.n, '@example.com'),
       '$2a$10$Nqnze7pX3SQ00XLsRnq9.uwJXLJ5nGD3UYIifZCBkm.LYkO1NgUhq',
       '热爱蜀地风物的旅行者',
       'USER', 1,
       DATE_SUB(NOW(), INTERVAL (seq.n * 3) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
WHERE seq.n < 50;

-- 30.2 100 条景点浏览记录（user 1001..1050，景点 1..19）
INSERT IGNORE INTO `user_view_history`
(`id`, `user_id`, `target_type`, `target_id`, `target_name`, `cover_image`, `sub_title`, `view_time`)
SELECT 1001 + seq.n, 1001 + (seq.n % 50), 'SCENIC', s.id, s.name, s.cover_image, c.name,
       DATE_SUB(NOW(), INTERVAL (seq.n % 20) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
JOIN scenic_spot s ON s.id = 1 + (seq.n % 19)
LEFT JOIN city c ON c.id = s.city_id
WHERE seq.n < 100;

-- 30.3 30 条用户收藏记录
INSERT IGNORE INTO `user_collection` (`id`, `user_id`, `target_type`, `target_id`, `create_time`)
SELECT 1001 + seq.n, 1001 + (seq.n % 50), 'SCENIC', 1 + (seq.n % 19),
       DATE_SUB(NOW(), INTERVAL (seq.n % 15) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
WHERE seq.n < 30;

-- 30.4 20 条 AI 对话记录（10 段会话，每段 1 问 1 答）
INSERT IGNORE INTO `user_ai_chat`
(`id`, `user_id`, `session_id`, `role`, `content`, `tokens`, `mark_type`, `create_time`)
SELECT 1001 + seq.n, 1001 + (seq.n % 50), CONCAT('sess-seed-', seq.n DIV 2),
       IF(seq.n % 2 = 0, 'user', 'assistant'),
       IF(seq.n % 2 = 0,
          ELT(1 + (seq.n % 5), '帮我规划成都三日游', '川剧变脸有什么看点', '九寨沟什么时候去最美', '四川有哪些必吃的川菜', '今天成都天气怎么样'),
          '好的，我来为你整理一份建议，记得结合季节与同行人调整节奏。'),
       0, 0, DATE_SUB(NOW(), INTERVAL (seq.n % 10) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
WHERE seq.n < 20;

-- 30.5 15 条用户行程
INSERT IGNORE INTO `user_trip`
(`id`, `user_id`, `title`, `days`, `companion`, `interests`, `budget`, `status`, `create_time`)
SELECT 1001 + seq.n, 1001 + (seq.n % 50),
       CONCAT('川西自驾 ', 3 + (seq.n % 4), ' 日计划'),
       3 + (seq.n % 4),
       ELT(1 + (seq.n % 5), '独自', '情侣', '亲子', '父母', '朋友'),
       '自然风光,人文古迹,古镇',
       3000 + seq.n * 200, 1, DATE_SUB(NOW(), INTERVAL (seq.n % 18) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
WHERE seq.n < 15;

-- 30.6 45 条行程每日明细（15 条行程 × 3 天）
INSERT IGNORE INTO `user_trip_day`
(`id`, `trip_id`, `day_index`, `date`, `scenic_id`, `scenic_name`, `stay_duration`, `transport`, `food`, `tips`)
SELECT 1001 + seq.n, 1001 + (seq.n DIV 3), 1 + (seq.n % 3),
       DATE_ADD(CURDATE(), INTERVAL (seq.n % 3) DAY),
       s.id, s.name,
       ELT(1 + (seq.n % 3), '2 小时', '3 小时', '半天'),
       ELT(1 + (seq.n % 3), '自驾', '城际高铁', '步行'),
       '当地特色小吃',
       '早晚温差大，注意增减衣物'
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
JOIN scenic_spot s ON s.id = 1 + (seq.n % 19)
WHERE seq.n < 45;

-- 30.7 10 条用户笔记（审核状态 0/1/2 交错）
INSERT IGNORE INTO `user_note` (`id`, `user_id`, `title`, `content`, `tags`, `audit_status`, `create_time`)
SELECT 1001 + seq.n, 1001 + (seq.n % 50),
       CONCAT('旅行随笔 ', 1001 + seq.n),
       '这次出行收获很多，沿途的风景与美食都值得记录。成都的慢生活、都江堰的壮阔，都让人印象深刻。',
       '旅行,随笔', (seq.n % 3), DATE_SUB(NOW(), INTERVAL (seq.n % 12) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
WHERE seq.n < 10;

-- 30.8 5 条反馈记录
INSERT IGNORE INTO `user_feedback` (`id`, `user_id`, `type`, `content`, `contact`, `status`, `create_time`)
SELECT 1001 + seq.n, 1001 + (seq.n % 50), '功能建议',
       CONCAT('希望增加更多川西小众景点与实用攻略（编号 ', seq.n, '）'),
       CONCAT('traveler', 1001 + (seq.n % 50), '@example.com'),
       (seq.n % 2), DATE_SUB(NOW(), INTERVAL (seq.n % 6) DAY)
FROM (SELECT a.n + b.n * 10 AS n
      FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) seq
WHERE seq.n < 5;

-- ============================================================================
-- 31. user_ai_chat 扩展字段：调用观测指标（仅首次执行时新增列）
--     记录每次 AI 请求的输入/输出长度、耗时、是否成功
-- ============================================================================
SET @has_ai_metrics := (SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_ai_chat' AND COLUMN_NAME = 'input_len');
SET @ddl_ai_metrics := IF(@has_ai_metrics = 0,
                          'ALTER TABLE `user_ai_chat` ADD COLUMN `input_len` INT DEFAULT NULL COMMENT ''输入长度（字符数）'' AFTER `mark_type`, ADD COLUMN `output_len` INT DEFAULT NULL COMMENT ''输出长度（字符数）'' AFTER `input_len`, ADD COLUMN `duration_ms` BIGINT DEFAULT NULL COMMENT ''调用耗时（毫秒）'' AFTER `output_len`, ADD COLUMN `success` TINYINT DEFAULT NULL COMMENT ''是否成功：0-失败，1-成功'' AFTER `duration_ms`',
                          'SELECT 1');
PREPARE stmt_ai_metrics FROM @ddl_ai_metrics;
EXECUTE stmt_ai_metrics;
DEALLOCATE PREPARE stmt_ai_metrics;

-- ============================================================================
-- 32. food_shop 美食店铺表（供 AI 工具 recommendFood 查询）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `food_shop` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(100) NOT NULL                COMMENT '店铺名称',
    `city`        VARCHAR(50)           DEFAULT NULL   COMMENT '所属城市',
    `taste`       VARCHAR(50)           DEFAULT NULL   COMMENT '口味 / 类型',
    `address`     VARCHAR(255)          DEFAULT NULL   COMMENT '地址',
    `price`       DECIMAL(10, 2)        DEFAULT NULL   COMMENT '人均价格（元）',
    `rating`      DECIMAL(3, 1)         DEFAULT NULL   COMMENT '评分（0-5）',
    `description` VARCHAR(255)          DEFAULT NULL   COMMENT '简介',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_city` (`city`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '美食店铺表';

-- 33. 美食店铺示例数据（幂等：显式主键 + INSERT IGNORE）
INSERT IGNORE INTO `food_shop` (`id`, `name`, `city`, `taste`, `address`, `price`, `rating`, `description`) VALUES
(1,  '陈麻婆豆腐（青羊店）', '成都', '麻辣',   '成都市青羊区西玉龙街',        68.00, 4.7, '百年老字号，麻婆豆腐麻辣鲜香烫'),
(2,  '小龙翻大江火锅',       '成都', '火锅',   '成都市锦江区春熙路',         128.00, 4.6, '成都人气火锅，锅底香浓、菜品新鲜'),
(3,  '钟水饺（总府路店）',   '成都', '小吃',   '成都市锦江区总府路',          35.00, 4.5, '成都名小吃，红油水饺皮薄馅嫩'),
(4,  '马旺子川小馆',         '成都', '川菜',   '成都市武侯区人民南路',        95.00, 4.8, '新派川菜，回锅肉与宫保鸡丁招牌'),
(5,  '乐山鲜知味钵钵鸡',     '乐山', '小吃',   '乐山市市中区张公桥好吃街',     45.00, 4.7, '乐山钵钵鸡代表，藤椒与红油双味'),
(6,  '甜皮鸭（赵鸭子）',     '乐山', '卤味',   '乐山市市中区嘉定中路',        40.00, 4.6, '外皮酥甜、肉质细嫩，乐山必吃'),
(7,  '自贡盐帮菜·鲜味馆',   '自贡', '鲜辣',   '自贡市自流井区五星街',        88.00, 4.6, '盐帮菜代表，冷吃兔与水煮牛肉'),
(8,  '宜宾燃面（老字号）',   '宜宾', '面食',   '宜宾市翠屏区西街',            28.00, 4.5, '宜宾燃面，油重无水、点火即燃'),
(9,  '泸州老窖美酒美食坊',   '泸州', '酒文化', '泸州市江阳区三星街',         110.00, 4.5, '浓香型白酒搭配川南家常菜'),
(10, '绵阳米粉（开元店）',   '绵阳', '小吃',   '绵阳市涪城区临园路',          22.00, 4.4, '绵阳米粉细滑入味，早餐首选'),
(11, '南充川北凉粉',         '南充', '小吃',   '南充市顺庆区人民中路',        20.00, 4.5, '川北凉粉麻辣爽口，夏日必吃'),
(12, '都江堰老腊肉馆',       '成都', '农家菜', '成都市都江堰市幸福路',        75.00, 4.6, '青城山老腊肉与山野菜，配青城茶');

-- ============================================================================
-- 34. user_profile 用户画像表（供 AI 助手注入个性化上下文）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_profile` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT      NOT NULL                COMMENT '用户ID',
    `age_range`   VARCHAR(20)          DEFAULT NULL   COMMENT '年龄段',
    `gender`      VARCHAR(10)          DEFAULT NULL   COMMENT '性别',
    `region`      VARCHAR(50)          DEFAULT NULL   COMMENT '常驻地域（市州）',
    `interests`   VARCHAR(255)         DEFAULT NULL   COMMENT '兴趣偏好（逗号分隔）',
    `companion`   VARCHAR(50)          DEFAULT NULL   COMMENT '同行类型',
    `budget`      VARCHAR(50)          DEFAULT NULL   COMMENT '预算范围',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT              DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户画像表';

-- 35. 用户画像示例数据（幂等：显式主键 + INSERT IGNORE）
INSERT IGNORE INTO `user_profile` (`id`, `user_id`, `age_range`, `gender`, `region`, `interests`, `companion`, `budget`) VALUES
(1, 1001, '26-35', '女', '成都', '自然风光,美食', '情侣', '3000-5000 元'),
(2, 1002, '18-25', '男', '绵阳', '人文古迹,摄影', '朋友', '1000-3000 元'),
(3, 1003, '36-45', '女', '乐山', '亲子,古镇',   '亲子', '5000-8000 元'),
(4, 1004, '26-35', '男', '宜宾', '红色旅游,历史', '独自', '1000-3000 元'),
(5, 1005, '46 岁以上', '女', '成都', '文化,养生', '父母', '3000-5000 元');
