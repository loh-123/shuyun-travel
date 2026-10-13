# -*- coding: utf-8 -*-
"""
蜀韵·智游（shuyun-travel）Excel -> SQL 数据导入脚本
=====================================================

使用方法（在项目根目录执行）：
    python tools/excel2sql.py

依赖安装：
    pip install pandas openpyxl

输入（项目根目录 shuyun-data/ 下）：
    01-景点.xlsx    -> scenic_spot  + scenic_image  + update_scenic_video.sql
    02-美食.xlsx    -> food
    03-文化.xlsx    -> culture     + culture_image
    04-非遗.xlsx    -> heritage    + heritage_image

输出：
    shuyun-web/src/main/resources/db/data.sql                （7 张业务表 upsert）
    shuyun-web/src/main/resources/db/update_scenic_video.sql （景点视频链接回填，仅 UPDATE）

处理规则：
    1. 图片路径前缀替换：
         "景点图片/" -> "/images/scenic/"
         "美食图片/" -> "/images/food/"
         "文化图片/" -> "/images/culture/"
         "非遗图片/" -> "/images/heritage/"
       已是 http(s) 开头的绝对地址保持原样。
    2. 城市映射：Excel 中的城市名（如「成都市」「凉山州」）去掉「市 / 州 / 自治州」
       后缀后，用子查询 (SELECT id FROM city WHERE name = '成都') 匹配 city 表，
       写入 scenic_spot.city_id；food 表则直接保存规范化的城市名到 city_name。
    3. 空单元格 / 「—」「——」占位符统一转成 NULL。
    4. 字符串中的反斜杠与单引号做转义（' -> \\'），避免 SQL 语法错误。
    5. 每 500 条合并为一批，使用 INSERT ... ON DUPLICATE KEY UPDATE（upsert）。
    6. 导出文件使用 UTF-8（无 BOM），避免命令行 / mysql 客户端中文乱码。
    7. 导入可重复执行且不破坏数据：不会 DROP / TRUNCATE，仅按主键 upsert，
       Excel 中不存在的行（如手工新增数据）会被保留。
    8. URL 类字段（网址 / 视频链接）：非 http(s) 值按规则处理——「xxx_百度百科」
       转为百度百科词条链接（URL 编码），其余非 URL 文本一律置为 NULL。
    9. 景点视频回填：读取 01-景点.xlsx 的「视频网址」列（R 列），按「名称」生成
       UPDATE scenic_spot SET video_url = '...' WHERE name = '...'，
       单独输出到 update_scenic_video.sql，不写入 data.sql，便于单独执行。

注意：
    - data.sql 只对 7 张业务表做 CREATE TABLE IF NOT EXISTS + upsert，不删表、不清空。
    - update_scenic_video.sql 只做 UPDATE 回填 video_url，不新增 / 不删除任何数据。
    - scenic_spot.city_id 通过子查询 (SELECT id FROM city WHERE name = '成都') 匹配，
      脚本会同时确保 city 表及其 21 个市州基础数据存在。
"""

import glob
import os
import sys
from datetime import datetime
from urllib.parse import quote

try:
    import pandas as pd
except ImportError:
    sys.exit("未安装 pandas，请先执行：pip install pandas openpyxl")

try:
    import openpyxl  # noqa: F401  仅用于确保 openpyxl 已安装（pandas 读取 xlsx 依赖）
except ImportError:
    sys.exit("未安装 openpyxl，请先执行：pip install pandas openpyxl")


# ---------------------------------------------------------------------------
# 路径常量
# ---------------------------------------------------------------------------
TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(TOOLS_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "shuyun-data")
OUT_PATH = os.path.join(
    PROJECT_ROOT, "shuyun-web", "src", "main", "resources", "db", "data.sql"
)
OUT_VIDEO_PATH = os.path.join(
    PROJECT_ROOT, "shuyun-web", "src", "main", "resources", "db", "update_scenic_video.sql"
)

# 图片前缀替换规则
IMAGE_PREFIX_MAP = {
    "景点图片/": "/images/scenic/",
    "美食图片/": "/images/food/",
    "文化图片/": "/images/culture/",
    "非遗图片/": "/images/heritage/",
}
IMAGE_PREFIX_SCENIC = ("景点图片/", "/images/scenic/")
IMAGE_PREFIX_FOOD = ("美食图片/", "/images/food/")
IMAGE_PREFIX_CULTURE = ("文化图片/", "/images/culture/")
IMAGE_PREFIX_HERITAGE = ("非遗图片/", "/images/heritage/")

