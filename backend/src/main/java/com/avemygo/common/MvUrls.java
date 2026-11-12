package com.avemygo.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** MV 来源边界；域名通过校验后仍需管理员人工核实是否为官方上传。 */
public final class MvUrls {

    private static final Set<String> HOSTS = Set.of(
            "bilibili.com", "www.bilibili.com", "player.bilibili.com",
            "youtube.com", "www.youtube.com", "youtu.be");

    private MvUrls() {}

    /** null/空白表示清空；其余只接受可信平台的 http(s) 地址。 */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int defaultPort = "https".equals(scheme) ? 443 : 80;
            if (url.length() <= 255 && ("https".equals(scheme) || "http".equals(scheme))
                    && HOSTS.contains(host) && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == defaultPort)) {
                return url;
            }
        } catch (URISyntaxException ignored) {
            // 对格式错误和非可信来源返回同一参数错误，不将解析器异常暴露给客户端。
        }
        throw new BizException(40001, "MV 地址须为 B 站或 YouTube 的 http(s) 链接，不含账号或非标准端口");
    }
}
