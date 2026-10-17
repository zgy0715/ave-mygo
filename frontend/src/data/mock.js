/**
 * 演示数据（Mock）——v2 前端先行，后端 M3 未就绪前的数据源。
 * 字段结构与 docs/05-数据库设计.md 对齐，后端接口就绪后：
 *   把页面里 `import { xxx } from '@/data/mock'` 换成 `@/api/xxx` 即可，视图不用大改。
 * ⚠️ 演出/订单为虚构示例；歌曲/成员条目参照公开资料整理，仅作界面演示。
 */

export const bands = [
  {
    id: 'mygo',
    name: 'MyGO!!!!!',
    nameCn: '迷子乐队',
    color: '#2c7da0',
    soft: '#e3f0f7',
    intro: '五个迷路的孩子，即使迷失也要继续前行。2023 年出道的五人乐队，以「迷子でもいい、迷子でも進む」为信条。',
    gradient: 'linear-gradient(120deg, #0e2230 0%, #287aac 55%, #7bd 125%)',
  },
  {
    id: 'mujica',
    name: 'Ave Mujica',
    nameCn: '无期免罪的女巫们',
    color: '#6b4fa3',
    soft: '#efeaf7',
    intro: '戴上面具，成为人偶。以华丽哥特视觉与完美舞台表演著称的神秘乐队。',
    gradient: 'linear-gradient(120deg, #17020c 0%, #5d1031 40%, #aa1656 75%, #b95 125%)',
  },
]

export const members = [
  // MyGO!!!!!
  { id: 'm1', band: 'mygo', name: '高松燈', cv: '羊宮妃那', position: 'Vocal', intro: '歌词里藏着说不出口的话。' },
  { id: 'm2', band: 'mygo', name: '千早愛音', cv: '立石凛', position: 'Guitar', intro: '演唱会策划担当，行动力拔群。' },
  { id: 'm3', band: 'mygo', name: '要楽奈', cv: '青木陽菜', position: 'Guitar', intro: 'RANA。天才吉他，随性而来。' },
  { id: 'm4', band: 'mygo', name: '長崎そよ', cv: '小日向美香', position: 'Bass', intro: '最清醒也最执着的那个人。' },
  { id: 'm5', band: 'mygo', name: '椎名立希', cv: '林鼓子', position: 'Drums', intro: '鼓点即是心跳。' },
  // Ave Mujica
  { id: 'u1', band: 'mujica', name: '三角初華', cv: '佐々木李子', position: 'Vocal', intro: '舞台上的女神，面具下的执念。' },
  { id: 'u2', band: 'mujica', name: '若葉睦', cv: '渡瀬結月', position: 'Guitar', intro: 'Mortis。沉默的人偶。' },
  { id: 'u3', band: 'mujica', name: '八幡海鈴', cv: '岡田夢以', position: 'Bass', intro: '商业头脑与贝斯同样可靠。' },
  { id: 'u4', band: 'mujica', name: '祐天寺にゃむ', cv: '米澤茜', position: 'Drums', intro: '视频创作者，幕后操盘手。' },
  { id: 'u5', band: 'mujica', name: '豊川祥子', cv: '高尾奏音', position: 'Keyboard', intro: 'Oblivionis。乐队的创立者。' },
]