# 市州名 -> id（与 init.sql 保持一致）
CITY_IDS = {
    "成都": 1, "绵阳": 2, "德阳": 3, "自贡": 4, "攀枝花": 5, "泸州": 6,
    "广元": 7, "遂宁": 8, "内江": 9, "乐山": 10, "南充": 11, "眉山": 12,
    "宜宾": 13, "广安": 14, "达州": 15, "雅安": 16, "巴中": 17, "资阳": 18,
    "阿坝": 19, "甘孜": 20, "凉山": 21,
}

# 市州基础数据（id, name, code, description, cover_image）
CITY_SEED = [
    (1, "成都", "510100", "四川省省会，天府之国，美食与休闲之都"),
    (2, "绵阳", "510700", "中国科技城，拥有越王楼与富乐山"),
    (3, "德阳", "510600", "重装之都，三星堆遗址所在地"),
    (4, "自贡", "510300", "千年盐都、恐龙之乡、南国灯城"),
    (5, "攀枝花", "510400", "阳光花城，康养胜地"),
    (6, "泸州", "510500", "中国酒城，长江与沱江交汇"),
    (7, "广元", "510800", "女皇故里，剑门关所在地"),
    (8, "遂宁", "510900", "观音故里，涪江之畔的宜居之城"),
    (9, "内江", "511000", "甜城，张大千故里"),
    (10, "乐山", "511100", "乐山大佛与峨眉山所在，山水人文名城"),
    (11, "南充", "511300", "丝绸之都，三国文化名城"),
    (12, "眉山", "511400", "三苏故里，东坡文化之乡"),
    (13, "宜宾", "511500", "中国酒都，长江首城"),
    (14, "广安", "511600", "邓小平故里，红色旅游之城"),
    (15, "达州", "511700", "巴人故里，川东门户"),
    (16, "雅安", "511800", "雨城，熊猫故乡"),
    (17, "巴中", "511900", "川陕革命根据地中心，红叶之乡"),
    (18, "资阳", "512000", "蜀人原乡，三贤故里"),
    (19, "阿坝", "513200", "阿坝藏族羌族自治州，九寨沟与黄龙所在"),
    (20, "甘孜", "513300", "甘孜藏族自治州，稻城亚丁与贡嘎山所在"),
    (21, "凉山", "513400", "中国最大的彝族聚居区，邛海与泸沽湖风光秀丽"),
]

# NULL 占位符（Excel 中用于表示「无」的符号）
NULL_TOKENS = {"", "-", "--", "—", "——", "－", "－", "无", "null", "NULL", "None", "nan"}

# 每批 INSERT 的行数
BATCH_SIZE = 500

# 统计各表插入记录数
STATS = {}

# 记录 URL 字段的清洗明细（用于最后打印）
URL_FIXES = []


# ---------------------------------------------------------------------------
# 通用工具函数
# ---------------------------------------------------------------------------
def norm_text(value):
    """把单元格值规范化为字符串：空值 / 占位符返回 None。"""
    if value is None:
        return None
    if isinstance(value, float) and pd.isna(value):
        return None
    text = str(value).strip()
    if text in NULL_TOKENS:
        return None
    return text


def q(value):
    """转义并生成 SQL 字符串字面量；None -> NULL。"""
    if value is None:
        return "NULL"
    text = str(value).replace("\\", "\\\\").replace("'", "\\'")
    return "'" + text + "'"


def to_float(value):
    """转为 float；无法解析时返回 None。"""
    text = norm_text(value)
    if text is None:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def dec(value, places):
    """生成 DECIMAL 字面量；无效时 NULL。"""
    number = to_float(value)
    if number is None:
        return "NULL"
    return ("%%.%df" % places) % number


def integer(value):
    """生成 INT 字面量；无效时 NULL。"""
    number = to_float(value)
    if number is None:
        return "NULL"
    return str(int(round(number)))


