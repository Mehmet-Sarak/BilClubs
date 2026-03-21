import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;

public class APIHandler {

    private static final HTMLTemplate welcomeTemplate = new HTMLTemplate("templates/welcome.html");

    private static final ExecutorService concurrentExecutor = Executors.newCachedThreadPool();

    private static final Credentials credentials = new Credentials(System.getenv());

    private static DBManager manager = new DBManager();
    private static MailSession session = new MailSession(credentials);

    private static Decoder base64Decoder = Base64.getDecoder();

    private static final List<String> allowedExtensions = List.of("png", "jpg", "jpeg", "pdf", "gif");

    public static void initializeDB() {
        manager.initialize("db");
    }

    private static JSONObject buildResponse(int code, JSONObject data, String errorMessage) {
        JSONObject response = new JSONObject();
        response.put("responseCode", code);
        response.put("success", code >= 200 && code < 300);
        
        if (data != null) {
            response.put("data", data);
        }
        
        if (errorMessage != null) {
            JSONObject errorObj = new JSONObject();
            errorObj.put("message", errorMessage);
            response.put("error", errorObj);
        }
        
        return response;
    }
    
    public static JSONObject handle(HttpExchange httpExchange) {
        String remoteAddress = httpExchange.getRemoteAddress().toString() + "/";
        String path = httpExchange.getRequestURI().getRawPath();

        if (!httpExchange.getRequestMethod().equals("POST")) {
            return buildResponse(400, null, "Method not allowed. Only POST is supported.");
        }

        JSONObject requestBody;
        try {
            requestBody = new JSONObject(StreamReader.readStream(httpExchange.getRequestBody()));
        } catch (IOException e) {
            return buildResponse(413, null, "Request body is too large. Maximum allowed size is " + ServerConfig.MAX_REQUEST_BYTES + " bytes.");
        } catch (Exception e) {
            return buildResponse(400, null, "Malformed JSON request body.");
        }

        if (ServerConfig.PRINT_DEBUG) System.out.printf("%s : %s\n", remoteAddress, path);

        if (!path.startsWith("/api/")) {
            return buildResponse(501, null, "Not implemented.");
        }

        String[] pathParts = path.substring(1).split("/");
        if (pathParts.length < 2) {
            return buildResponse(400, null, "Invalid API endpoint");
        }

        String action = pathParts[1];

        try {
            if (action.equals("signup")) {
                String email = requestBody.optString("email", null);
                String password = requestBody.optString("password", null);
                String firstName = requestBody.optString("firstName", null);
                String lastName = requestBody.optString("lastName", null);
                
                if (email == null || password == null || firstName == null || lastName == null) {
                    return buildResponse(400, null, "Missing required fields: email, password, firstName, or lastName");
                }
                
                Filter emailFilter = new Filter();
                emailFilter.addFilter("email", email);
                
                if (manager.queryUser(emailFilter) != null) {
                    return buildResponse(400, null, "Account already exists with the same email.");
                }
                
                if (LoginVerifier.verify(email, password)) {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFirstName(firstName);
                    newUser.setLastName(lastName);
                    
                    if (manager.addUserUnsafe(newUser)) {
                        HTMLTemplate welcomeMessage = welcomeTemplate.formatted("name", newUser.getFullName());
                        MailMessage message = new MailMessage();
                        message.setSubject("Welcome to Bil'Clubs");
                        message.fromTemplate(welcomeMessage);
                        message.addRecipient(newUser.getEmail());
                        MailTask task = session.getTask(message);
                        concurrentExecutor.submit(task);
                        JSONObject data = new JSONObject();
                        data.put("email", email);
                        data.put("fullName", firstName + " " + lastName);
                        return buildResponse(200, data, null);
                    } else {
                        return buildResponse(500, null, "Unknown database error.");
                    }
                } else {
                    return buildResponse(400, null, "Incorrect email or password. Verification failed.");
                }
            }

            if (action.equals("login")) {
                String email = requestBody.optString("email", null);
                String password = requestBody.optString("password", null);
                
                if (email == null || password == null) {
                    return buildResponse(400, null, "Missing required fields: email or password");
                }
                
                Filter emailFilter = new Filter();
                emailFilter.addFilter("email", email);
                User userByEmail = manager.queryUser(emailFilter);
                
                if (userByEmail == null) {
                    return buildResponse(404, null, "Account does not exist.");
                }
                
                if (LoginVerifier.verify(email, password)) {
                    userByEmail.generateToken();
                    manager.updateUser(userByEmail);
                    JSONObject data = new JSONObject();
                    data.put("sessionToken", userByEmail.getToken());
                    data.put("userId", userByEmail.getId());
                    return buildResponse(200, data, null);
                } else {
                    return buildResponse(401, null, "Incorrect email or password. Verification failed.");
                }
            }


            if (action.equals("upload")) {
                if (!requestBody.has("userId") || !requestBody.has("sessionToken")) {
                    return buildResponse(401, null, "Missing credentials: userId or sessionToken not provided.");
                }
                
                Integer userId = requestBody.optInt("userId", 0);
                String sessionToken = requestBody.optString("sessionToken", null);
                
                Filter userFilter = new Filter();
                userFilter.addFilter("id", userId);

                User user = manager.queryUser(userFilter);

                if (user == null) {
                    return buildResponse(403, null, "User does not exist.");
                }

                if (!user.validateToken(sessionToken)) {
                    return buildResponse(403, null, "Invalid credentials.");
                }

                JSONObject data = new JSONObject();
                JSONObject fileMap = new JSONObject();

                JSONArray files = requestBody.getJSONArray("files");

                for (int index = 0; index < files.length(); index++) {
                    JSONObject file = files.getJSONObject(index);
                    try {
                        String fileName = file.getString("fileName");
                        String fileDataBase64 = file.getString("fileData");
                        byte[] fileData = base64Decoder.decode(fileDataBase64);
                        String fileExtension = file.getString("fileType");
                        if (!allowedExtensions.contains(fileExtension)) continue;
                        Long hash = ((long) fileDataBase64.hashCode()) * 961 + ((long) fileName.hashCode()) * 31 + ((long) sessionToken.hashCode());
                        // we can use the session token to reduce collisions
                        // why are we avoiding collisions anyway???
                        String newFileName = hash.toString() + "." + fileExtension;
                        File newFile = new File("./static/" + newFileName);
                        FileOutputStream outputStream = new FileOutputStream(newFile);
                        outputStream.write(fileData);
                        outputStream.close();
                        Media media = new Media();
                        media.setUserId(userId);
                        media.setRealFileName(fileName);
                        media.setStoredFileName(newFileName);
                        manager.addFile(media);

                        fileMap.put(fileName, newFileName);
                    } catch (Exception e) {
                        /*
                            of course my code is perfect and the only possible source
                            of an exception is a malformed request  :))
                        */ 
                        continue;
                    }
                }

                data.put("fileMap", fileMap);

                return buildResponse(200, data, null); 
            }

            return buildResponse(404, null, "Endpoint action not found");

        } catch (Exception e) {
            if (ServerConfig.PRINT_STACK_TRACES) e.printStackTrace();
            return buildResponse(500, null, "Internal server error: " + e.getMessage());
        }
    }
}