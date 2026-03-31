import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;

public class APIHandler {
    private static final HTMLTemplate welcomeTemplate = new HTMLTemplate("templates/welcome.html");
    private static final ExecutorService concurrentExecutor = Executors.newCachedThreadPool();
    private static final Credentials credentials = new Credentials(System.getenv());
    public static DBManager manager = new DBManager();
    private static MailSession session = new MailSession(credentials);
    private static final Base64.Decoder base64Decoder = Base64.getDecoder();
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

    private static class AuthResult {
        User user;
        JSONObject errorResponse;
    }

    private static AuthResult authenticate(JSONObject requestBody) {
        AuthResult authResult = new AuthResult();
        if (!requestBody.has("userId") || !requestBody.has("sessionToken")) {
            authResult.errorResponse = buildResponse(401, null, "Missing credentials: userId or sessionToken not provided.");
            return authResult;
        }

        Integer userId = requestBody.optInt("userId", 0);
        String sessionToken = requestBody.optString("sessionToken", null);

        Filter userFilter = new Filter();
        userFilter.addFilter("id", userId);
        User user = manager.queryUser(userFilter);

        if (user == null) {
            authResult.errorResponse = buildResponse(403, null, "User does not exist.");
            return authResult;
        }

        if (!user.validateToken(sessionToken)) {
            authResult.errorResponse = buildResponse(403, null, "Invalid credentials.");
            return authResult;
        }

        authResult.user = user;
        return authResult;
    }

    private static JSONObject handleUserAction(String action, JSONObject requestBody) {
        switch (action) {
            case "signup":
                return signup(requestBody);
            case "login":
                return login(requestBody);
            case "logout":
            case "getProfile":
            case "updateProfile":
            case "setInterests":
            case "generateEmbeddings":
            case "listClubs":
            case "upload": {
                AuthResult authResult = authenticate(requestBody);
                if (authResult.errorResponse != null) return authResult.errorResponse;
                return handleAuthedUserAction(action, authResult.user, requestBody);
            }
            default:
                return buildResponse(400, null, "Unsupported user action.");
        }
    }

    private static JSONObject handleAuthedUserAction(String action, User user, JSONObject requestBody) {
        switch (action) {
            case "logout":
                return logout(user, requestBody);
            case "getProfile":
                return getProfile(user, requestBody);
            case "updateProfile":
                return updateProfile(user, requestBody);
            case "setInterests":
                return setInterests(user, requestBody);
            case "generateEmbeddings":
                return generateEmbeddings(user, requestBody);
            case "listClubs":
                return listUserClubs(user, requestBody);
            case "upload":
                return uploadFiles(user, requestBody);
            default:
                return buildResponse(400, null, "Unsupported user action.");
        }
    }

    private static JSONObject handleClubAction(String action, JSONObject requestBody) {
        AuthResult authResult = authenticate(requestBody);
        if (authResult.errorResponse != null) return authResult.errorResponse;

        switch (action) {
            case "create":
                return createClub(authResult.user, requestBody);
            default:
                return buildResponse(400, null, "Unsupported club action.");
        }
    }

