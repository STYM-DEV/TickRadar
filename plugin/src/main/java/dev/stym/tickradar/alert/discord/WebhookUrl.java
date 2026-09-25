package dev.stym.tickradar.alert.discord;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebhookUrl {

    public static final String TEST_ENDPOINT_PROPERTY = "tickradar.discord.test-endpoint";
    public static final String EXPECTED_FORM = "https://discord.com/api/webhooks/<id>/<token>";
    private static final Set<String> HOSTS = Set.of("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");
    private static final Pattern PATH = Pattern.compile("/api(?:/v[0-9]{1,2})?/webhooks/([0-9]{5,25})/([A-Za-z0-9_-]{20,200})/?");
    private static final Pattern QUERY = Pattern.compile("thread_id=[0-9]{5,25}");
    private static final int HTTPS_PORT = 443;
    private static final int VISIBLE_ID_DIGITS = 4;
    private static final String HIDDEN = "****";
    private static final Pattern IPV4 = Pattern.compile("([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})");
    private static final Set<String> LOOPBACK_NAMES = Set.of("localhost", "[::1]", "[0:0:0:0:0:0:0:1]");

    private final String raw;
    private final URI target;
    private final String host;
    private final String id;
    private final String token;
    private final String endpointNotice;

    private WebhookUrl(String raw, URI target, String host, String id, String token, String endpointNotice) {
        this.raw = raw;
        this.target = target;
        this.host = host;
        this.id = id;
        this.token = token;
        this.endpointNotice = endpointNotice;
    }

    public static WebhookUrl parse(String raw) throws InvalidWebhookUrlException {
        return parse(raw, System.getProperty(TEST_ENDPOINT_PROPERTY));
    }

    static WebhookUrl parse(String raw, String testEndpoint) throws InvalidWebhookUrlException {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidWebhookUrlException("the webhook URL is empty");
        }
        URI uri = toUri(trimmed);
        String host = checkedHost(uri);
        Matcher path = checkedPath(uri);
        checkQuery(uri);
        String endpoint = testEndpoint == null ? "" : testEndpoint.trim();
        if (endpoint.isEmpty()) {
            return new WebhookUrl(trimmed, uri, host, path.group(1), path.group(2), null);
        }
        URI base = localEndpoint(endpoint);
        if (base == null) {
            return new WebhookUrl(trimmed, uri, host, path.group(1), path.group(2), "The system property -D"
                    + TEST_ENDPOINT_PROPERTY + " is ignored: it must be an http(s) URL whose host is localhost or a"
                    + " loopback or private IP address. Discord alerts go to Discord.");
        }
        return new WebhookUrl(trimmed, redirected(base, uri), host, path.group(1), path.group(2), "The system property -D"
                + TEST_ENDPOINT_PROPERTY + " is set: Discord alerts are sent to " + base.getHost()
                + " instead of Discord. Remove it outside of tests.");
    }

    public String endpointNotice() {
        return endpointNotice;
    }

    public String masked() {
        return "https://" + host + "/api/webhooks/" + id.substring(0, Math.min(VISIBLE_ID_DIGITS, id.length())) + ".../" + HIDDEN;
    }

    public String redact(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(raw, masked()).replace(token, HIDDEN);
    }

    URI target() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WebhookUrl url && url.raw.equals(raw) && url.target.equals(target);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return masked();
    }

    private static URI toUri(String text) throws InvalidWebhookUrlException {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new InvalidWebhookUrlException("the webhook URL is not a valid URL");
        }
    }

    private static String checkedHost(URI uri) throws InvalidWebhookUrlException {
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw new InvalidWebhookUrlException("the webhook URL must start with https://");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!HOSTS.contains(host) || uri.getRawUserInfo() != null) {
            throw new InvalidWebhookUrlException("the webhook URL must point to discord.com");
        }
        if (uri.getPort() != -1 && uri.getPort() != HTTPS_PORT) {
            throw new InvalidWebhookUrlException("the webhook URL must not set a port");
        }
        return host;
    }

    private static Matcher checkedPath(URI uri) throws InvalidWebhookUrlException {
        Matcher path = PATH.matcher(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (!path.matches()) {
            throw new InvalidWebhookUrlException("the webhook URL must look like " + EXPECTED_FORM);
        }
        return path;
    }

    private static void checkQuery(URI uri) throws InvalidWebhookUrlException {
        boolean badQuery = uri.getRawQuery() != null && !QUERY.matcher(uri.getRawQuery()).matches();
        if (badQuery || uri.getRawFragment() != null) {
            throw new InvalidWebhookUrlException("the webhook URL must look like " + EXPECTED_FORM);
        }
    }

    private static URI localEndpoint(String endpoint) {
        String base = endpoint;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            URI uri = new URI(base);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean plain = uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null;
            boolean web = scheme.equals("http") || scheme.equals("https");
            return web && plain && uri.getHost() != null && isLocalHost(uri.getHost()) ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    static boolean isLocalHost(String candidate) {
        String name = candidate.toLowerCase(Locale.ROOT);
        if (LOOPBACK_NAMES.contains(name)) {
            return true;
        }
        Matcher ipv4 = IPV4.matcher(name);
        if (!ipv4.matches()) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < octets.length; i++) {
            octets[i] = Integer.parseInt(ipv4.group(i + 1));
            if (octets[i] > 255) {
                return false;
            }
        }
        return octets[0] == 127 || octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    private static URI redirected(URI base, URI uri) {
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        try {
            return new URI(base + uri.getRawPath() + query);
        } catch (URISyntaxException e) {
            return uri;
        }
    }
}
