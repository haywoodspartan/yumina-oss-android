package ai.storywriter.mobile;

public final class ServerAddressTest {
    private static int checks;
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    private static void reject(String value) {
        try { ServerAddress.normalize(value); throw new AssertionError("Accepted " + value); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        check(ServerAddress.normalize(" 10.45.3.238:8000 ").equals("http://10.45.3.238:8000/"), "LAN default");
        check(ServerAddress.normalize("HTTPS://STORIES.EXAMPLE:443/").equals("https://stories.example:443/"), "Case and TLS");
        check(ServerAddress.normalize("http://[fd00::123]:8000").equals("http://[fd00::123]:8000/"), "IPv6 LAN");
        check(ServerAddress.sameOrigin("https://stories.example/", "https://STORIES.example:443/api/chats?q=1"), "Default port");
        check(ServerAddress.sameOrigin("http://10.45.3.238:8000/", "http://10.45.3.238:8000/api/world-library"), "Same server");
        check(ServerAddress.sameOriginBlob("https://stories.example/", "blob:https://stories.example:443/export-id"), "Own Blob export");
        for (String url : new String[]{null, "blob:null/export-id", "blob:https://evil.test/export-id", "blob:http://stories.example/export-id", "blob:https://stories.example:8000/export-id", "blob:https://evil@stories.example/export-id", "https://stories.example/export-id"})
            check(!ServerAddress.sameOriginBlob("https://stories.example/", url), "Foreign Blob: " + url);
        for (String url : new String[]{"http://stories.example/", "https://stories.example:8000/", "https://stories.example.evil/", "https://stories.example@evil.test/", "https://evil@stories.example/", "javascript:alert(1)", "file:///etc/passwd", "intent://stories.example", "//stories.example/path", "not a URI"})
            check(!ServerAddress.sameOrigin("https://stories.example/", url), "Foreign navigation: " + url);
        for (String url : new String[]{"", "localhost:8000", "127.0.0.1:8000", "http://[::1]:8000", "0.0.0.0:8000", "ftp://host.test", "https://user:password@host.test", "https://host.test/path", "https://host.test/?secret=x", "https://host.test/#chat", "http://host.test:0", "http://host.test:65536", "http://host.test:", "http://host.test:-2", "http://bad host.test"}) reject(url);
        String origin = "https://stories.example/", page = "https://stories.example/app/settings";
        check(ServerAddress.canOpenAppOptions(origin, page, "yumina-app://options", true, true, false), "Explicit app options link");
        check(!ServerAddress.canOpenAppOptions(origin, page, "yumina-app://options", false, true, false), "No iframe app options");
        check(!ServerAddress.canOpenAppOptions(origin, page, "yumina-app://options", true, false, false), "No scripted app options");
        check(!ServerAddress.canOpenAppOptions(origin, page, "yumina-app://options", true, true, true), "No redirected app options");
        for (String source : new String[]{null, "https://evil.test/", "http://stories.example/", "https://stories.example:8000/", "https://evil@stories.example/"})
            check(!ServerAddress.canOpenAppOptions(origin, source, "yumina-app://options", true, true, false), "No foreign page command: " + source);
        for (String command : new String[]{null, "yumina-app://options/", "yumina-app://options?server=https://evil.test", "yumina-app://options#clear", "yumina-app://clear", "yumina-app://user@options", "intent://options"})
            check(!ServerAddress.canOpenAppOptions(origin, page, command, true, true, false), "Only the exact options command: " + command);
        System.out.println(checks + " server address and origin boundary checks passed.");
    }
}
