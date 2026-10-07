package ai.storywriter.mobile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Pure Java so connection boundaries can be tested without an emulator. */
public final class ServerAddress {
    public static final String APP_OPTIONS_URL = "yumina-app://options";
    private ServerAddress() {}

    public static String normalize(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter your Yumina server address.");
        if (!value.contains("://")) value = "http://" + value;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https"))
                throw new IllegalArgumentException("Use an http:// or https:// address.");
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")))
                throw new IllegalArgumentException("Use the server address only, without a path, password, or query.");
            int port = uri.getPort();
            if (port == 0 || port > 65535 || uri.getRawAuthority().endsWith(":"))
                throw new IllegalArgumentException("The port must be between 1 and 65535.");
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.startsWith("127.")
                    || host.equals("[::1]") || host.equals("[0:0:0:0:0:0:0:1]") || host.equals("0.0.0.0"))
                throw new IllegalArgumentException("Use your PC's Wi-Fi or LAN address. Localhost points to this phone.");
            return new URI(scheme, null, host, port, "/", null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That server address is not valid.");
        }
    }

    public static boolean sameOrigin(String server, String candidate) {
        try {
            URI a = new URI(server), b = new URI(candidate);
            return b.getUserInfo() == null && a.getHost() != null && b.getHost() != null
                    && a.getScheme().equalsIgnoreCase(b.getScheme())
                    && a.getHost().equalsIgnoreCase(b.getHost()) && port(a) == port(b);
        } catch (Exception e) { return false; }
    }

    /** Blob exports carry the creating page's origin before their UUID. */
    public static boolean sameOriginBlob(String server, String candidate) {
        return candidate != null && candidate.startsWith("blob:") && sameOrigin(server, candidate.substring(5));
    }

    /** Only an explicit main-page link from the chosen server may open native options. */
    public static boolean canOpenAppOptions(String server, String page, String candidate,
            boolean mainFrame, boolean gesture, boolean redirect) {
        return APP_OPTIONS_URL.equals(candidate) && mainFrame && gesture && !redirect && sameOrigin(server, page);
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }
}