def norm_image(value, prefix_rule):
    """规范化单张图片路径。"""
    text = norm_text(value)
    if text is None:
        return None
    text = text.replace("\\", "/").strip()
    if text.startswith("http://") or text.startswith("https://"):
        return text
    zh_prefix, en_prefix = prefix_rule
    text = text.replace(zh_prefix, en_prefix)
    if not text.startswith("/"):
        text = "/" + text
    return text


def split_images(value, prefix_rule):
    """拆分逗号分隔的图片串并逐张规范化。"""
    text = norm_text(value)
    if text is None:
        return []
    parts = text.replace("，", ",").split(",")
    result = []
    for part in parts:
        url = norm_image(part, prefix_rule)
        if url:
            result.append(url)
    return result


def norm_city_name(value):
    """规范化城市名：去掉「市 / 州 / 自治州 / 地区」后缀。"""
    text = norm_text(value)
    if text is None:
        return None
    for suffix in ("自治州", "地区", "市", "州"):
        if text.endswith(suffix) and len(text) > len(suffix):
            text = text[: -len(suffix)]
            break
    return text


def city_id_of(value):
    """城市名 -> city 表 id（脚本内校验用）；未知城市返回 None。"""
    name = norm_city_name(value)
    if name is None:
        return None
    return CITY_IDS.get(name)


def city_id_sql(city_name):
    """生成 scenic_spot.city_id 的 SQL 片段：按 city 表名字匹配（子查询）。"""
    if city_name is None:
        return "NULL"
    return "(SELECT `id` FROM `city` WHERE `name` = %s)" % q(city_name)


def clean_url(value, field_label):
    """校验 URL 类字段（网址 / 视频链接），返回合法 URL 或 None。

    - 空值 -> None
    - http / https 开头 -> 原样保留
    - 「xxx_百度百科」 -> 转为百度百科词条链接（URL 编码）
    - 其它非 URL 文本 -> None（置空，不影响插入）
    """
    text = norm_text(value)
    if text is None:
        return None
    if text.startswith("http://") or text.startswith("https://"):
        return text
    if text.endswith("_百度百科"):
        keyword = text[: -len("_百度百科")].strip()
        url = "https://baike.baidu.com/item/" + quote(keyword)
        URL_FIXES.append((field_label, text, url))
        return url
    URL_FIXES.append((field_label, text, None))
    return None


# ---------------------------------------------------------------------------
# 表结构 DDL
# ---------------------------------------------------------------------------
DDL_CITY = """\
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '市州表';"""

DDL_SCENIC_SPOT = """\
CREATE TABLE IF NOT EXISTS `scenic_spot` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`             VARCHAR(100)   NOT NULL                COMMENT '景点名称',
    `city_id`          BIGINT                 DEFAULT NULL    COMMENT '所属市州ID',
    `type`             VARCHAR(50)            DEFAULT NULL    COMMENT '景点类型',
    `level`            VARCHAR(20)            DEFAULT NULL    COMMENT '景区等级',
    `address`          VARCHAR(255)           DEFAULT NULL    COMMENT '地址',
    `website`          VARCHAR(500)           DEFAULT NULL    COMMENT '相关网址',
    `longitude`        DECIMAL(10, 6)         DEFAULT NULL    COMMENT '经度',
    `latitude`         DECIMAL(10, 6)         DEFAULT NULL    COMMENT '纬度',
    `cover_image`      VARCHAR(255)           DEFAULT NULL    COMMENT '封面图',
    `description`      TEXT                                   COMMENT '景点介绍',
    `open_time`        VARCHAR(100)           DEFAULT NULL    COMMENT '开放时间',
    `ticket_info`      VARCHAR(255)           DEFAULT NULL    COMMENT '门票信息',
    `score`            DECIMAL(3, 1)          DEFAULT 5.0     COMMENT '评分',
    `tags`             VARCHAR(255)           DEFAULT NULL    COMMENT '标签（逗号分隔）',
    `recommend_reason` VARCHAR(255)           DEFAULT NULL    COMMENT '推荐理由',
    `view_count`       INT                    NOT NULL DEFAULT 0 COMMENT '浏览次数（人气）',
    `create_time`      DATETIME               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT                DEFAULT 0       COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_city_id` (`city_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '景点表';"""

DDL_SCENIC_IMAGE = """\
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '景点图片表';"""

DDL_FOOD = """\
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '美食表';"""

