# SQL 迁移总览（V1 – V38）

> 位置：`ave-mygo/src/main/resources/db/migration/`　共 **38 个文件**，Flyway 已应用 **V1–V37**（37 条历史，全部 success），V38 为纯 UPDATE，已在库执行、待下次启动补记录。
> 生成时间：2026-10-20（依据文件实际内容 + Flyway 历史表核对）

---

## 一、总览表

| 版本 | 文件 | 分类 | 内容 | 状态 |
|---|---|---|---|---|
| V1 | init | 结构 | 13 张表初始结构（含 `chk_stock CHECK`）| ✅ |
| V2 | song_seed | 种子 | 初始曲目（封面/音源/ MV 留空）| ✅ |
| V3 | event_seed | 种子 | MyGO 纪念 Live（昼/夜两场 + S/普通席票档）| ✅ |
| V4 | member_seed | 种子 | 成员 + CV（官方公开资料）| ✅ |
| V5 | official_images | 素材 | 官方图片落位（anime.bang-dream.com）| ✅ |
| V6 | data_expansion | 种子 | 曲库/演出扩充 | ✅ |
| V7 | cover_images | 素材 | AI 生成封面落位 | ✅（已被 V9 撤销）|
| V8 | fix_duplicates | 修正 | 合并《栞》重复(id 10→6)、修专辑 | ✅ |
| V9 | remove_ai_covers | 修正 | **撤销全部 AI 封面**（站长红线）| ✅ |
| V10 | cover_convention | 约定 | 封面路径约定 `cover-{id}` | ✅ |
| V11 | discography_expansion | 种子 | 全曲库扩充（官方曲绘目录）| ✅ |
| V12 | sumimi_members_fix | 修正 | **修正 sumimi 成员**（初華 × まな，非 にゃむ）| ✅ |
| V13 | sumimi_images | 素材 | sumimi 立绘 | ✅ |
| V14 | sumimi_band_kv | 素材 | sumimi 双人 KV | ✅ |
| V15 | sumimi_songs | 种子 | sumimi 两曲（Here, the world! / Sweet Escape）| ✅ |
| V16 | official_covers | 素材 | **官方封面全量**（56 张 / 55 首）；删除查无此曲《忘れてやまない》| ✅ |
| V17 | setlist_song12_fix | 修正 | 清理 song 12 删除后的 setlist 悬空引用 + 序号前移 | ✅ |
| V18 | album_fix_and_covers | 修正 | 专辑正名（**迷跡波 / 跡暖空**）+ 专辑封面 | ✅ |
| V19 | sumimi_covers | 素材 | sumimi 配信封面 + 建单专、两曲挂靠 | ✅ |
| V20 | remove_phantom_album | 修正 | **删幻影专辑《旗印》**；建《Alea jacta est》EP + 素晴らしき世界单曲专；合并重复 47→9 | ✅ |
| V21 | alea_jacta_est_cover | 素材 | EP 官方封面（用户提供高清图）| ✅ |
| V22 | admin_user | 权限 | 站长账号（id=1）提权 ADMIN | ✅ |
| V23 | conversation_history | 结构 | 建 AI 助手对话历史表 | ✅ |
| V24 | official_mv_links | 数据 | 官方 MV 第一批 12 首（7 B站 + 5 YouTube）| ✅（YouTube 已被 V27/V28/V38 替换）|
| V25 | official_audio_previews | 数据 | **iTunes 官方 30s 试听 47 首** + 补 45 首时长 | ✅ |
| V26 | stream_links | 结构+数据 | `song` 加 `stream_url` 列（守卫式 ALTER）；网易云全曲外链 10 首 | ✅（已幂等化）|
| V27 | bangumi_insert_songs | 数据 | Mujica 篇番剧插入曲 ep 内嵌 8 首 | ✅ |
| V28 | mygo_insert_songs | 数据+补录 | MyGO 篇（栞 ED / 碧天伴走 #7）+ **补录《焚音打》** | ✅（INSERT 已幂等化）|
| V29 | haruhikage_mygo_ep | 数据 | 春日影改 MyGO #07 名场面版 | ✅（被 V38 再升级）|
| V30 | missing_insert_songs | 补录 | **八芒星ダンス + 人間になりたいうたII** | ✅（已幂等化）|
| V31 | sumimi_mv | 数据 | sumimi《Here, the world!》MV | ✅ |
| V32 | bili_full_songs_batch1 | 数据 | B站完整音频第一批 13 首 | ✅ |
| V33 | bili_full_songs_batch2 | 数据 | 第二批（Symbol IV / 輪符雨）| ✅ |
| V34 | bili_full_songs_batch3 | 数据 | 第三批（砂寸奏/端程山/過惰幻/霧周途/歩拾道）| ✅ |
| V35 | bili_full_songs_batch4 | 数据 | 第四批（残痕字/往欄印/エガクミライ/静降想）| ✅ |
| V36 | bili_full_songs_batch5 | 数据 | 第五批（潜在表明 FIRST TAKE / Ave Mujica 同名 / 素晴らしき世界）| ✅ |
| V37 | completeness_album_and_phantom_cleanup | 结构+修正 | 建《Completeness》专辑 + 7 曲挂靠；删 3 个专辑名幽灵条目（17/26/57）| ✅（已幂等化）|
| V38 | official_bili_mv_fix | 数据 | **春日影→官方 MV**（BV1JHLUz4EUy）；無路矢纠正为 MyGO 曲并换 B 站源 | ✅ |
| V39 | ep_to_bvid | 数据 | **番剧 ep_id 内嵌改为 bvid**（B站禁止第三方嵌入番剧）：10 首换 bvid，2 首改跳转卡 | ✅ |
| V40 | discography_fix_and_covers | 数据 | 专辑归属纠正（潜伏表明/影色舞→音一会，栞/焚音打→壱雫空，碧天伴走/詩超絆/迷路日々/無路矢→迷跡波，歩拾道/霧周途/過惰幻→跡暖空）+ 3 首补录曲封面 + 删 4 张空壳专辑 | ✅ |

