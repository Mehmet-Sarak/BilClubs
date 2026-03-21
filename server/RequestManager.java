import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;


public class RequestManager {

    private static String defaultAddress = "http://127.0.0.1:5000/";

    private static Encoder base64Encoder = Base64.getEncoder();

    public static void setDefaultAddress(String address) {
        address = address.replaceAll("/+$", "");
        defaultAddress = address + "/";
    }

    public static Response sendPostRequest(String ENDPOINT, JSONObject json) {
        try {
            URL url = new URL(defaultAddress + ENDPOINT);

            HttpURLConnection conn = (HttpURLConnection)(url.openConnection());
            
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();

            InputStream stream = responseCode < 400 ? conn.getInputStream() : conn.getErrorStream();
            String responseString = StreamReader.readStream(stream);

            conn.disconnect();
            return new Response(new JSONObject(responseString.trim()));    
        }
        catch (Exception e) {
            e.printStackTrace();
            return new Response();
        }
    }

    public static Response uploadFile(JSONObject json, File fileToUpload) {
        ArrayList<File> filesToUpload = new ArrayList<>(1);
        filesToUpload.add(fileToUpload);
        Response response = uploadFiles(json, filesToUpload);
        return response;
    }

    public static Response uploadFiles(JSONObject json, ArrayList<File> filesToUpload) {
        /*
            we will save the files with a new file name created by their names + content hashes (?)
            then, an entry with
                user ID
                file hash
                file's real name
                additional file information
                IP address (? may not need this if we have a general access log)
            will be saved in the database
        */

        JSONArray files = new JSONArray();

        for (File file : filesToUpload) {
            byte[] fileData;

            try {
                int size = (int) file.length();
                fileData = new byte[size];
                InputStream inputStream = new FileInputStream(file);
                inputStream.read(fileData);
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            String fileName = file.getName();
            String fileDataBase64 = base64Encoder.encodeToString(fileData);

            int lastIndexOfDot = fileName.lastIndexOf('.');
            String fileExtension = fileName.substring(lastIndexOfDot + 1);

            JSONObject fileObject = new JSONObject();
            fileObject.put("fileName", fileName);
            fileObject.put("fileData", fileDataBase64);
            fileObject.put("fileType", fileExtension);

            files.put(fileObject);
        }

        json.put("files", files);

        Response response = sendPostRequest("api/upload", json);

        return response;
    }
}