DDL_CULTURE = """\
CREATE TABLE IF NOT EXISTS `culture` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`               VARCHAR(50)  NOT NULL                COMMENT '名称',
    `category`           VARCHAR(50)           DEFAULT NULL   COMMENT '分类',
    `cover_image`        VARCHAR(255)          DEFAULT NULL   COMMENT '封面图',
    `summary`            VARCHAR(500)          DEFAULT NULL   COMMENT '摘要',
    `content`            LONGTEXT                             COMMENT '正文',
    `related_scenic_ids` VARCHAR(100)          DEFAULT NULL   COMMENT '关联景点ID（逗号分隔）',
    `inheritors`         TEXT                                 COMMENT '传承人（每行一条：姓名|身份|一句话）',
    `website`            VARCHAR(500)          DEFAULT NULL   COMMENT '简介网址',
    `related_scenic`     VARCHAR(500)          DEFAULT NULL   COMMENT '相关景点（逗号分隔）',
    `related_person`     VARCHAR(255)          DEFAULT NULL   COMMENT '相关人物（逗号分隔）',
    `video_url`          VARCHAR(500)          DEFAULT NULL   COMMENT '视频地址',
    `tags`               VARCHAR(255)          DEFAULT NULL   COMMENT '标签（逗号分隔）',
    `view_count`         INT         NOT NULL DEFAULT 0      COMMENT '浏览次数（人气）',
    `create_time`        DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT               DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文化表';"""

DDL_CULTURE_IMAGE = """\
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文化图片表';"""

DDL_HERITAGE = """\
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '非物质文化遗产表';"""

DDL_HERITAGE_IMAGE = """\
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '非遗图片表';"""


# ---------------------------------------------------------------------------
# 读取 Excel
# ---------------------------------------------------------------------------
def read_excel(pattern):
    """按通配符定位并读取 Excel（兼容 01-景点..xlsx 这类双点文件名）。"""
    matches = glob.glob(os.path.join(DATA_DIR, pattern))
    if not matches:
        raise FileNotFoundError("未找到匹配的 Excel：%s" % pattern)
    path = matches[0]
    df = pd.read_excel(path, dtype=str)
    print("读取 %-22s 共 %d 行" % (os.path.basename(path), len(df)))
    return df


# ---------------------------------------------------------------------------
# 生成 VALUES 片段
# ---------------------------------------------------------------------------
def build_upsert(table, columns, row_tuples):
    """把数据行拼装成批量 upsert 语句（每 BATCH_SIZE 条一批）。

    使用 INSERT ... ON DUPLICATE KEY UPDATE：不删表、不清空，按主键覆盖更新，
    重复执行安全，并且不会影响 Excel 之外的数据（如手工新增行）。
    """
    if not row_tuples:
        return "-- %s：无数据\n" % table
    col_sql = ", ".join("`%s`" % c for c in columns)
    update_sql = ", ".join("`%s` = VALUES(`%s`)" % (c, c)
                          for c in columns if c != "id")
    statements = []
    for start in range(0, len(row_tuples), BATCH_SIZE):
        chunk = row_tuples[start:start + BATCH_SIZE]
        statements.append(
            "INSERT INTO `%s` (%s) VALUES\n%s\nON DUPLICATE KEY UPDATE %s;" % (
                table, col_sql, ",\n".join(chunk), update_sql))
    return "\n".join(statements) + "\n"


def format_value(value):
    """把一行数据格式化为 (col_value, col_value, ...) 片段。"""
    return "(" + ", ".join(value) + ")"