---

## 二、表结构类变更（仅 4 处）

| 版本 | 变更 | 说明 |
|---|---|---|
| V1 | 建 13 张表 | 含 `chk_stock CHECK (sold_count <= total_stock)` 防超卖约束 |
| V23 | 建 `conversation_history` | AI 助手多轮对话（user_id 可空、session_id 索引）|
| V26 | `song` + `stream_url` | 官方平台全曲外链（守卫式 ALTER，可安全重放）|
| V37 | `album` +1 行 / `song` −3 行 | 建 Completeness（id=15）、清幽灵条目 |

> 其余 34 个迁移全部是**数据类**（INSERT / UPDATE / DELETE），不改表结构。

---

## 三、数据删除与合并审计（谁被删了、为什么）

| 版本 | 对象 | 原因 |
|---|---|---|
| V8 | song id=10《栞》| 与 id=6 重复 → 合并保留 6 |
| V16 | song id=12《忘れてやまない》| **全企划查无此曲**（幻影条目）|
| V20 | album id=5《Ave Mujicaの旗印》| 官网 discography 无此专辑 |
| V20 | song id=47《素晴らしき世界 でも どこにもない場所》| 与 id=9 重复 → 保留 9 |
| V37 | song 17《迷跡波》/ 26《跡暖空》/ 57《Completeness》| **专辑名误录为歌曲**（对应 album 7/8/15 已存在）|

---

## 四、当前数据水位（2026-10-20 核对）

| 指标 | 数值 |
|---|---|
| 歌曲 | 56 首（含补录：焚音打/八芒星/人間になりたいうたII；已清 3 个幽灵条目）|
| 专辑 | 10 张（V20/V40 清理空壳与幻影；每张均有曲目）|
| 有 MV | 49 / 56 首（47 可内嵌 + 2 跳转卡）|
| YouTube 外链残留 | **0**（V38 清零）|
| 有官方试听 | 47 首 |
| 全曲收听外链（网易云）| 10 首 |
| Flyway 历史 | 37 条 success，max=37；V38 待记录 |

---

## 五、注意事项（重要）

1. **已应用迁移禁止改内容**（会触发 checksum 校验失败）。V16、V18 文件头注释里仍写着旧文件名（`V15__`、`V16__`），属历史遗留、**不影响执行，不要动**。
2. **V26–V37 已幂等化**：V26 用 information_schema 守卫 ALTER；V28/V30/V37 的 INSERT 带 `ON DUPLICATE KEY UPDATE`。原因：曾手动执行导致 Flyway 重跑失败（Duplicate column）。今后**新迁移必须可由 Flyway 正常执行**。
3. **V38 尚未写入历史**：下次启动后端会自动执行（纯 UPDATE，重复执行无副作用）。
4. **版本号分配**：新建迁移前先 `ls db/migration | sort -V | tail -1`，取最大号 +1。
5. **不要手工 `mysql < Vxx.sql` 后不补历史**；若确需即时生效，只允许对**纯 UPDATE/DELETE 的幂等迁移**这么做（仍需重启让 Flyway 补记录）。

## 六、核对命令

```bash
# 已应用版本
mysql -u root -p**** bandori_live -e "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank;"

# 迁移文件最大版本
ls ave-mygo/src/main/resources/db/migration | sort -V | tail -1

# 回滚（谨慎）：Flyway 社区版无 undo，按需反向写新迁移
```
