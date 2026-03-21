import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

public class StaticFileHandler {

    private static final File notFoundFile = new File("./templates/fileNotFound.html");
    private static final File browserLanding = new File("./templates/browserLanding.html");

    private static final Map<String, String> contentTypeMap =  new HashMap<String, String>() {{
        put("jpg",  "image/jpeg");
        put("jpeg", "image/jpeg");
        put("png",  "image/png");
        put("gif",  "image/gif");
        put("webp", "image/webp");
        put("svg",  "image/svg+xml");
        put("ico",  "image/x-icon");
        put("mp4",  "video/mp4");
        put("mp3",  "audio/mpeg");
        put("pdf",  "application/pdf");
        put("txt",  "text/plain");
        put("html", "text/html");
        put("json", "application/json");
        put("xml",  "application/xml");
    }};

    public static File getFileSanitized(String path) {
        try {
            File staticFolder = new File("./static/");
            File requestedFile = new File(".".concat(path));
            if (!requestedFile.isFile()) return null;
            String resolvedPath = requestedFile.getCanonicalPath();
            String folderPath = staticFolder.getCanonicalPath();
            if (resolvedPath.startsWith(folderPath)) {
                return requestedFile;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public static byte[] handle(HttpExchange httpExchange) {
        byte[] out = {};

        File requestedFile;

        String path = httpExchange.getRequestURI().getRawPath();

        if (path.startsWith("/static/")) {
            requestedFile = getFileSanitized(path);
            if (requestedFile == null) {
                requestedFile = notFoundFile;
            }
            String absolutePath = requestedFile.getAbsolutePath();
            String fileExtension = absolutePath.substring(1 + absolutePath.lastIndexOf('.')).toLowerCase().trim();
            String contentType = contentTypeMap.getOrDefault(fileExtension, "text/plain");
            httpExchange.getResponseHeaders().set("Content-Type", contentType.concat("; charset=UTF-8"));
        } else {
            requestedFile = browserLanding;
            httpExchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        }
        
        try {
            int size = (int) requestedFile.length();
            out = new byte[size];
            InputStream inputStream = new FileInputStream(requestedFile);
            inputStream.read(out);
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out;
    }
}
