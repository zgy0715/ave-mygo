首页 Hero 背景视频（网页用 720p 版本）

- band-mygo-720p.mp4     壱雫空 MV    89s   20MB   1865 kbps
- band-mujica-720p.mp4   KiLLKiSS MV  88s   19MB   1748 kbps

原片为 1920x1080（assets-src/video/），已裁掉超长部分、转码为 720p、去掉音轨（页面 muted 自动播放）。
1080P 原片保留在 assets-src/video/（.gitignore 排除，不进版本库、不进部署包），需要重新压制时从那里取。

转码参数（2026-11-10 重压，码率从 787/794 kbps 提到 1865/1748 kbps）：
  ffmpeg -t <时长> -i <1080p 原片> -vf "scale=1280:-2:flags=lanczos" \
    -c:v libx264 -preset slow -crf 20 -maxrate 1900k -bufsize 3800k \
    -profile:v high -level 4.0 -pix_fmt yuv420p -an -movflags +faststart out.mp4

同分辨率下旧版码率只有 0.79 Mbps，暗部与快速运动段有明显色块；提到 1.8 Mbps 后鼓面文字、
镲片边缘、发丝都明显更锐。移动端不会自动下载（点 MV chip 才加载），首屏无额外负担。

注意版权，勿随公开部署分发。
