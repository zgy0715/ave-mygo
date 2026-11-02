#!/usr/bin/env python3
"""从开发库导出参考数据 -> db/seed/reference.sql（幂等 upsert）。

新环境部署流程：Flyway 回放 V1..V55 -> 执行 db/seed/reference.sql。
老库不受影响（可直接重复执行，结果收敛）。

用法：
    DB_PASSWORD=xxx python scripts/gen_seed.py [输出路径]

连接参数可用环境变量覆盖：DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME。
只读：本脚本仅执行 SELECT。
"""
import datetime
import decimal
import os
import sys

import pymysql

# 参考数据表（按外键依赖顺序）
TABLES = [
    "band",
    "member",
    "album",
    "song",
    "live_event",
    "live_session",
    "ticket_type",
    "setlist_item",
]

# 测试残留过滤：事件标题以 [TEST] 开头 / 场次名以 E2E 开头
EVENT_FILTER = "is_deleted = 0 AND title NOT LIKE '[TEST]%'"
SESSION_FILTER = "is_deleted = 0 AND name NOT LIKE 'E2E%'"


def conn():
    return pymysql.connect(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        port=int(os.getenv("DB_PORT", "3306")),
        user=os.getenv("DB_USER", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        database=os.getenv("DB_NAME", "bandori_live"),
        charset="utf8mb4",
    )


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, decimal.Decimal)):
        return str(v)
    if isinstance(v, datetime.datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(v, datetime.date):
        return "'" + v.isoformat() + "'"
    s = str(v).replace("\\", "\\\\").replace("'", "''")
    return "'" + s + "'"


def columns(cur, table):
    cur.execute(
        "SELECT column_name FROM information_schema.columns "
        "WHERE table_schema = DATABASE() AND table_name = %s "
        "ORDER BY ordinal_position",
        (table,),
    )
    return [r[0] for r in cur.fetchall()]


def rows_for(cur, table):
    if table == "live_event":
        cur.execute(f"SELECT * FROM live_event WHERE {EVENT_FILTER} ORDER BY id")
    elif table == "live_session":
        cur.execute(
            "SELECT * FROM live_session WHERE "
            f"{SESSION_FILTER} AND event_id IN (SELECT id FROM live_event WHERE {EVENT_FILTER}) "
            "ORDER BY id"
        )
    elif table in ("ticket_type", "setlist_item"):
        cur.execute(
            f"SELECT * FROM {table} WHERE is_deleted = 0 AND session_id IN ("
            "SELECT id FROM live_session WHERE "
            f"{SESSION_FILTER} AND event_id IN (SELECT id FROM live_event WHERE {EVENT_FILTER})) "
            "ORDER BY id"
        )
    elif table == "band":
        cur.execute("SELECT * FROM band ORDER BY id")
    else:
        cur.execute(f"SELECT * FROM {table} WHERE is_deleted = 0 ORDER BY id")
    return cur.fetchall()


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "db/seed/reference.sql"
    c = conn()
    cur = c.cursor()
    chunks = [
        "-- 参考数据基线（自动生成，勿手改）：scripts/gen_seed.py",
        f"-- 生成时间：{datetime.datetime.now():%Y-%m-%d %H:%M:%S}",
        "-- 用途：新环境 Flyway 回放 V1..V55 后执行本文件，数据收敛到与开发库一致的基线。",
        "-- 幂等：全部为 INSERT ... ON DUPLICATE KEY UPDATE，可重复执行。",
        "-- 已排除：软删除行、[TEST] 事件、E2E 场次（及其票档/歌单）。",
        "",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "",
    ]
    total = 0
    for t in TABLES:
        cols = columns(cur, t)
        rows = rows_for(cur, t)
        total += len(rows)
        collist = ", ".join(f"`{c}`" for c in cols)
        upd = ", ".join(f"`{c}` = VALUES(`{c}`)" for c in cols if c != "id")
        chunks.append(f"-- {t}: {len(rows)} 行")
        if rows:
            chunks.append(f"INSERT INTO `{t}` ({collist}) VALUES")
            vals = [", ".join(lit(v) for v in r) for r in rows]
            chunks.append(",\n".join(f"({v})" for v in vals))
            chunks.append(f"ON DUPLICATE KEY UPDATE {upd};")
        chunks.append("")
    chunks += ["SET FOREIGN_KEY_CHECKS = 1;", f"-- 合计 {total} 行"]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(chunks) + "\n")
    print(f"wrote {out}: {total} rows")


if __name__ == "__main__":
    main()