# ---------------------------------------------------------------------------
# 各表转换
# ---------------------------------------------------------------------------
def gen_scenic(df):
    spot_rows = []
    image_rows = []
    scenic_id = 0
    unknown_city = set()
    for _, row in df.iterrows():
        scenic_id += 1
        city_name = norm_city_name(row.get("所属城市"))
        cid = CITY_IDS.get(city_name)
        if city_name is not None and cid is None:
            unknown_city.add(city_name)
        values = [
            str(scenic_id),
            q(norm_text(row.get("名称"))),
            city_id_sql(city_name),
            q(norm_text(row.get("类型"))),
            q(norm_text(row.get("等级"))),
            q(norm_text(row.get("地址"))),
            q(clean_url(row.get("网址"), "scenic_spot.website")),
            dec(row.get("经度"), 6),
            dec(row.get("纬度"), 6),
            q(norm_image(row.get("封面图"), IMAGE_PREFIX_SCENIC)),
            q(norm_text(row.get("简介"))),
            q(norm_text(row.get("开放时间"))),
            q(norm_text(row.get("门票"))),
            dec(row.get("评分"), 1),
            q(norm_text(row.get("标签"))),
            q(norm_text(row.get("推荐理由"))),
        ]
        spot_rows.append(format_value(values))
        for order, url in enumerate(split_images(row.get("详情图"), IMAGE_PREFIX_SCENIC)):
            image_rows.append("(%d, %d, %s, %d)" % (
                len(image_rows) + 1, scenic_id, q(url), order))

    spot_cols = [
        "id", "name", "city_id", "type", "level", "address", "website",
        "longitude", "latitude", "cover_image", "description", "open_time",
        "ticket_info", "score", "tags", "recommend_reason",
    ]
    image_cols = ["id", "scenic_id", "image_url", "sort_order"]
    if unknown_city:
        print("  [警告] 未匹配到 city 表的城市：%s" % "、".join(sorted(unknown_city)))
    STATS["scenic_spot"] = len(spot_rows)
    STATS["scenic_image"] = len(image_rows)
    return [
        ("scenic_spot", DDL_SCENIC_SPOT, build_upsert("scenic_spot", spot_cols, spot_rows)),
        ("scenic_image", DDL_SCENIC_IMAGE, build_upsert("scenic_image", image_cols, image_rows)),
    ]


def gen_scenic_video(df):
    """生成景点视频链接回填脚本（UPDATE ... WHERE name = ...）。

    - 景点名称取「名称」列（B 列），视频取「视频网址」列（R 列）。
    - 仅回填 clean_url 判定为合法 URL 的行（B 站 / 央视网 / 扬帆等）；
      非 URL 文本一律跳过并在控制台提示，避免写入脏链接。
    - 仅生成 UPDATE 语句，不新增 / 不删除数据，可重复执行。
    """
    lines = []
    skipped = []
    for _, row in df.iterrows():
        name = norm_text(row.get("名称"))
        url = clean_url(row.get("视频网址"), "scenic_spot.video_url")
        if name is None:
            continue
        if url is None:
            skipped.append(name)
            continue
        lines.append(
            "UPDATE `scenic_spot` SET `video_url` = %s WHERE `name` = %s;"
            % (q(url), q(name)))
    STATS["scenic_video"] = len(lines)
    if skipped:
        print("  [提示] 以下景点无有效视频网址，已跳过：%s" % "、".join(skipped))
    return lines


def gen_food(df):
    rows = []
    food_id = 0
    for _, row in df.iterrows():
        food_id += 1
        values = [
            str(food_id),
            q(norm_text(row.get("名称"))),
            q(clean_url(row.get("视频网址"), "food.video_url")),
            q(norm_text(row.get("分类"))),
            q(norm_city_name(row.get("所属城市"))),
            q(norm_image(row.get("封面图"), IMAGE_PREFIX_FOOD)),
            q(norm_text(row.get("简介"))),
            q(norm_text(row.get("历史故事"))),
            integer(row.get("辣度")),
            integer(row.get("推荐指数")),
            dec(row.get("人均价格"), 2),
            q(norm_text(row.get("代表店铺"))),
            q(norm_text(row.get("店铺地址"))),
            dec(row.get("店铺经度"), 6),
            dec(row.get("店铺纬度"), 6),
            q(norm_image(row.get("店铺图片"), IMAGE_PREFIX_FOOD)),
        ]
        rows.append(format_value(values))

    cols = [
        "id", "name", "video_url", "category", "city_name", "cover_image",
        "description", "history", "spicy_level", "recommend_index", "avg_price",
        "shop_name", "shop_address", "shop_longitude", "shop_latitude", "shop_image",
    ]
    STATS["food"] = len(rows)
    return [("food", DDL_FOOD, build_upsert("food", cols, rows))]