/** 时长统一存秒；mvUrl 存 B 站 embed 页（后端就绪后同名字段直接替换） */
export const songs = [
  { id: 1, title: '迷星叫', band: 'mygo', album: '1st Single「迷星叫」', releaseDate: '2022-11-09', duration: 232, tags: ['出道曲'], mvUrl: '', audioUrl: '' },
  { id: 2, title: '音一会', band: 'mygo', album: '2nd Single「音一会」', releaseDate: '2023-01-11', duration: 245, tags: ['抒情'], mvUrl: '', audioUrl: '' },
  { id: 3, title: '壱雫空', band: 'mygo', album: '3rd Single「壱雫空」', releaseDate: '2023-04-12', duration: 218, tags: ['热血'], mvUrl: '', audioUrl: '' },
  { id: 4, title: '潜在表明', band: 'mygo', album: '3rd Single「壱雫空」', releaseDate: '2023-04-12', duration: 226, tags: ['摇滚'], mvUrl: '', audioUrl: '' },
  { id: 5, title: '栞', band: 'mygo', album: '4th Single「栞」', releaseDate: '2023-05-24', duration: 262, tags: ['抒情', '名场面'], mvUrl: '', audioUrl: '' },
  { id: 6, title: '碧天伴走', band: 'mygo', album: '4th Single「栞」', releaseDate: '2023-05-24', duration: 240, tags: ['热血'], mvUrl: '', audioUrl: '' },
  { id: 7, title: '忘れてやまない', band: 'mygo', album: '1st Album「閉析」', releaseDate: '2023-08-09', duration: 235, tags: ['摇滚'], mvUrl: '', audioUrl: '' },
  { id: 8, title: '無路矢', band: 'mygo', album: '1st Album「閉析」', releaseDate: '2023-08-09', duration: 251, tags: ['摇滚'], mvUrl: '', audioUrl: '' },
  { id: 9, title: '詩超絆', band: 'mygo', album: '2nd Album「跡空」', releaseDate: '2023-12-13', duration: 288, tags: ['名场面', '抒情'], mvUrl: '', audioUrl: '' },
  { id: 10, title: '春日影', band: 'mygo', album: 'TV 动画《MyGO!!!!!》剧中曲', releaseDate: '2023-07-27', duration: 254, tags: ['CRYCHIC', '名场面'], mvUrl: '', audioUrl: '' },
  { id: 11, title: '黒のバースデイ', band: 'mujica', album: '1st Single「黒のバースデイ」', releaseDate: '2023-02-15', duration: 243, tags: ['出道曲', '哥特'], mvUrl: '', audioUrl: '' },
  { id: 12, title: 'Ave Mujicaの旗印', band: 'mujica', album: '2nd Single「Ave Mujicaの旗印」', releaseDate: '2023-04-05', duration: 236, tags: ['哥特'], mvUrl: '', audioUrl: '' },
  { id: 13, title: '素晴らしき世界', band: 'mujica', album: '3rd Single「素晴らしき世界」', releaseDate: '2023-05-31', duration: 248, tags: ['抒情'], mvUrl: '', audioUrl: '' },
  { id: 14, title: '顔', band: 'mujica', album: 'TV 动画《Ave Mujica》剧中曲', releaseDate: '2025-01-15', duration: 230, tags: ['哥特', '摇滚'], mvUrl: '', audioUrl: '' },
]

