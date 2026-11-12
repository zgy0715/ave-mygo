#!/usr/bin/env python3
"""从开发库只读导出参考数据 -> db/seed/reference.sql。

目录数据按冻结迁移的 ID 更新；演出域按业务键仅补缺失记录。
演出域不导出主键，不覆盖现有行，新票档已售数始终从 0 开始。
新环境先完整执行 Flyway；种子为可选补录，维护期间单进程执行。

用法：
    DB_PASSWORD=xxx python scripts/gen_seed.py [输出路径]

连接参数可用环境变量覆盖：DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME。
只读：本脚本仅执行 SELECT。
"""
import datetime
import decimal
import os
import sys

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
    import pymysql

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


def event_key(event, alias=""):
    prefix = f"{alias}." if alias else ""
    return (f"{prefix}`title` = {lit(event['title'])} "
            f"AND {prefix}`band_id` <=> {lit(event['band_id'])}")


def event_id_sql(event):
    return (f"(SELECT `id` FROM `live_event` WHERE {event_key(event)} "
            "AND `is_deleted` = 0 ORDER BY `id` LIMIT 1)")


def session_id_sql(session, events):
    event = events[session["event_id"]]
    return ("(SELECT s.`id` FROM `live_session` s "
            "JOIN `live_event` e ON e.`id` = s.`event_id` "
            f"WHERE {event_key(event, 'e')} AND s.`name` = {lit(session['name'])} "
            "AND s.`is_deleted` = 0 AND e.`is_deleted` = 0 ORDER BY s.`id` LIMIT 1)")


def runtime_insert(table, row, events, sessions):
    # 主键来自目标库；运行中的售票、上下架与软删除状态不能被参考数据回滚。
    values = {column: lit(value) for column, value in row.items()
              if column not in ("id", "sold_count")}
    parent = None
    if table == "live_event":
        key = event_key(row)
    elif table == "live_session":
        parent = event_id_sql(events[row["event_id"]])
        values["event_id"] = parent
        key = f"`event_id` = {parent} AND `name` = {lit(row['name'])}"
    else:
        parent = session_id_sql(sessions[row["session_id"]], events)
        values["session_id"] = parent
        key_column = "name" if table == "ticket_type" else "order_no"
        key = f"`session_id` = {parent} AND `{key_column}` = {lit(row[key_column])}"
    columns_sql = ", ".join(f"`{column}`" for column in values)
    guard = f"{parent} IS NOT NULL AND " if parent else ""
    return (f"INSERT INTO `{table}` ({columns_sql})\n"
            f"SELECT {', '.join(values.values())}\n"
            f"WHERE {guard}NOT EXISTS (SELECT 1 FROM `{table}` WHERE {key});")


def render_seed(data):
    events = {row["id"]: row for row in data["live_event"]}
    sessions = {row["id"]: row for row in data["live_session"]}
    chunks = [
        "-- 参考数据基线（自动生成，勿手改）：scripts/gen_seed.py",
        f"-- 生成时间：{datetime.datetime.now():%Y-%m-%d %H:%M:%S}",
        "-- 用途：完整 Flyway 迁移后的可选补录；请在维护期间单进程执行。",
        "-- 乐队/成员/专辑/歌曲按冻结迁移 ID 更新元数据；不是生产库整库恢复脚本。",
        "-- 演出/场次/票档/曲目单按业务键仅插入缺失行，不覆盖现有行，也不复活软删行。",
        "-- 演出域主键由目标库生成，票档省略 sold_count（数据库默认 0）。",
        "-- 不导出用户、订单、配额；已排除软删除行、[TEST] 演出与 E2E 场次。",
        "",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "",
    ]
    total = 0
    for t in TABLES:
        rows = data[t]
        total += len(rows)
        chunks.append(f"-- {t}: {len(rows)} 行")
        if t in ("live_event", "live_session", "ticket_type", "setlist_item"):
            chunks.extend(runtime_insert(t, row, events, sessions) for row in rows)
        elif rows:
            cols = list(rows[0])
            collist = ", ".join(f"`{c}`" for c in cols)
            upd = ", ".join(f"`{c}` = VALUES(`{c}`)" for c in cols if c != "id")
            chunks.append(f"INSERT INTO `{t}` ({collist}) VALUES")
            vals = [", ".join(lit(r[c]) for c in cols) for r in rows]
            chunks.append(",\n".join(f"({v})" for v in vals))
            chunks.append(f"ON DUPLICATE KEY UPDATE {upd};")
        chunks.append("")
    chunks += ["COMMIT;", f"-- 合计 {total} 行"]
    return "\n".join(chunks) + "\n"


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "db/seed/reference.sql"
    with conn() as c:
        with c.cursor() as cur:
            cur.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            cur.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            data = {}
            for table in TABLES:
                cols = columns(cur, table)
                data[table] = [dict(zip(cols, row)) for row in rows_for(cur, table)]
    sql = render_seed(data)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(sql)
    print(f"wrote {out}: {sum(len(rows) for rows in data.values())} rows")


if __name__ == "__main__":
    main()