    private static JSONObject signup(JSONObject requestBody) {
        String email = requestBody.optString("email", null);
        String password = requestBody.optString("password", null);
        String firstName = requestBody.optString("firstName", null);
        String lastName = requestBody.optString("lastName", null);
        String major = requestBody.optString("major", null);

        if (email == null || password == null || firstName == null || lastName == null) {
            return buildResponse(400, null, "Missing required fields: email, password, firstName, or lastName");
        }

        Filter emailFilter = new Filter();
        emailFilter.addFilter("email", email);
        if (manager.queryUser(emailFilter) != null) {
            return buildResponse(400, null, "Account already exists with the same email.");
        }

        boolean verified;
        try {
            verified = LoginVerifier.verify(email, password);
        } catch (IOException e) {
            return buildResponse(500, null, "Credential verification service error.");
        }
        if (!verified) {
            return buildResponse(400, null, "Incorrect email or password. Verification failed.");
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        if (major != null) newUser.setMajor(major);

        if (!manager.addUserUnsafe(newUser)) {
            return buildResponse(500, null, "Unknown database error.");
        }

        HTMLTemplate welcomeMessage = welcomeTemplate.formatted("name", newUser.getFullName());
        MailMessage message = new MailMessage();
        message.setSubject("Welcome to Bil'Clubs");
        message.fromTemplate(welcomeMessage);
        message.addRecipient(newUser.getEmail());
        MailTask mailTask = session.getTask(message);
        if (mailTask != null) concurrentExecutor.submit(mailTask);

        JSONObject data = new JSONObject();
        data.put("email", email);
        data.put("fullName", firstName + " " + lastName);
        return buildResponse(200, data, null);
    }

    private static JSONObject login(JSONObject requestBody) {
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

        if (userByEmail.isBanned()) {
            return buildResponse(403, null, "Account is banned.");
        }

        boolean verified;
        try {
            verified = LoginVerifier.verify(email, password);
        } catch (IOException e) {
            return buildResponse(500, null, "Credential verification service error.");
        }
        if (!verified) {
            return buildResponse(401, null, "Incorrect email or password. Verification failed.");
        }

        userByEmail.generateToken();
        manager.updateUser(userByEmail);
        JSONObject data = new JSONObject();
        data.put("sessionToken", userByEmail.getToken());
        data.put("userId", userByEmail.getId());
        return buildResponse(200, data, null);
    }

    private static JSONObject logout(User user, JSONObject requestBody) {
        user.clearToken();
        manager.updateUser(user);
        return buildResponse(200, null, null);
    }

    private static JSONObject getProfile(User user, JSONObject requestBody) {
        JSONObject data = new JSONObject();
        data.put("userId", user.getId());
        data.put("email", user.getEmail());
        data.put("firstName", user.getFirstName());
        data.put("lastName", user.getLastName());
        data.put("major", user.getMajor());
        data.put("interests", new JSONArray(user.getInterests()));
        data.put("clubIds", new JSONArray(user.getClubIds()));
        return buildResponse(200, data, null);
    }

    private static JSONObject updateProfile(User user, JSONObject requestBody) {
        if (requestBody.has("firstName")) {
            String firstName = requestBody.optString("firstName", "").trim();
            if (firstName.isEmpty()) return buildResponse(400, null, "firstName cannot be empty.");
            user.setFirstName(firstName);
        }
        if (requestBody.has("lastName")) {
            String lastName = requestBody.optString("lastName", "").trim();
            if (lastName.isEmpty()) return buildResponse(400, null, "lastName cannot be empty.");
            user.setLastName(lastName);
        }
        if (requestBody.has("major")) {
            String major = requestBody.optString("major", "").trim();
            if (major.isEmpty()) return buildResponse(400, null, "major cannot be empty.");
            user.setMajor(major);
        }
        if (requestBody.has("interests")) {
            JSONArray interests = requestBody.optJSONArray("interests", new JSONArray());
            ArrayList<String> interestsList = new ArrayList<>();
            for (Object interest : interests) {
                interestsList.add(interest.toString().trim());
            }
            user.setInterests(interestsList);
        }

        manager.updateUser(user);
        return buildResponse(200, null, null);
    }

    private static JSONObject setInterests(User user, JSONObject requestBody) {
        JSONArray interests = requestBody.optJSONArray("interests", new JSONArray());
        ArrayList<String> interestsList = new ArrayList<>();
        for (Object interest : interests) {
            interestsList.add(interest.toString().trim());
        }
        user.setInterests(interestsList);
        manager.updateUser(user);
        return buildResponse(200, null, null);
    }

    private static JSONObject generateEmbeddings(User user, JSONObject requestBody) {
        EmbeddingsTask embeddingsTask = new EmbeddingsTask(user);
        concurrentExecutor.submit(embeddingsTask);
        return buildResponse(200, null, null);
    }

    private static JSONObject listUserClubs(User user, JSONObject requestBody) {
        JSONArray clubArray = new JSONArray();
        for (Integer clubId : user.getClubIds()) {
            Filter clubFilter = new Filter();
            clubFilter.addFilter("id", clubId);
            Club club = manager.queryClub(clubFilter);
            if (club == null) continue;

            JSONObject clubJson = new JSONObject();
            clubJson.put("id", club.getId());
            clubJson.put("clubName", club.getClubName());
            clubJson.put("clubDescription", club.getClubDescription());
            clubArray.put(clubJson);
        }

        JSONObject data = new JSONObject();
        data.put("clubs", clubArray);
        return buildResponse(200, data, null);
    }

    private static JSONObject createClub(User user, JSONObject requestBody) {
        if (!user.hasGeneralPrivilege(Privileges.MANAGER) && !user.hasGeneralPrivilege(Privileges.ADMIN)) {
            return buildResponse(401, null, "Not authorized to create a new club.");
        }

        String clubName = requestBody.optString("clubName", null);
        String clubDescription = requestBody.optString("clubDescription", null);
        if (clubName == null || clubDescription == null) {
            return buildResponse(400, null, "Malformed JSON request body.");
        }

        Club newClub = new Club(clubName, clubDescription);
        if (!manager.addClub(newClub)) {
            return buildResponse(500, null, "Club could not be created due to a database error.");
        }

        newClub.addMember(user);
        user.joinClub(newClub);
        user.setClubPrivilege(newClub, Privileges.ADMIN);
        newClub.setMemberPrivilege(user, Privileges.ADMIN);

        boolean updatedClub = manager.updateClub(newClub);
        boolean updatedUser = manager.updateUser(user);
        if (!updatedClub || !updatedUser) {
            return buildResponse(500, null, "Club was created but membership information could not be persisted.");
        }

        JSONObject data = new JSONObject();
        data.put("clubId", newClub.getId());
        data.put("clubName", newClub.getClubName());
        return buildResponse(200, data, null);
    }

    private static JSONObject uploadFiles(User user, JSONObject requestBody) {
        JSONObject data = new JSONObject();
        JSONObject fileMap = new JSONObject();
        JSONObject fileStatus = new JSONObject();

        JSONArray files = requestBody.optJSONArray("files");
        if (files == null) {
            return buildResponse(400, null, "Missing or invalid 'files' array in request body.");
        }

        for (int index = 0; index < files.length(); index++) {
            JSONObject file;
            String fileName;
            try {
                file = files.getJSONObject(index);
                fileName = file.getString("fileName");
            } catch (Exception e) {
                continue;
            }
            try {
                String fileDataBase64 = file.getString("fileData");
                byte[] fileData = base64Decoder.decode(fileDataBase64);
                String fileExtension = file.getString("fileType").trim().toLowerCase();
                if (!allowedExtensions.contains(fileExtension)) continue;
                String newFileName = UUID.randomUUID().toString() + "." + fileExtension;
                File newFile = new File("./static/" + newFileName);
                try (FileOutputStream outputStream = new FileOutputStream(newFile)) {
                    outputStream.write(fileData);
                    outputStream.close();
                    Media media = new Media();
                    media.setUserId(user.getId());
                    media.setRealFileName(fileName);
                    media.setStoredFileName(newFileName);
                    manager.addFile(media);
                    fileMap.put(fileName, newFileName);
                    fileStatus.put(fileName, true);
                } catch (Exception e) {
                    newFile.delete();
                    fileStatus.put(fileName, false);
                }
            } catch (Exception e) {
                fileStatus.put(fileName, false);
                continue;
            }
        }

        data.put("fileMap", fileMap);
        data.put("fileStatus", fileStatus);

        return buildResponse(200, data, null); 
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

        String endpoint = pathParts[1];

        try {
            String action = requestBody.optString("action", "").trim();
            if (action.isEmpty()) {
                return buildResponse(400, null, "Missing required field: action");
            }

            if (endpoint.equals("user")) {
                return handleUserAction(action, requestBody);
            }

            if (endpoint.equals("club")) {
                return handleClubAction(action, requestBody);
            }

            return buildResponse(404, null, "Endpoint not found. Supported endpoints are /api/user and /api/club.");

        } catch (Exception e) {
            if (ServerConfig.PRINT_STACK_TRACES) e.printStackTrace();
            return buildResponse(500, null, "Internal server error.");
        }
    }
}