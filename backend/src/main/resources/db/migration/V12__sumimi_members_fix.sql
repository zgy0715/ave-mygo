-- =====================================================================
-- V12__sumimi_members_fix.sql —— 修正 sumimi 成员（V11 首版数据有误）
-- 正确成员：三角初華(Vo./Gt.) × 純田まな(Vo.)，资料来源：官方角色列表
-- =====================================================================

UPDATE band SET `description` = '三角初華 × 純田まな 的双人偶像组合。镜头前的闪闪发光，是两个人一起挣来的。'
WHERE `id` = 3;

UPDATE member SET
  `name_ja` = '三角 初華',
  `name_romaji` = 'Misumi Uika',
  `cv` = '佐々木李子',
  `position` = 'Vo./Gt.',
  `intro` = 'sumimi 的主唱兼吉他手。同时也是 Ave Mujica 的 Doloris。'
WHERE `id` = 11;

UPDATE member SET
  `name_ja` = '純田 まな',
  `name_romaji` = 'Sumita Mana',
  `cv` = '反田叶月',
  `position` = 'Vo.',
  `intro` = 'sumimi 的主唱。元气满满的偶像担当，永远站在镜头最前面。'
WHERE `id` = 12;
