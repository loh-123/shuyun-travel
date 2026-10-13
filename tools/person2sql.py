# -*- coding: utf-8 -*-
"""
蜀韵·智游（shuyun-travel）传承人数据导入脚本
=====================================================

使用方法（在项目根目录执行）：
    python tools/person2sql.py

依赖安装：
    pip install pandas openpyxl pypinyin

输入：
    shuyun-data/05-传承人.xlsx  （列：姓名 / 简介 / 代表文化 / 视频网址）

输出：
    shuyun-web/src/main/resources/db/person.sql

处理规则：
    1. 逐行读取姓名、简介、代表文化、视频网址。
    2. 头像路径自动生成：/images/person/{拼音名}.jpg
       - 用 pypinyin 把姓名转拼音，多字直接拼接（不带分隔符），
         与 public/images/person/ 下实际文件名保持一致
         （例如「彭登怀」-> pengdenghuai.jpg）。
       - pypinyin 缺失时回退到固定映射表（见 PINYIN_FALLBACK）。
    3. 「代表文化」若为逗号分隔的多值，取第一个作为主关联写入 culture_name。
    4. culture_id 先留 NULL，导入后用 UPDATE ... LEFT JOIN 按名称回填。
    5. 使用 INSERT ... ON DUPLICATE KEY UPDATE 保持幂等（按主键 upsert）。
    6. 导出文件使用 UTF-8（无 BOM），避免 MySQL 客户端中文乱码。

注意：
    - 本脚本不 DROP / 不 TRUNCATE，仅 CREATE TABLE IF NOT EXISTS + upsert。
    - culture_id 回填依赖 culture 表的 name；若 Excel 名称与库中不一致，
      可在 CULTURE_NAME_MAP 中补充映射。
"""

import glob
import os
import sys
from datetime import datetime

try:
    import pandas as pd
except ImportError:
    sys.exit("未安装 pandas，请先执行：pip install pandas openpyxl pypinyin")

try:
    import openpyxl  # noqa: F401  仅用于确保 openpyxl 已安装
except ImportError:
    sys.exit("未安装 openpyxl，请先执行：pip install pandas openpyxl pypinyin")

# pypinyin 可选：缺失时回退到固定映射表
try:
    from pypinyin import lazy_pinyin
    HAS_PYPINYIN = True
except ImportError:
    lazy_pinyin = None
    HAS_PYPINYIN = False


# ---------------------------------------------------------------------------
# 路径常量
# ---------------------------------------------------------------------------
TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(TOOLS_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "shuyun-data")
OUT_PATH = os.path.join(
    PROJECT_ROOT, "shuyun-web", "src", "main", "resources", "db", "person.sql"
)

# 头像目录前缀（与 public/images/person 对应）
AVATAR_PREFIX = "/images/person/"

# pypinyin 缺失时的固定拼音映射（24 位传承人，已与实际图片文件名核对）
PINYIN_FALLBACK = {
    "彭登怀": "pengdenghuai", "郝淑萍": "haoshuping", "孟德芝": "mengdezhi",
    "史正良": "shizhengliang", "彭子渝": "pengziyu", "陈寿": "chenshou",
    "胡锦矗": "hujinchu", "张和民": "zhanghemin", "沈铁梅": "shentiemei",
    "陈巧茹": "chenqiaoru", "李月秋": "liyueqiu", "沈伐": "shenfa",
    "李德才": "lidecai", "道安": "daoan", "倪成玉": "nichengyu",
    "陈云华": "chenyunhua", "赵思进": "zhaosijin", "李芳福": "lifangfu",
    "胡光葵": "huguangkui", "杨华珍": "yanghuazhen", "汪国芳": "wangguofang",
    "毕六福": "biliufu", "樊德然": "fanderan", "李冰": "libing",
}

# Excel「代表文化」名 -> culture 表 name 的映射（默认同名，特殊差异在此登记）
CULTURE_NAME_MAP = {
    # "川剧, 川剧高腔": "川剧",   # 多值已在 _primary_culture 中取第一个
}

# NULL 占位符
NULL_TOKENS = {"", "-", "--", "—", "——", "－", "无", "null", "NULL", "None", "nan"}

# 行数统计
STATS = {}


# ---------------------------------------------------------------------------
# 表结构 DDL
# ---------------------------------------------------------------------------
DDL_CULTURE_PERSON = """\
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='川蜀文化传承人';"""


# ---------------------------------------------------------------------------
# 工具函数
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


def pinyin_of(name):
    """姓名 -> 拼音（多字直接拼接，如「彭登怀」-> pengdenghuai）。"""
    if HAS_PYPINYIN:
        return "".join(lazy_pinyin(name)).lower()
    return PINYIN_FALLBACK.get(name, "")


def avatar_of(name):
    """生成头像路径：/images/person/{拼音}.jpg。"""
    py = pinyin_of(name)
    if not py:
        return None
    return AVATAR_PREFIX + py + ".jpg"


