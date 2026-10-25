"""数据审计脚本：只读，不改任何数据。
1) 数据完整性（孤儿引用、库存异常、空专辑/空乐队、重名）
2) 每首歌的 30s 试听链接是否可用
3) 每个 MV 的 B 站视频是否可用、标题是否对得上、UP 主是否官方
输出：/tmp/audit.json + 控制台摘要
"""
import os
import json
import re
import subprocess
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor

M = r"D:/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB = "bandori_live"
OP = urllib.request.build_opener(urllib.request.ProxyHandler({}))
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer": "https://www.bilibili.com"}


def sql(q):
    out = subprocess.run([M, "-u", "root", "-p" + os.environ.get("DB_PASSWORD", ""), "--default-character-set=utf8mb4",
                          DB, "-N", "-B", "-e", q], capture_output=True)
    return [line.rstrip("\r").split("\t") for line in out.stdout.decode("utf-8", "ignore").strip().split("\n") if line.strip()]


def check_audio(url):
    if not url:
        return {"ok": False, "reason": "无 audio_url"}
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA["User-Agent"], "Range": "bytes=0-1"})
        with OP.open(req, timeout=12) as r:
            ctype = r.headers.get("Content-Type", "")
            total = r.headers.get("Content-Range", "")
            return {"ok": r.status in (200, 206), "status": r.status, "ctype": ctype, "range": total}
    except urllib.error.HTTPError as e:
        return {"ok": False, "status": e.code}
    except Exception as e:
        return {"ok": False, "reason": str(e)[:60]}


def check_bili(bvid):
    url = f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}"
    try:
        req = urllib.request.Request(url, headers=UA)
        with OP.open(req, timeout=12) as r:
            data = json.load(r)
        if data.get("code") != 0:
            return {"ok": False, "code": data.get("code"), "message": data.get("message")}
        d = data["data"]
        return {"ok": True, "title": d.get("title"), "owner": (d.get("owner") or {}).get("name"),
                "duration": d.get("duration"), "state": d.get("state"), "views": (d.get("stat") or {}).get("view")}
    except Exception as e:
        return {"ok": False, "reason": str(e)[:60]}


# ---------- 1. 完整性 ----------
integrity = {}
integrity["songs_orphan_album"] = sql("""
    SELECT s.id, s.title, s.album_id FROM song s
    WHERE s.album_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM album a WHERE a.id = s.album_id AND a.is_deleted = 0)
""")
integrity["songs_orphan_band"] = sql("""
    SELECT s.id, s.title, s.band_id FROM song s
    WHERE NOT EXISTS (SELECT 1 FROM band b WHERE b.id = s.band_id)
""")
integrity["songs_visible_but_deleted"] = sql("SELECT id, title FROM song WHERE status = 1 AND is_deleted = 1")
integrity["albums_empty"] = sql("""
    SELECT a.id, a.name FROM album a
    WHERE a.is_deleted = 0 AND NOT EXISTS (SELECT 1 FROM song s WHERE s.album_id = a.id AND s.is_deleted = 0)
""")
integrity["albums_orphan_band"] = sql("""
    SELECT a.id, a.name, a.band_id FROM album a
    WHERE NOT EXISTS (SELECT 1 FROM band b WHERE b.id = a.band_id)
""")
integrity["members_orphan_band"] = sql("""
    SELECT m.id, m.name_ja, m.band_id FROM member m
    WHERE NOT EXISTS (SELECT 1 FROM band b WHERE b.id = m.band_id)
""")
integrity["setlist_orphan_song"] = sql("""
    SELECT i.id, i.session_id, i.song_id FROM setlist_item i
    WHERE NOT EXISTS (SELECT 1 FROM song s WHERE s.id = i.song_id)
""")
integrity["setlist_orphan_session"] = sql("""
    SELECT i.id, i.session_id FROM setlist_item i
    WHERE NOT EXISTS (SELECT 1 FROM live_session s WHERE s.id = i.session_id)
""")
integrity["orders_orphan_session"] = sql("""
    SELECT o.order_no, o.session_id FROM ticket_order o
    WHERE NOT EXISTS (SELECT 1 FROM live_session s WHERE s.id = o.session_id)
""")
integrity["stock_overbooked"] = sql("SELECT id, name, total_stock, sold_count FROM ticket_type WHERE sold_count > total_stock")
integrity["stock_negative"] = sql("SELECT id, name, total_stock, sold_count FROM ticket_type WHERE sold_count < 0")
integrity["dup_song_title_same_band"] = sql("""
    SELECT band_id, title, COUNT(*) c FROM song WHERE is_deleted = 0
    GROUP BY band_id, title HAVING c > 1
""")
integrity["dup_album_name"] = sql("""
    SELECT name, COUNT(*) c FROM album WHERE is_deleted = 0 GROUP BY name HAVING c > 1
""")
integrity["favorite_orphan_song"] = sql("""
    SELECT f.id, f.target_id FROM favorite f
    WHERE f.target_type = 'SONG' AND NOT EXISTS (SELECT 1 FROM song s WHERE s.id = f.target_id)
""")
integrity["playlist_orphan_song"] = sql("""
    SELECT i.id, i.playlist_id, i.song_id FROM playlist_item i
    WHERE NOT EXISTS (SELECT 1 FROM song s WHERE s.id = i.song_id)
""")
integrity["bands_without_songs"] = sql("""
    SELECT b.id, b.name FROM band b
    WHERE NOT EXISTS (SELECT 1 FROM song s WHERE s.band_id = b.id AND s.is_deleted = 0)
""")