/** 演出 → 场次 → 票档，结构与 live_event / live_session / ticket_type 对齐 */
export const events = [
  {
    id: 1,
    title: 'MyGO!!!!! 7th LIVE「前に進むなら」',
    band: 'mygo',
    venue: '東京ガーデンシアター',
    date: '2026-11-08',
    openTime: '16:00',
    startTime: '17:00',
    status: 'ONSALE',          // NOT_OPEN 未开售 | ONSALE 销售中 | SOLDOUT 售罄 | ENDED 已结束
    poster: '',
    description:
      '「就算迷路，也要继续前行。」MyGO!!!!! 五人再度登上舞台。本次 LIVE 将首次完整演唱最新专辑曲目，并在终场带来全体合唱的新编曲版本。',
    sessions: [
      {
        id: 101, name: '昼场', startTime: '2026-11-08 17:00',
        ticketTypes: [
          { id: 1001, name: 'S 席', price: 9800, totalStock: 500, soldCount: 487 },   // 价格单位：元×100？→ 用分；见下注释
          { id: 1002, name: 'A 席', price: 6800, totalStock: 800, soldCount: 511 },
          { id: 1003, name: '普通席', price: 4800, totalStock: 1200, soldCount: 340 },
        ],
      },
    ],
  },
  {
    id: 2,
    title: 'Ave Mujica 5th LIVE「Salvage the Witch」',
    band: 'mujica',
    venue: '横浜アリーナ',
    date: '2026-12-13',
    openTime: '16:30',
    startTime: '17:30',
    status: 'NOT_OPEN',
    poster: '',
    description: '面具之下，究竟是谁？Ave Mujica 年末专场，全新舞台装置与全员新装登场。',
    sessions: [
      {
        id: 201, name: '夜场', startTime: '2026-12-13 17:30',
        ticketTypes: [
          { id: 2001, name: 'S 席', price: 9800, totalStock: 400, soldCount: 0 },
          { id: 2002, name: 'A 席', price: 6800, totalStock: 900, soldCount: 0 },
        ],
      },
    ],
  },
  {
    id: 3,
    title: 'BanG Dream! 感謝祭 合同LIVE',
    band: 'mygo',
    venue: '神奈川県民ホール',
    date: '2026-10-25',
    openTime: '15:00',
    startTime: '16:00',
    status: 'ONSALE',
    poster: '',
    description: 'MyGO!!!!! 与 Ave Mujica 同台出演的感谢祭合同 LIVE，限定组合曲仅此一场。',
    sessions: [
      {
        id: 301, name: '唯一场次', startTime: '2026-10-25 16:00',
        ticketTypes: [
          { id: 3001, name: 'VIP 席', price: 12800, totalStock: 200, soldCount: 196 },
          { id: 3002, name: 'S 席', price: 8800, totalStock: 600, soldCount: 592 },
          { id: 3003, name: '普通席', price: 5800, totalStock: 1000, soldCount: 640 },
        ],
      },
    ],
  },
  {
    id: 4,
    title: 'MyGO!!!!! 6th LIVE「迷子のままでも」',
    band: 'mygo',
    venue: 'Zepp Haneda',
    date: '2026-06-21',
    openTime: '15:30',
    startTime: '16:30',
    status: 'ENDED',
    poster: '',
    description: '已结束场次示例——结束后可在本页录入 Setlist（P2 功能）。',
    sessions: [
      {
        id: 401, name: '夜场', startTime: '2026-06-21 16:30',
        ticketTypes: [
          { id: 4001, name: 'S 席', price: 8800, totalStock: 400, soldCount: 400 },
          { id: 4002, name: '普通席', price: 5800, totalStock: 800, soldCount: 760 },
        ],
      },
    ],
  },
]

/**
 * ⚠️ 价格单位说明：mock 里 price = 日元位直接写大数仅为演示，
 * 后端落库统一用「分」（int），展示层 / 100，避免浮点误差。
 */

/** 订单示例，结构与 ticket_order 对齐 */
export const orders = [
  {
    id: 9001,
    orderNo: 'BL202610170001',
    eventId: 3,
    sessionId: 301,
    ticketTypeId: 3002,
    quantity: 2,
    amount: 17600,
    status: 'PENDING',         // PENDING 待支付 | PAID 已支付 | CANCELLED 已取消
    createdAt: '2026-10-17 19:40',
    payDeadline: '2026-10-17 20:55',
  },
  {
    id: 9002,
    orderNo: 'BL202610160032',
    eventId: 1,
    sessionId: 101,
    ticketTypeId: 1002,
    quantity: 1,
    amount: 6800,
    status: 'PAID',
    createdAt: '2026-10-16 12:03',
    payDeadline: '2026-10-16 12:18',
  },
  {
    id: 9003,
    orderNo: 'BL202610150107',
    eventId: 4,
    sessionId: 401,
    ticketTypeId: 4002,
    quantity: 2,
    amount: 11600,
    status: 'CANCELLED',
    createdAt: '2026-10-15 21:11',
    payDeadline: '2026-10-15 21:26',
  },
]

/** 简单收藏示例（P1 模块） */
export const favoriteSongIds = [5, 10, 14]

export const bandById = (id) => bands.find((b) => b.id === id)
export const eventById = (id) => events.find((e) => e.id === Number(id))
export const songById = (id) => songs.find((s) => s.id === Number(id))

/** 票档余票状态：>50% 充足 / ≤50% 紧张（≤10% 加急）/ 0 售罄 */
export function stockLevel(tt) {
  const left = tt.totalStock - tt.soldCount
  if (left <= 0) return 'soldout'
  const ratio = left / tt.totalStock
  if (ratio <= 0.1) return 'critical'
  if (ratio <= 0.5) return 'low'
  return 'ok'
}
