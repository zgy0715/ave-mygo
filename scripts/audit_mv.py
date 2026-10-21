"""MV 逐个核实：只读。
对每首有 bvid 的歌：调用 B 站 view 接口，比对「视频标题 vs 歌曲名」「UP 主是否官方」「视频状态」。
对无 MV / 非官方源的歌：尝试 B 站搜索接口，列出候选（不修改数据）。
输出 .audit/mv.json
"""
import json
import os
import re
import subprocess
import urllib.parse
import urllib.request
import urllib.error
import time

M = r"D:/MySQL/MySQL Server 8.0/bin/mysql.exe"
OP = urllib.request.build_opener(urllib.request.ProxyHandler({}))
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer": "https://www.bilibili.com"}

OFFICIAL_HINTS = ["BanG Dream", "MyGO", "Ave Mujica", "バンドリ", "ブシロード", "Bushiroad", "bilibili"]

def sql(q):
    out = subprocess.run([M, "-u", "root", "-p20040715", "--default-character-set=utf8mb4",
                          "bandori_live", "-N", "-B", "-e", q], capture_output=True)
    return [l.rstrip("\r").split("\t") for l in out.stdout.decode("utf-8", "ignore").strip().split("\n") if l.strip()]

def get_json(url, timeout=20):
    req = urllib.request.Request(url, headers=UA)
    with OP.open(req, timeout=timeout) as r:
        return json.load(r)

def view(bvid):
    for attempt in range(2):
        try:
            d = get_json(f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}", timeout=15)
            if d.get("code") != 0:
                return {"ok": False, "code": d.get("code"), "msg": d.get("message")}
            v = d["data"]
            return {"ok": True, "title": v.get("title"), "owner": (v.get("owner") or {}).get("name"),
                    "state": v.get("state"), "duration": v.get("duration"),
                    "pubdate": time.strftime("%Y-%m-%d", time.localtime(v.get("pubdate", 0))),
                    "views": (v.get("stat") or {}).get("view"),
                    "copyright": v.get("copyright")}
        except Exception as e:
            if attempt == 1:
                return {"ok": False, "reason": str(e)[:50]}
            time.sleep(1)

def search(keyword, limit=6):
    try:
        kw = urllib.parse.quote(keyword)
        d = get_json(f"https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword={kw}&page=1", timeout=15)
        if d.get("code") != 0:
            return {"ok": False, "code": d.get("code"), "msg": d.get("message")}
        items = (d.get("data") or {}).get("result") or []
        out = []
        for it in items[:limit]:
            out.append({"bvid": it.get("bvid"), "title": re.sub(r"<[^>]+>", "", it.get("title", "")),
                        "author": it.get("author"), "play": it.get("play"), "duration": it.get("duration")})
        return {"ok": True, "items": out}
    except Exception as e:
        return {"ok": False, "reason": str(e)[:60]}

rows = sql("""
    SELECT s.id, s.title, b.name, IFNULL(s.mv_url,''), IFNULL(s.mv_platform,'')
    FROM song s LEFT JOIN band b ON b.id=s.band_id
    WHERE s.is_deleted=0 AND s.status=1 ORDER BY s.id
""")

results = []
for sid, title, band, mv, platform in rows:
    m = re.search(r"bvid=(BV[0-9A-Za-z]+)", mv or "")
    entry = {"id": int(sid), "title": title, "band": band, "mv_url": mv, "platform": platform}
    if not m:
        entry["status"] = "NO_MV" if not mv else "NO_BVID"
        results.append(entry)
        continue
    bvid = m.group(1)
    v = view(bvid)
    entry["bvid"] = bvid
    entry["bili"] = v
    if not v.get("ok"):
        entry["status"] = "BROKEN"
        results.append(entry)
        continue
    t = v.get("title", "")
    core = re.sub(r"[（(].*?[)）]", "", title).strip()
    entry["title_match"] = core[:6] in t if core else False
    entry["official_owner"] = any(h.lower() in (v.get("owner") or "").lower() for h in OFFICIAL_HINTS)
    entry["status"] = "OK" if (entry["title_match"] and entry["official_owner"] and v.get("state") == 0) else "SUSPECT"
    results.append(entry)

# 无 MV / 可疑的，给出搜索候选
candidates = {}
for e in results:
    if e["status"] in ("NO_MV", "NO_BVID", "SUSPECT", "BROKEN"):
        q = f"{e['title']} {e['band']} バンドリ"
        candidates[f"{e['id']}:{e['title']}"] = search(q)

os.makedirs(os.path.join(os.path.dirname(__file__), "..", ".audit"), exist_ok=True)
with open(os.path.join(os.path.dirname(__file__), "..", ".audit", "mv.json"), "w", encoding="utf-8") as f:
    json.dump({"results": results, "candidates": candidates}, f, ensure_ascii=False, indent=1)

ok = [e for e in results if e["status"] == "OK"]
print(f"总计 {len(results)} 首：OK {len(ok)} / SUSPECT {sum(1 for e in results if e['status']=='SUSPECT')} / "
      f"BROKEN {sum(1 for e in results if e['status']=='BROKEN')} / NO_MV {sum(1 for e in results if e['status']=='NO_MV')} / "
      f"NO_BVID {sum(1 for e in results if e['status']=='NO_BVID')}")
print("\n== 可疑/异常明细 ==")
for e in results:
    if e["status"] != "OK":
        b = e.get("bili") or {}
        print(f"[{e['status']}] #{e['id']} {e['title']} | 原标题={b.get('title')} | UP={b.get('owner')} | "
              f"state={b.get('state')} | 匹配={e.get('title_match')} 官方={e.get('official_owner')}")
print("\n== 搜索候选（前 3）==")
for k, v in candidates.items():
    if v.get("ok"):
        print(f"{k}:")
        for it in v["items"][:3]:
            print(f"    {it['bvid']} | {it['title'][:40]} | {it['author']} | 播放{it['play']}")
    else:
        print(f"{k}: 搜索失败 {v}")