def primary_culture(value):
    """取「代表文化」的第一个（逗号分隔），并套用映射表。"""
    text = norm_text(value)
    if text is None:
        return None
    text = text.replace("，", ",")
    first = text.split(",")[0].strip()
    return CULTURE_NAME_MAP.get(first, first) or None


# ---------------------------------------------------------------------------
# 生成 SQL
# ---------------------------------------------------------------------------
def build_insert(rows):
    """把数据行拼装成 upsert 语句。"""
    if not rows:
        return "-- culture_person：无数据\n"
    cols = ["id", "name", "brief", "culture_name", "culture_id",
            "video_url", "avatar", "sort_order"]
    col_sql = ", ".join("`%s`" % c for c in cols)
    update_sql = ", ".join("`%s` = VALUES(`%s`)" % (c, c)
                          for c in cols if c != "id")
    return (
        "INSERT INTO `culture_person` (%s) VALUES\n%s\n"
        "ON DUPLICATE KEY UPDATE %s;\n" % (
            col_sql, ",\n".join(rows), update_sql))


def read_excel(pattern):
    """按通配符定位并读取 Excel。"""
    matches = glob.glob(os.path.join(DATA_DIR, pattern))
    if not matches:
        raise FileNotFoundError("未找到匹配的 Excel：%s" % pattern)
    path = matches[0]
    df = pd.read_excel(path, dtype=str)
    print("读取 %-20s 共 %d 行" % (os.path.basename(path), len(df)))
    return df


def gen_person(df):
    """生成 culture_person 的 INSERT 行。"""
    rows = []
    pid = 0
    missing_pinyin = []
    for _, row in df.iterrows():
        pid += 1
        name = norm_text(row.get("姓名"))
        avatar = avatar_of(name) if name else None
        if name and not pinyin_of(name):
            missing_pinyin.append(name)
        values = [
            str(pid),
            q(name),
            q(norm_text(row.get("简介"))),
            q(primary_culture(row.get("代表文化"))),
            "NULL",  # culture_id 先留空，导入后由 UPDATE 回填
            q(norm_text(row.get("视频网址"))),
            q(avatar),
            str(pid),
        ]
        rows.append("(" + ", ".join(values) + ")")
    if missing_pinyin:
        print("  [警告] 以下姓名未生成拼音（头像路径为 NULL）：%s"
              % "、".join(missing_pinyin))
    STATS["culture_person"] = len(rows)
    return rows


def main():
    print("=" * 64)
    print("传承人 Excel -> SQL 转换开始")
    print("=" * 64)
    if not HAS_PYPINYIN:
        print("  [提示] 未安装 pypinyin，改用固定映射表生成头像路径。")

    df = read_excel("05-*.xlsx")
    rows = gen_person(df)

    header = [
        "-- ============================================================================",
        "-- 蜀韵·智游（shuyun-travel）传承人数据导入脚本（upsert，非破坏性导入）",
        "-- 由 tools/person2sql.py 自动生成，请勿手工修改。",
        "-- 生成时间：%s" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "-- 输入：shuyun-data/05-传承人.xlsx",
        "-- 输出：shuyun-web/src/main/resources/db/person.sql",
        "--",
        "-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 shuyun < person.sql",
        "-- 重新生成：python tools/person2sql.py",
        "--",
        "-- 记录数统计：",
        "--   culture_person   %d 条" % STATS.get("culture_person", 0),
        "--",
        "-- 说明：本脚本不 DROP / 不 TRUNCATE，仅 CREATE TABLE IF NOT EXISTS + upsert 导入，",
        "--       可重复执行；culture_id 由文末 UPDATE 按 culture.name 自动回填。",
        "-- ============================================================================",
        "",
        "SET NAMES utf8mb4;",
        "",
        "USE `shuyun`;",
        "",
    ]

    body = [
        "-- ----------------------------------------------------------------------------",
        "-- 1. 传承人表：culture_person",
        "-- ----------------------------------------------------------------------------",
        DDL_CULTURE_PERSON,
        "",
        build_insert(rows),
        "-- ----------------------------------------------------------------------------",
        "-- 2. 自动匹配 culture_id（按 culture_name = culture.name）",
        "-- ----------------------------------------------------------------------------",
        "UPDATE `culture_person` cp",
        "LEFT JOIN `culture` c ON cp.`culture_name` = c.`name`",
        "SET cp.`culture_id` = c.`id`",
        "WHERE cp.`culture_id` IS NULL;",
    ]

    content = "\n".join(header + body)
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8", newline="\n") as fp:
        fp.write(content)

    print("-" * 64)
    print("已生成：%s" % OUT_PATH)
    print("   culture_person   %d 条" % STATS.get("culture_person", 0))
    print("=" * 64)


if __name__ == "__main__":
    main()