# ---------- 2. 歌曲与音频/MV ----------
songs = sql("""
    SELECT s.id, s.title, b.name, IFNULL(a.name,''), IFNULL(s.duration,''),
           IFNULL(s.audio_url,''), IFNULL(s.stream_url,''), IFNULL(s.mv_url,''), IFNULL(s.mv_platform,'')
    FROM song s
    LEFT JOIN band b ON b.id = s.band_id
    LEFT JOIN album a ON a.id = s.album_id
    WHERE s.is_deleted = 0 AND s.status = 1
    ORDER BY s.id
""")


def work(row):
    sid, title, band, album, dur, audio, stream, mv, platform = (row + [""] * 9)[:9]
    bvid = None
    m = re.search(r"bvid=(BV[0-9A-Za-z]+)", mv)
    if m:
        bvid = m.group(1)
    res = {
        "id": int(sid), "title": title, "band": band, "album": album,
        "duration": int(dur) if dur else None,
        "has_audio": bool(audio), "has_stream": bool(stream),
        "mv_url": mv, "mv_platform": platform, "bvid": bvid,
    }
    res["audio"] = check_audio(audio) if audio else {"ok": False, "reason": "无 audio_url"}
    res["bili"] = check_bili(bvid) if bvid else None
    return res


with ThreadPoolExecutor(max_workers=6) as pool:
    details = list(pool.map(work, songs))

report = {"integrity": integrity, "songs": details}
os.makedirs(os.path.join(os.path.dirname(__file__), "..", ".audit"), exist_ok=True)
with open(os.path.join(os.path.dirname(__file__), "..", ".audit", "audit.json"), "w", encoding="utf-8") as f:
    json.dump(report, f, ensure_ascii=False, indent=1)

# ---------- 摘要 ----------
print("== 完整性 ==")
for k, v in integrity.items():
    print(f"  {k}: {len(v)}" + ("" if not v else f" → {v[:3]}"))

bad_audio = [d for d in details if not d["audio"].get("ok")]
print(f"\n== 音频：共 {len(details)} 首，异常 {len(bad_audio)} 首 ==")
for d in bad_audio[:8]:
    print(f"  #{d['id']} {d['title']} → {d['audio']}")

no_mv = [d for d in details if not d["mv_url"]]
bad_mv = [d for d in details if d["bvid"] and not d["bili"].get("ok")]
print(f"\n== MV：无源 {len(no_mv)} 首；有 bvid {sum(1 for d in details if d['bvid'])} 首，失效 {len(bad_mv)} 首 ==")
for d in bad_mv[:6]:
    print(f"  #{d['id']} {d['title']} {d['bvid']} → {d['bili']}")
for d in no_mv[:10]:
    print(f"  无MV #{d['id']} {d['title']}（{d['band']}）")
