import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class StreamReader {
    public static String readStream(InputStream stream) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            long totalChars = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                totalChars += line.length();
                if (totalChars > ServerConfig.MAX_REQUEST_BYTES) {
                    throw new IOException("Request body exceeds maximum allowed size.");
                }
                builder.append(line);
            }
            return builder.toString();
        }
    }
}