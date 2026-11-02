-- V47: 音源回填（2026-10-22）
-- 来源：iTunes Search API 日区（country=jp），30s 官方预览，全部经 HTTP Range 验证 200/206
-- 48 ELEMENTS / 59 Sweet Escape / 62 人間になりたいうたII / 36 カバーコレクション：iTunes 全目录无预览，维持无源

UPDATE `song` SET `audio_url` = 'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/01/40/a9/0140a90c-8f6e-4395-a8e8-9ec05259c964/mzaf_11473953764860556383.plus.aac.p.m4a',
                  `duration`  = 242
  WHERE `id` = 41 AND `title` = 'Masquerade Rhapsody Request';

UPDATE `song` SET `audio_url` = 'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/b1/e4/b1/b1e4b131-b565-8220-f083-a1c94f85778d/mzaf_15566032399383303711.plus.aac.p.m4a',
                  `duration`  = 223  -- 官方曲长 3:42~3:43，原值 340 有误（V41 从 B站视频时长误取）
  WHERE `id` = 55 AND `title` = 'in your blue eyes';

UPDATE `song` SET `audio_url` = 'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/6a/8c/ff/6a8cffe4-9768-1068-8721-df66a26764ca/mzaf_6041876199284644908.plus.aac.p.m4a',
                  `duration`  = 227
  WHERE `id` = 56 AND `title` = 'S The Way';

UPDATE `song` SET `audio_url` = 'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/23/0d/02/230d025a-72aa-7038-da2e-c54a87c6f088/mzaf_949642698194693992.plus.aac.p.m4a'
  WHERE `id` = 60 AND `title` = '焚音打';

UPDATE `song` SET `audio_url` = 'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/58/9f/ae/589fae70-983b-84f3-0efc-37f3ce9129d1/mzaf_16019185153775512435.plus.aac.p.m4a'
  WHERE `id` = 61 AND `title` = '八芒星ダンス';
