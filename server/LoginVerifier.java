/*
    A class for verifying user credentials via WebMail's API.
    Intended for server-side use.
*/

import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LoginVerifier {

    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";

    static final String ENDPOINT = "https://webmail.bilkent.edu.tr/";

    public static boolean verify(String username, String password)
            throws IOException, ProtocolException, MalformedURLException {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        URL url = new URL(ENDPOINT);

        HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Referer", ENDPOINT);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Origin", ENDPOINT.substring(0, ENDPOINT.length() - 1));

        if (conn.getResponseCode() >= 400) {
            return false;
        }

        StringBuilder builder = new StringBuilder();
        try (InputStream is = conn.getInputStream()) {
            byte[] response = is.readAllBytes();
            builder.append(new String(response, StandardCharsets.UTF_8));
        }
        String responseString = builder.toString();
        int tokenStartIndex = responseString.indexOf("name=\"_token\" value=\"");
        int tokenEndIndex = responseString.indexOf("\"", tokenStartIndex + 23);
        String token = responseString.substring(tokenStartIndex + 21, tokenEndIndex);

        HttpURLConnection conn2 = (HttpURLConnection) (url.openConnection());

        conn2.setConnectTimeout(5000);
        conn2.setReadTimeout(5000);

        conn2.setRequestMethod("POST");

        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn2.setRequestProperty("User-Agent", USER_AGENT);
        conn2.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn2.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn2.setRequestProperty("Connection", "keep-alive");
        conn2.setRequestProperty("Referer", ENDPOINT);
        conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn2.setRequestProperty("Origin", ENDPOINT.substring(0, ENDPOINT.length() - 1));
        conn2.setRequestProperty("X-Roundcube-Request", token);

        String formData = "_task=login" +
                "&_action=login" +
                "&_timezone=" + URLEncoder.encode("Europe/Istanbul", StandardCharsets.UTF_8) +
                "&_url=" +
                "&_user=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                "&_pass=" + URLEncoder.encode(password, StandardCharsets.UTF_8) +
                "&_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        try (OutputStream os = conn2.getOutputStream()) {
            os.write(formData.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn2.getResponseCode();

        conn.disconnect();
        conn2.disconnect();

        if (responseCode >= 400) {
            return false;
        } else if (responseCode < 300 && responseCode >= 200) {
            // FIXME: Apparently this is not enough for verification, we need to check if a
            // session token cookie exists
            return true;
        } else {
            return false;
        }
    }
}