def gen_culture(df):
    culture_rows = []
    image_rows = []
    culture_id = 0
    for _, row in df.iterrows():
        culture_id += 1
        values = [
            str(culture_id),
            q(norm_text(row.get("名称"))),
            q(norm_text(row.get("分类"))),
            q(norm_image(row.get("封面图"), IMAGE_PREFIX_CULTURE)),
            q(norm_text(row.get("简介"))),
            q(norm_text(row.get("详细介绍"))),
            q(clean_url(row.get("简介网址"), "culture.website")),
            q(norm_text(row.get("相关景点"))),
            q(norm_text(row.get("相关人物"))),
            q(clean_url(row.get("视频链接"), "culture.video_url")),
            q(norm_text(row.get("标签"))),
        ]
        culture_rows.append(format_value(values))
        for order, url in enumerate(split_images(row.get("图片集"), IMAGE_PREFIX_CULTURE)):
            image_rows.append("(%d, %d, %s, %d)" % (
                len(image_rows) + 1, culture_id, q(url), order))

    culture_cols = [
        "id", "name", "category", "cover_image", "summary", "content",
        "website", "related_scenic", "related_person", "video_url", "tags",
    ]
    image_cols = ["id", "culture_id", "image_url", "sort_order"]
    STATS["culture"] = len(culture_rows)
    STATS["culture_image"] = len(image_rows)
    return [
        ("culture", DDL_CULTURE, build_upsert("culture", culture_cols, culture_rows)),
        ("culture_image", DDL_CULTURE_IMAGE, build_upsert("culture_image", image_cols, image_rows)),
    ]


def gen_heritage(df):
    heritage_rows = []
    image_rows = []
    heritage_id = 0
    for _, row in df.iterrows():
        heritage_id += 1
        values = [
            str(heritage_id),
            q(norm_text(row.get("名称"))),
            q(clean_url(row.get("简介网址"), "heritage.website")),
            q(norm_text(row.get("类别"))),
            q(norm_text(row.get("级别"))),
            q(norm_text(row.get("传承人"))),
            q(norm_text(row.get("简介"))),
            q(clean_url(row.get("视频链接"), "heritage.video_url")),
        ]
        heritage_rows.append(format_value(values))
        for order, url in enumerate(split_images(row.get("图片(2-3张)"), IMAGE_PREFIX_HERITAGE)):
            image_rows.append("(%d, %d, %s, %d)" % (
                len(image_rows) + 1, heritage_id, q(url), order))

    heritage_cols = [
        "id", "name", "website", "category", "level", "inheritor", "summary", "video_url",
    ]
    image_cols = ["id", "heritage_id", "image_url", "sort_order"]
    STATS["heritage"] = len(heritage_rows)
    STATS["heritage_image"] = len(image_rows)
    return [
        ("heritage", DDL_HERITAGE, build_upsert("heritage", heritage_cols, heritage_rows)),
        ("heritage_image", DDL_HERITAGE_IMAGE,
         build_upsert("heritage_image", image_cols, image_rows)),
    ]


