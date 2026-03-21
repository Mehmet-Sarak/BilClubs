import java.security.SecureRandom;

public class SecureTokenGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

    public static String generate() {
        int length = ServerConfig.SESSION_TOKEN_LENGTH;
        StringBuilder tokenBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            tokenBuilder.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        long ttlValue = System.currentTimeMillis() + ServerConfig.SESSION_TOKEN_TTL;
        tokenBuilder.append(':');
        tokenBuilder.append(ttlValue);
        return tokenBuilder.toString();
    }
}
