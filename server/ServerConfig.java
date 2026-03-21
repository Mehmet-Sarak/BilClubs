public class ServerConfig {
    public static final boolean PRINT_STACK_TRACES = true;
    public static final boolean PRINT_DEBUG = true;

    public static final int SESSION_TOKEN_LENGTH = 32;
    public static final long SESSION_TOKEN_TTL = 24 * 60 * 60 * 1000; // a day in milliseconds

    public static final long MAX_REQUEST_BYTES = 16 * 1024 * 1024;
}
