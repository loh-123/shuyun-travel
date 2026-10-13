-- ============================================================================
-- 蜀韵·智游（shuyun-travel）数据库结构对齐脚本  fix_columns.sql
-- ============================================================================
-- 作用：
--   把「实体类有、数据库没有」的字段/表补齐，修复启动后接口报错：
--     Unknown column 'website' in 'field list'
--     Table 'shuyun.food' doesn't exist  等
--
-- 幂等说明（重要）：
--   * 您提到的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 语法在
--     **MySQL 8.0 并不支持**（实测 8.0.44 报 1064 语法错误，只有 MariaDB 支持）。
--     因此这里统一采用「查 INFORMATION_SCHEMA 判断后再执行」的等价幂等写法，
--     init.sql 也是这么做的。可重复执行，已存在的列/表会自动跳过。
--
-- 执行方式（在项目根目录）：
--   mysql -u root -p123456 --default-character-set=utf8mb4 -D shuyun < shuyun-web/src/main/resources/db/fix_columns.sql
--
-- 说明：本脚本只对齐结构，不改动任何已有数据。
-- ============================================================================

SET NAMES utf8mb4;

-- ############################################################################
-- 第一部分：7 张业务表（本次报错直接相关）
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 1. scenic_spot：补 website（实体新增，库中缺此列）
-- ----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scenic_spot' AND COLUMN_NAME = 'website');
SET @ddl := IF(@has = 0,
               'ALTER TABLE `scenic_spot` ADD COLUMN `website` VARCHAR(500) DEFAULT NULL COMMENT ''相关网址'' AFTER `address`',
               'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. food：整张表不存在，直接建表（已存在则跳过）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `food` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`            VARCHAR(100)   NOT NULL                COMMENT '美食名称',
    `video_url`       VARCHAR(500)           DEFAULT NULL    COMMENT '视频地址',
    `category`        VARCHAR(50)            DEFAULT NULL    COMMENT '分类',
    `city_name`       VARCHAR(50)            DEFAULT NULL    COMMENT '所属城市',
    `cover_image`     VARCHAR(255)           DEFAULT NULL    COMMENT '封面图',
    `description`     TEXT                                   COMMENT '简介',
    `history`         TEXT                                   COMMENT '历史故事',
    `spicy_level`     TINYINT                DEFAULT NULL    COMMENT '辣度（1-5）',
    `recommend_index` TINYINT                DEFAULT NULL    COMMENT '推荐指数（1-5）',
    `avg_price`       DECIMAL(10, 2)         DEFAULT NULL    COMMENT '人均价格（元）',
    `shop_name`       VARCHAR(100)           DEFAULT NULL    COMMENT '代表店铺',
    `shop_address`    VARCHAR(255)           DEFAULT NULL    COMMENT '店铺地址',
    `shop_longitude`  DECIMAL(10, 6)         DEFAULT NULL    COMMENT '店铺经度',
    `shop_latitude`   DECIMAL(10, 6)         DEFAULT NULL    COMMENT '店铺纬度',
    `shop_image`      VARCHAR(255)           DEFAULT NULL    COMMENT '店铺图片',
    `create_time`     DATETIME               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT                DEFAULT 0       COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_city` (`city_name`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '美食表';

-- ----------------------------------------------------------------------------
-- 3. culture：补 website（URL，VARCHAR(500)）
-- ----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'culture' AND COLUMN_NAME = 'website');
SET @ddl := IF(@has = 0,
               'ALTER TABLE `culture` ADD COLUMN `website` VARCHAR(500) DEFAULT NULL COMMENT ''简介网址'' AFTER `content`',
               'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.2 culture：补 related_scenic（相关景点，逗号分隔）
SET @has := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'culture' AND COLUMN_NAME = 'related_scenic');
SET @ddl := IF(@has = 0,
               'ALTER TABLE `culture` ADD COLUMN `related_scenic` VARCHAR(500) DEFAULT NULL COMMENT ''相关景点（逗号分隔）'' AFTER `website`',
               'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.3 culture：补 related_person（相关人物，逗号分隔）
SET @has := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'culture' AND COLUMN_NAME = 'related_person');
SET @ddl := IF(@has = 0,
               'ALTER TABLE `culture` ADD COLUMN `related_person` VARCHAR(255) DEFAULT NULL COMMENT ''相关人物（逗号分隔）'' AFTER `related_scenic`',
               'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. heritage：整张表不存在，直接建表（已存在则跳过）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `heritage` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(100) NOT NULL                COMMENT '项目名称',
    `website`     VARCHAR(500)          DEFAULT NULL   COMMENT '简介网址',
    `category`    VARCHAR(50)           DEFAULT NULL   COMMENT '类别',
    `level`       VARCHAR(20)           DEFAULT NULL   COMMENT '级别',
    `inheritor`   VARCHAR(255)          DEFAULT NULL   COMMENT '传承人',
    `summary`     TEXT                                 COMMENT '简介',
    `video_url`   VARCHAR(500)          DEFAULT NULL   COMMENT '视频地址',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '非物质文化遗产表';

-- ----------------------------------------------------------------------------
-- 5. culture_image：整张表不存在，直接建表（已存在则跳过）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `culture_image` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `culture_id`  BIGINT       NOT NULL                COMMENT '文化ID',
    `image_url`   VARCHAR(255) NOT NULL                COMMENT '图片地址',
    `sort_order`  INT                   DEFAULT 0      COMMENT '排序',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_culture_id` (`culture_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文化图片表';

-- ----------------------------------------------------------------------------
-- 6. heritage_image：整张表不存在，直接建表（已存在则跳过）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `heritage_image` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `heritage_id` BIGINT       NOT NULL                COMMENT '非遗ID',
    `image_url`   VARCHAR(255) NOT NULL                COMMENT '图片地址',
    `sort_order`  INT                   DEFAULT 0      COMMENT '排序',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_heritage_id` (`heritage_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '非遗图片表';

-- 注：scenic_image 实体与库结构完全一致，无需处理。

-- ############################################################################
-- 第二部分：附录（超出 7 张表范围，但同样会导致接口报错，一并修复）
-- ############################################################################

-- A. user_ai_chat：补观测/标记字段（实体有、库没有）
SET @has := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_ai_chat' AND COLUMN_NAME = 'mark_type');
SET @ddl := IF(@has = 0,
               'ALTER TABLE `user_ai_chat` ADD COLUMN `mark_type` TINYINT DEFAULT 0 COMMENT ''标记类型：0-未标记，1-优质对话，2-训练语料'' AFTER `tokens`, ADD COLUMN `input_len` INT DEFAULT NULL COMMENT ''输入长度'' AFTER `mark_type`, ADD COLUMN `output_len` INT DEFAULT NULL COMMENT ''输出长度'' AFTER `input_len`, ADD COLUMN `duration_ms` BIGINT DEFAULT NULL COMMENT ''调用耗时（毫秒）'' AFTER `output_len`, ADD COLUMN `success` TINYINT DEFAULT NULL COMMENT ''是否成功：0-失败，1-成功'' AFTER `duration_ms`',
               'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- B. user_note：补审核字段（实体有、库没有）
SET @has := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_note' AND COLUMN_NAME = 'audit_status');
SET @ddl := IF(@has = 0,
               'ALTER TABLE `user_note` ADD COLUMN `audit_status` TINYINT DEFAULT 0 COMMENT ''审核状态：0-待审核，1-已通过，2-已拒绝'' AFTER `tags`, ADD COLUMN `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT ''拒绝理由'' AFTER `audit_status`',
               'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- C. food_shop：整张表不存在（供 AI 工具 recommendFood 使用）
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

-- C.2 email_subscription：整张表不存在（首页邮箱订阅）
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

-- C.3 user_notification：整张表不存在（站内信）
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

-- C.4 backup_history：整张表不存在（后台备份记录）
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

-- C.5 user_profile：整张表不存在（用户画像，供 AI 注入上下文）
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

-- ============================================================================
-- 自检（执行后分别应返回：1 / 3 / 4 / 5 / 1）
--   SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
--     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='scenic_spot' AND COLUMN_NAME='website';
--   SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
--     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='culture' AND COLUMN_NAME IN ('website','related_scenic','related_person');
--   SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
--     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('food','heritage','culture_image','heritage_image');
--   SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
--     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('food_shop','email_subscription','user_notification','backup_history','user_profile');
--   SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
--     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_ai_chat' AND COLUMN_NAME='mark_type';
-- ============================================================================
