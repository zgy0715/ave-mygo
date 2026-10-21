/** 乐队公共映射：DB band_id → 前端视觉 slug（全站唯一定义处） */
export const slugByBandId = (id) => ({ 1: 'mygo', 2: 'ave-mujica', 3: 'sumimi' }[id] || 'mygo')

/** 乐队名 → slug：库里 'MyGO!!!!' 与 'MyGO!!!!!' 混用，按前缀归一化 */
export const slugByName = (name = '') => {
  if (name.startsWith('MyGO')) return 'mygo'
  if (name.startsWith('Ave Mujica')) return 'ave-mujica'
  if (name.toLowerCase().includes('sumimi')) return 'sumimi'
  return 'mygo'
}