# ---------------------------------------------------------------------------
# 市州基础数据 SQL
# ---------------------------------------------------------------------------
def build_city_sql():
    lines = [
        "CREATE DATABASE IF NOT EXISTS `shuyun`",
        "    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;",
        "USE `shuyun`;",
        "",
        "-- 确保 city 表及其 21 个市州基础数据存在（幂等，可反复执行）",
        DDL_CITY,
        "",
    ]
    values = []
    for cid, name, code, description in CITY_SEED:
        values.append("(%d, %s, %s, %s)" % (
            cid, q(name), q(code), q(description)))
    lines.append(
        "INSERT IGNORE INTO `city` (`id`, `name`, `code`, `description`) VALUES\n"
        + ",\n".join(values) + ";")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main():
    print("=" * 64)
    print("Excel -> SQL 转换开始")
    print("=" * 64)

    scenic_df = read_excel("01-*.xlsx")
    food_df = read_excel("02-*.xlsx")
    culture_df = read_excel("03-*.xlsx")
    heritage_df = read_excel("04-*.xlsx")

    sections = []
    sections.extend(gen_scenic(scenic_df))
    sections.extend(gen_food(food_df))
    sections.extend(gen_culture(culture_df))
    sections.extend(gen_heritage(heritage_df))

    # 景点视频链接回填（独立输出，不并入 data.sql）
    video_lines = gen_scenic_video(scenic_df)

    # 汇总统计
    stat_lines = []
    for table in ["scenic_spot", "scenic_image", "food", "culture",
                  "culture_image", "heritage", "heritage_image"]:
        stat_lines.append("--   %-16s %d 条" % (table, STATS.get(table, 0)))

    header = [
        "-- ============================================================================",
        "-- 蜀韵·智游（shuyun-travel）Excel 数据导入脚本（upsert，非破坏性导入）",
        "-- 由 tools/excel2sql.py 自动生成，请勿手工修改。",
        "-- 生成时间：%s" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "-- 输入：shuyun-data/01-景点.xlsx、02-美食.xlsx、03-文化.xlsx、04-非遗.xlsx",
        "-- 输出：shuyun-web/src/main/resources/db/data.sql",
        "--",
        "-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 -D shuyun < data.sql",
        "-- 重新生成：python tools/excel2sql.py",
        "--",
        "-- 记录数统计：",
    ] + stat_lines + [
        "--",
        "-- 说明：本脚本不 DROP / 不 TRUNCATE，仅 CREATE TABLE IF NOT EXISTS + upsert 导入，",
        "--       可重复执行，且不会覆盖 Excel 之外的数据（如手工新增行）。",
        "-- ============================================================================",
        "",
        "SET NAMES utf8mb4;",
        "",
        build_city_sql(),
        "",
    ]

    body = []
    titles = {
        "scenic_spot": "1. 景点表",
        "scenic_image": "2. 景点图片表",
        "food": "3. 美食表",
        "culture": "4. 文化表",
        "culture_image": "5. 文化图片表",
        "heritage": "6. 非遗表",
        "heritage_image": "7. 非遗图片表",
    }
    for table, ddl, insert_sql in sections:
        body.append("-- ----------------------------------------------------------------------------")
        body.append("-- %s：%s" % (titles.get(table, table), table))
        body.append("-- ----------------------------------------------------------------------------")
        body.append(ddl)
        body.append("")
        body.append(insert_sql)

    content = "\n".join(header + body)

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8", newline="\n") as fp:
        fp.write(content)

    # 景点视频回填脚本（前置条件：scenic_spot 已有 video_url 字段）
    video_header = [
        "-- ============================================================================",
        "-- 蜀韵·智游（shuyun-travel）景点视频链接回填脚本  update_scenic_video.sql",
        "-- 由 tools/excel2sql.py 自动生成，请勿手工修改。",
        "-- 生成时间：%s" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "-- 来源：shuyun-data/01-景点.xlsx 的「视频网址」列（R 列，按「名称」匹配）",
        "-- 用途：为 scenic_spot.video_url 回填景点视频链接（B 站 / 央视网等）。",
        "--",
        "-- 前置条件：scenic_spot 表须已存在 video_url 字段（见 db/fix_columns.sql，或手工执行：",
        "--   ALTER TABLE scenic_spot ADD COLUMN video_url VARCHAR(500) DEFAULT NULL COMMENT '景点视频链接' AFTER recommend_reason;）",
        "--",
        "-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 shuyun < update_scenic_video.sql",
        "-- 重新生成：python tools/excel2sql.py",
        "--",
        "-- 记录数统计：",
        "--   scenic_spot.video_url 回填 %d 条" % STATS.get("scenic_video", 0),
        "--",
        "-- 说明：本脚本仅 UPDATE，不新增 / 不删除任何行；按 name 匹配，未命中（名称不一致）",
        "--       的行不会被修改，可重复执行。",
        "-- ============================================================================",
        "",
        "SET NAMES utf8mb4;",
        "",
    ]
    video_content = "\n".join(
        video_header + (video_lines or ["-- 01-景点.xlsx 中无有效视频网址"]))
    with open(OUT_VIDEO_PATH, "w", encoding="utf-8", newline="\n") as fp:
        fp.write(video_content)

    print("-" * 64)
    print("已生成：%s" % OUT_PATH)
    print("已生成：%s" % OUT_VIDEO_PATH)
    print("记录数统计：")
    for table in ["scenic_spot", "scenic_image", "food", "culture",
                  "culture_image", "heritage", "heritage_image"]:
        print("   %-16s %d 条" % (table, STATS.get(table, 0)))
    print("   %-16s %d 条" % ("scenic_video", STATS.get("scenic_video", 0)))
    if URL_FIXES:
        print("URL 字段清洗：")
        for label, raw, result in URL_FIXES:
            print("   [%s] %s -> %s" % (label, raw, result if result else "NULL"))
    print("=" * 64)


if __name__ == "__main__":
    main()
