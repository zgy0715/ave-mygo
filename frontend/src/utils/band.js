/** 乐队公共映射：DB band_id → 前端视觉 slug（全站唯一定义处） */
export const slugByBandId = (id) => ({ 1: 'mygo', 2: 'ave-mujica', 3: 'sumimi' }[id] || 'mygo')
