import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.json.*;
 
public class BilClubsServer {

    private final static HttpHandler api = new HttpHandler() {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            JSONObject response = APIHandler.handle(httpExchange);
            final byte[] out = (response.toString()).getBytes("UTF-8");
            httpExchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            int responseCode = response.optInt("responseCode", 400);
            httpExchange.sendResponseHeaders(responseCode, out.length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(out);
            os.close();
        }
    };

    private final static HttpHandler staticFile = new HttpHandler() {
        
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {
                final byte[] out = StaticFileHandler.handle(httpExchange);
                httpExchange.sendResponseHeaders(200, out.length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(out);
                os.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    public static void main(String[] args) throws Exception {

        APIHandler.initializeDB();

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(5000), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/api", api);
        httpServer.createContext("/", staticFile);
        httpServer.start();

        // TODO: we need a rate limiter

        System.out.println("Server Started");
      

    }
}
