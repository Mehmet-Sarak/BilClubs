# BilClubs — Server Utilities Reference

This document describes every class in the BilClubs backend. It covers constructors, methods, parameters, and any important design notes.

---

## Table of Contents

- [Server](#server)
  - [`BilClubsServer`](#bilclubsserver)
  - [`ServerConfig`](#serverconfig)
- [API Layer](#api-layer)
  - [`APIHandler`](#apihandler)
  - [`StreamReader`](#streamreader)
  - [`StaticFileHandler`](#staticfilehandler)
- [Authentication](#authentication)
  - [`LoginVerifier`](#loginverifier)
  - [`SecureTokenGenerator`](#securetokengenerator)
  - [`Sanitizer`](#sanitizer)
- [Database](#database)
  - [`DBManager`](#dbmanager)
  - [`Filter`](#filter)
- [Entities](#entities)
  - [`User`](#user)
  - [`Media`](#media)
  - [`Privileges`](#privileges)
- [HTTP Client](#http-client)
  - [`RequestManager`](#requestmanager)
  - [`Response`](#response)
- [Email Utilities](#email-utilities)
  - [`Credentials`](#credentials)
  - [`HTMLTemplate`](#htmltemplate)
  - [`MailMessage`](#mailmessage)
  - [`MailSession`](#mailsession)
  - [`MailTask`](#mailtask)

---

## Server

### `BilClubsServer`

The entry point of the application. Starts the HTTP server and registers the API and static file handler contexts.

#### Methods

| Element | Description |
| :--- | :--- |
| `main(String[] args)` | Initializes the database, creates the HTTP server on port `5000`, registers `/api` and `/` contexts, and starts listening |

#### Server Contexts

| Context | Handler | Description |
| :--- | :--- | :--- |
| `/api` | `APIHandler` | Routes all POST requests to the API handler and returns a JSON response |
| `/` | `StaticFileHandler` | Serves static files from `./static/` or falls back to the browser landing page |

**NOTE:** The server uses a cached thread pool executor (`Executors.newCachedThreadPool()`), meaning each incoming request may be handled on a separate thread. Any shared state accessed from handlers must be treated as concurrent.

---

<br>

### `ServerConfig`

A static configuration class holding global constants used throughout the server. Modify this file to change server-wide behaviour.

#### Constants

| Constant | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `PRINT_STACK_TRACES` | `boolean` | `true` | When enabled, full stack traces are printed on internal server errors |
| `PRINT_DEBUG` | `boolean` | `true` | When enabled, DB queries and request paths are printed to stdout |
| `SESSION_TOKEN_LENGTH` | `int` | `32` | The number of random characters in a generated session token (excluding the TTL suffix) |
| `SESSION_TOKEN_TTL` | `long` | `86400000` (1 day in ms) | How long a session token remains valid after generation, in milliseconds |
| `MAX_REQUEST_BYTES` | `long` | `16777216` (16 MB) | Maximum allowed size of a request body read by `StreamReader` |

**NOTE:** `PRINT_DEBUG` and `PRINT_STACK_TRACES` should be set to `false` in production. Leaving them enabled may leak internal details to logs accessible by unintended parties.

---

<br>

## API Layer

### `APIHandler`

The central handler for all API requests. Parses the request body, routes to the correct action, and returns a structured `JSONObject` response.

#### Methods

| Element | Description |
| :--- | :--- |
| `initializeDB()` | Initializes the `DBManager` with the `db` directory. Must be called before the server starts accepting requests |
| `handle(HttpExchange httpExchange)` | Parses the incoming HTTP exchange, routes to the matching action, and returns a `JSONObject` response |

#### Supported Endpoints

All endpoints expect a `POST` request with a JSON body. The action is determined by the second path segment (e.g. `/api/login` → action `login`).

| Action | Required Fields | Description |
| :--- | :--- | :--- |
| `signup` | `email`, `password`, `firstName`, `lastName` | Verifies credentials against Bilkent WebMail, creates a new user, and sends a welcome email |
| `login` | `email`, `password` | Verifies credentials, generates a new session token, persists it, and returns `sessionToken` and `userId` |
| `upload` | `userId`, `sessionToken`, `files` | Validates the session token, then saves each file in the `files` array to `./static/` and records a `Media` entry in the database |

#### `files` Array Schema (for `upload`)

Each object in the `files` JSON array must contain:

| Field | Type | Description |
| :--- | :--- | :--- |
| `fileName` | `String` | The original name of the file |
| `fileData` | `String` | Base64-encoded file content |
| `fileType` | `String` | File extension (e.g. `png`, `jpg`). Must be in the allowed list: `png`, `jpg`, `jpeg`, `pdf`, `gif` |

#### Response Format

All responses follow this structure:

```json
{
  "responseCode": 200,
  "success": true,
  "data": { ... },
  "error": { "message": "..." }
}
```

`data` is present on success; `error` is present on failure.


---

<br>

### `StreamReader`

Reads an `InputStream` into a `String`, enforcing a maximum byte limit to prevent request body-based DoS attacks.

#### Methods

| Element | Description |
| :--- | :--- |
| `readStream(InputStream stream)` | Reads the stream line by line into a `String`. Throws `IOException` if the total character count exceeds `ServerConfig.MAX_REQUEST_BYTES` |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `stream` | `readStream` | The `InputStream` to read, typically `HttpExchange.getRequestBody()` |

#### Throws

| Exception | Condition |
| :--- | :--- |
| `IOException` | Thrown when the total characters read exceed `ServerConfig.MAX_REQUEST_BYTES` |

**TODO:** The size check counts characters, not raw bytes. For multi-byte UTF-8 characters the actual byte count may exceed `MAX_REQUEST_BYTES` before the check triggers. For strict enforcement, count bytes at the raw stream level.

---

<br>

### `StaticFileHandler`

Serves files from the `./static/` directory. Prevents path traversal attacks by canonicalizing the requested path and verifying it remains within the static folder.

#### Methods

| Element | Description |
| :--- | :--- |
| `getFileSanitized(String path)` | Resolves the requested path, verifies it is within `./static/`, and returns the `File` if safe. Returns `null` on any violation or error |
| `handle(HttpExchange httpExchange)` | Determines the correct file to serve, sets the `Content-Type` header based on file extension, reads the file into a byte array, and returns it |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `path` | `getFileSanitized` | The raw URL path from the request URI (e.g. `/static/image.png`) |
| `httpExchange` | `handle` | The HTTP exchange from the server context |

#### Behaviour

| Request Path | Response |
| :--- | :--- |
| Starts with `/static/` | Attempts to serve the matching file from `./static/`. Falls back to `./templates/fileNotFound.html` if not found |
| Any other path | Serves `./templates/browserLanding.html` (the SPA entry point) |

---

<br>

## Authentication

### `LoginVerifier`

Verifies Bilkent University student credentials by authenticating against the WebMail server (`webmail.bilkent.edu.tr`). Intended strictly for server-side use.

#### Methods

| Element | Description |
| :--- | :--- |
| `verify(String username, String password)` | Performs a two-step HTTP handshake against the Bilkent WebMail login endpoint. Returns `true` if authentication succeeds, `false` otherwise |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `username` | `verify` | The student email address (e.g. `student@ug.bilkent.edu.tr`) |
| `password` | `verify` | The password associated with the provided email address |

#### Throws

| Exception | Condition |
| :--- | :--- |
| `IOException` | Network or stream error during the HTTP request |
| `ProtocolException` | Invalid HTTP method configuration |
| `MalformedURLException` | Malformed target URL |

**VERY IMPORTANT NOTE:** This class must only ever be called server-side. Client-side authentication checks can be bypassed by memory editing or recompilation. Upon successful verification, the server must generate and return a session token and user ID. All subsequent authenticated requests must supply these credentials, which the server validates against the database. A new token must be generated on every login so that a previously compromised token is automatically invalidated.

---

<br>

### `SecureTokenGenerator`

A stateless utility class for generating cryptographically secure session tokens with an embedded TTL timestamp. Uses a single shared `SecureRandom` instance to avoid repeated OS entropy seeding.

#### Methods

| Element | Description |
| :--- | :--- |
| `generate()` | Generates a random alphanumeric token of length `ServerConfig.SESSION_TOKEN_LENGTH`, appends a `:` separator and the expiry timestamp in milliseconds, and returns the full token string |

#### Token Format

```
<RANDOM_CHARS>:<EXPIRY_EPOCH_MS>
```

Example:
```
A3FX9KQZ1BNW7YRC2PLT8VHD4EMJ5SUI:1735000000000
```

The expiry value is `System.currentTimeMillis() + ServerConfig.SESSION_TOKEN_TTL` at the time of generation.

**NOTE:** The token charset is uppercase alphanumeric (`A-Z`, `0-9`), giving 36 possible characters per position. At the default length of 32 characters this provides `ln(36) / ln(2) * 32` (approximately 165) bits of entropy, which is sufficient (~128) for session tokens. The TTL is embedded in the token itself rather than stored separately in the database — `User.validateToken()` is responsible for parsing and checking it.

**TODO:** Migrate TTL to a separate column in the database.

---

<br>

### `Sanitizer`

A utility class for escaping special characters in strings before they are used in contexts that interpret backslashes or control characters.

#### Methods

| Element | Description |
| :--- | :--- |
| `sanitizeEscapedString(String string)` | Escapes `\`, `"`, `\n`, `\r`, and `\t` characters in the input string and returns the sanitized result |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `string` | `sanitizeEscapedString` | The raw input string to sanitize |

**TODO:** Sanitize all user provided data.

---

<br>

## Database

### `DBManager`

Manages all persistence operations using ObjectDB via JPA. Maintains two separate entity managers: one for `User` records and one for `Media` records.

#### Methods — Lifecycle

| Element | Description |
| :--- | :--- |
| `initialize(String directory)` | Creates `EntityManagerFactory` and `EntityManager` instances for both `users.odb` and `static.odb` databases in the given directory. No-op if already initialized |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `directory` | `initialize` | Path to the directory where the `.odb` database files are stored (e.g. `"db"`) |

#### Methods — User Operations

| Element | Description |
| :--- | :--- |
| `queryUsers(Filter filter)` | Executes a JPQL query for `User` entities matching all conditions in the filter. Returns a `List<User>`, or `null` if not initialized |
| `queryUser(Filter filter)` | Calls `queryUsers` and returns the single result. Returns `null` if the result count is not exactly 1 |
| `doesUniqueUserExist(Filter filter)` | Returns `true` if exactly one `User` matches the filter |
| `addUser(User user)` | Checks for email uniqueness, then persists the user. Returns `false` if a user with the same email already exists |
| `addUserUnsafe(User user)` | Persists the user without any uniqueness check. Use only when uniqueness is already guaranteed by the caller |
| `updateUser(User user)` | Merges an existing user by ID. Returns `false` if the user does not exist or the ID is null |

#### Methods — File Operations

| Element | Description |
| :--- | :--- |
| `queryFiles(Filter filter)` | Executes a JPQL query for `Media` entities matching all conditions in the filter. Returns a `List<Media>`, or `null` if not initialized |
| `queryFile(Filter filter)` | Calls `queryFiles` and returns the single result. Returns `null` if the result count is not exactly 1 |
| `addFile(Media media)` | Persists a new `Media` entity. Returns `true` on success, `false` if not initialized |

#### Supported Filter Keys — Users

| Key | Matches on |
| :--- | :--- |
| `id` | `User.getId()` |
| `token` | `User.getToken()` |
| `name` | `User.getFullName()` |
| `email` | `User.getEmail()` |

#### Supported Filter Keys — Files

| Key | Matches on |
| :--- | :--- |
| `id` | `Media.getId()` |
| `realName` | `Media.getRealFileName()` |
| `storedName` | `Media.getStoredFileName()` |

**NOTE:** Query values are bound using JPQL named parameters (`:paramName`) and are never concatenated directly into the query string, preventing JPQL injection attacks. Unknown filter keys are silently ignored.

<br><br>
**TODO:**
- Add `Club`, `Thread`, `Event` and `TimeTable`
- Add complex filtering. JPQL parameters can be suplied into function calls as `obj.functionCall(:p1, :p2, ..., :pn) = :pk`, which would be quite useful for specific features.

---

<br>

### `Filter`

A simple key-value container used to build conditional database queries. Each entry corresponds to one `WHERE`/`AND` clause in the resulting JPQL query.

#### Constructors & Methods

| Element | Description |
| :--- | :--- |
| `Filter()` | Creates an empty filter |
| `addFilter(String rule, Object value)` | Adds a filter condition keyed by `rule` with the given `value` |
| `clear()` | Removes all filter conditions |
| `getMap()` | Returns the underlying `Map<String, Object>` of filter conditions |
| `toString()` | Returns a human-readable representation of all active filter conditions |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `rule` | `addFilter` | The filter key (e.g. `"email"`, `"id"`). Must match a recognized key in `DBManager`'s key map |
| `value` | `addFilter` | The value to match against. Can be any `Object`; `.toString()` is used when binding |

---

<br>

## Entities

### `User`

A JPA entity representing a registered user. Stored in `users.odb`. The primary key is auto-generated by ObjectDB.

#### Constructors

| Element | Description |
| :--- | :--- |
| `User()` | No-argument constructor required by JPA |
| `User(String firstName, String lastName, String email)` | Creates a user with trimmed name and email fields |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `firstName` | Constructor, `setFirstName` | The user's first name. Whitespace is trimmed automatically |
| `lastName` | Constructor, `setLastName` | The user's last name. Whitespace is trimmed automatically |
| `email` | Constructor, `setEmail` | The user's email address. Whitespace is trimmed. Email cannot be changed once set |

#### Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `Integer` | Auto-generated primary key |
| `firstName` | `String` | User's first name |
| `lastName` | `String` | User's last name |
| `email` | `String` | Unique email address (`@Column(unique=true)`) |
| `token` | `String` | Current session token including embedded TTL. `null` if no active session |
| `embeddings` | `float[]` | Vector embeddings for interest-based matching |
| `interests` | `List<String>` | List of interest tags |
| `followedUsers` | `Set<Integer>` | IDs of followed users |
| `followedClubs` | `Set<Integer>` | IDs of followed clubs |
| `administeredClubs` | `Map<Integer, Integer>` | Map of club ID → privilege bitmask for clubs the user administers |
| `privileges` | `int` | Global privilege bitmask. Defaults to `Privileges.NORMAL_USER` |

#### Session Token Methods

| Element | Description |
| :--- | :--- |
| `generateToken()` | Calls `SecureTokenGenerator.generate()` and stores the result as the current token |
| `getToken()` | Returns the stored token string, or `null` if no token has been generated |
| `validateToken(String providedToken)` | Returns `true` if the provided token is non-null, matches the stored token, and has not passed its embedded TTL expiry timestamp |

#### Privilege Methods

| Element | Description |
| :--- | :--- |
| `isAdmin()` | Returns `true` if the global `ADMIN` bit is set in `privileges` |
| `hasClubPrivilege(Integer id, int privilegeType)` | Returns `true` if the user's privilege bitmask for the given club includes the specified privilege bit. `privilegeType` must be a single (non-composite) flag |
| `setClubPrivilege(Integer id, int privilege)` | Sets the privilege bitmask for a club. `privilege` may be a composite bitmask created with bitwise OR |
| `removeClubPrivilege(Integer id)` | Removes all club-level privileges for the given club ID |

#### Social Methods

| Element | Description |
| :--- | :--- |
| `followUser(Integer id)` | Adds a user ID to the followed users set |
| `unfollowUser(Integer id)` | Removes a user ID from the followed users set |
| `followClub(Integer id)` | Adds a club ID to the followed clubs set |
| `unfollowClub(Integer id)` | Removes a club ID from the followed clubs set |

#### Moderation Methods

| Element | Description |
| :--- | :--- |
| `banUser()` | Clears all embeddings, followed clubs/users, administered clubs, sets token to `null`, and sets privileges to `BANNED_USER` |
| `banFromClub(Integer id)` | Removes the club from followed clubs and sets club-level privileges to `BANNED_USER` |

---

<br>

### `Media`

A JPA entity representing an uploaded file. Stores the uploader's user ID, the original filename, and the server-side stored filename. Stored in `static.odb`.

#### Constructors

| Element | Description |
| :--- | :--- |
| `Media()` | No-argument constructor required by JPA |
| `Media(Integer userId, String realFileName, String storedFileName)` | Creates a media record with all fields set |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `userId` | Constructor, `setUserId` | The ID of the user who uploaded the file |
| `realFileName` | Constructor, `setRealFileName` | The original filename as provided by the client. Whitespace is trimmed |
| `storedFileName` | Constructor, `setStoredFileName` | The server-assigned filename under `./static/`. Whitespace is trimmed |

#### Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `Integer` | Auto-generated primary key |
| `userId` | `Integer` | ID of the uploading user |
| `realFileName` | `String` | Original client-side filename |
| `storedFileName` | `String` | Filename as stored on disk in `./static/` |

---

<br>

### `Privileges`

A constants class defining the privilege bitmask values used for both global user privileges and per-club privilege assignments.

#### Constants

| Constant | Binary | Decimal | Description |
| :--- | :--- | :--- | :--- |
| `BANNED_USER` | `0000` | `0` | No access. Used to explicitly ban a user globally or from a specific club |
| `NORMAL_USER` | `0001` | `1` | Default privilege level for all registered users |
| `MODERATOR` | `0010` | `2` | Moderator-level access |
| `MANAGER` | `0100` | `4` | Manager-level access |
| `ADMIN` | `1000` | `8` | Full administrative access |

**NOTE:** Privileges are designed as bitmasks. Composite privileges should be created using bitwise OR (e.g. `MODERATOR | MANAGER`). When checking a specific privilege, always use bitwise AND (`&`) against a single flag, not a composite one. See `User.hasClubPrivilege()` for the correct pattern.

---

<br>

## HTTP Client

### `RequestManager`

A client-side utility for sending HTTP POST requests and file uploads to the BilClubs server. Used primarily for testing and for building client-side integrations.

#### Methods

| Element | Description |
| :--- | :--- |
| `setDefaultAddress(String address)` | Sets the base server URL used for all subsequent requests. Trailing slashes are normalized automatically |
| `sendPostRequest(String ENDPOINT, JSONObject json)` | Sends a JSON POST request to the given endpoint and returns a `Response` object |
| `uploadFile(JSONObject json, File fileToUpload)` | Convenience wrapper that uploads a single file by delegating to `uploadFiles` |
| `uploadFiles(JSONObject json, ArrayList<File> filesToUpload)` | Reads each file, base64-encodes it, appends it to a `files` JSON array, and posts to `api/upload` |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `address` | `setDefaultAddress` | The base URL of the server (e.g. `"http://127.0.0.1:5000"`) |
| `ENDPOINT` | `sendPostRequest` | The API path relative to the base address (e.g. `"api/login"`) |
| `json` | `sendPostRequest`, `uploadFile`, `uploadFiles` | The JSON body to send. For uploads, the `files` array is added to this object automatically |
| `fileToUpload` | `uploadFile` | A single `File` object to upload |
| `filesToUpload` | `uploadFiles` | A list of `File` objects to upload in a single request |

---

<br>

### `Response`

A wrapper around a JSON API response. Provides typed accessors for the response code, success flag, error message, and payload data.

#### Constructors

| Element | Description |
| :--- | :--- |
| `Response()` | Creates a null response (e.g. for network failures). `isNullResponse()` returns `true` |
| `Response(JSONObject response)` | Parses a raw JSON API response object into a typed `Response` |

#### Methods

| Element | Description |
| :--- | :--- |
| `getCode()` | Returns the HTTP response code |
| `isSuccess()` | Returns `true` if the response code is in the 2xx range |
| `getErrorMessage()` | Returns the error message string, or an empty string if the response succeeded |
| `getPayload()` | Returns the `data` field of the response as a `JSONObject` |
| `payloadHasField(String key)` | Returns `true` if the payload contains the given key |
| `isNullResponse()` | Returns `true` if the response was created from a network failure (no server response received) |
| `toString()` | Returns a human-readable summary including code, success flag, and payload |

---

<br>

## Email Utilities

### `Credentials`

A data container for SMTP authentication details. Supports loading credentials from environment variables or directly from strings.

#### Constructors & Methods

| Element | Description |
| :--- | :--- |
| `Credentials(Map<String, String> environment)` | Reads `SMTP_EMAIL` and `SMTP_PASSWORD` from the provided environment map. Marks itself invalid if either is missing |
| `Credentials(String username, String password)` | Directly sets the username and password |
| `getUsername()` | Returns the stored username (sender email address) |
| `getPassword()` | Returns the stored password (SMTP app password) |
| `isValid()` | Returns `true` if both username and password are non-null |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `environment` | `Credentials(Map)` | A string map of environment variables, typically `System.getenv()` |
| `username` | `Credentials(String, String)` | The sender's email address |
| `password` | `Credentials(String, String)` | The app password for the sender's email account |

---

<br>

### `HTMLTemplate`

Loads an HTML file and provides methods for injecting values into `{{ key }}` placeholders. Supports both mutating (`format`) and non-mutating (`formatted`) operations to allow a single template to be reused across multiple requests.

#### Constructors & Methods

| Element | Description |
| :--- | :--- |
| `HTMLTemplate(String fileName)` | Loads the HTML file at the given path and stores its content as a string |
| `format(String key, String value)` | Mutates the template by replacing all `{{ key }}` placeholders with `value` |
| `format(HashMap<String, String> formatMap)` | Mutates the template by replacing all placeholders found in the map |
| `formatted(String key, String value)` | Returns a cloned `HTMLTemplate` with the substitution applied, leaving the original unchanged |
| `formatted(HashMap<String, String> formatMap)` | Returns a cloned `HTMLTemplate` with all substitutions from the map applied, leaving the original unchanged |
| `toString()` | Returns the current template content as a `String` |
| `clone()` | Returns a shallow copy of the template |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `fileName` | Constructor | Path to the HTML template file (e.g. `"templates/welcome.html"`) |
| `key` | `format`, `formatted` | The placeholder name to replace (without curly braces or spaces) |
| `value` | `format`, `formatted` | The string to inject in place of the placeholder |
| `formatMap` | `format`, `formatted` | A map of placeholder names to replacement strings for bulk substitution |

---

<br>

### `MailMessage`

A payload object containing the content, subject, and recipients of an email to be sent.

#### Methods

| Element | Description |
| :--- | :--- |
| `setContent(String content)` | Sets the plain-text body of the email |
| `setSubject(String subject)` | Sets the subject line of the email |
| `useHTML()` | Flags the message as HTML (sets `isHTML = true`) |
| `isHTML()` | Returns `true` if the message content is HTML |
| `fromTemplate(HTMLTemplate template)` | Sets the message body from the template's string content and automatically flags the message as HTML |
| `addRecipient(String recipient)` | Parses and adds an email address to the recipient list |
| `getContent()` | Returns the message body string |
| `getSubject()` | Returns the subject line |
| `getRecipients()` | Returns the recipient list as an `Address[]` array |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `content` | `setContent` | The raw string body of the email |
| `subject` | `setSubject` | The subject/title text for the email |
| `template` | `fromTemplate` | An `HTMLTemplate` object whose rendered string becomes the email body |
| `recipient` | `addRecipient` | A recipient email address string |

---

<br>

### `MailSession`

Manages the SMTP connection to Gmail and creates `MailTask` instances for asynchronous delivery.

#### Constructors & Methods

| Element | Description |
| :--- | :--- |
| `MailSession(Credentials credentials)` | Configures SMTP properties for Gmail (`smtp.gmail.com:587` with STARTTLS) and initializes the session if credentials are valid |
| `getTask(MailMessage msg)` | Creates and returns a `MailTask` ready to be submitted to an executor. Returns `null` if credentials are invalid |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `credentials` | Constructor | A `Credentials` object containing the sender's Gmail address and app password |
| `msg` | `getTask` | The configured `MailMessage` to be sent |

**NOTE:** `MailSession` is hardcoded to use Gmail's SMTP server. The password must be a Gmail App Password (16 characters), not the account's login password. App Passwords require two-factor authentication to be enabled on the sender account.

---

<br>

### `MailTask`

A `Runnable` that sends a single `MailMessage` via the provided `Session`. Intended to be submitted to a thread pool executor for non-blocking delivery.

#### Constructors & Methods

| Element | Description |
| :--- | :--- |
| `MailTask(MailMessage msg, Session session, Credentials credentials)` | Constructs a `MimeMessage` from the provided `MailMessage`, session, and sender credentials |
| `run()` | Sends the message via `Transport.send()`. Called automatically when submitted to an executor |

#### Parameters

| Parameter | Context | Description |
| :--- | :--- | :--- |
| `msg` | Constructor | The `MailMessage` containing content, subject, and recipients |
| `session` | Constructor | The authenticated `javax.mail.Session` provided by `MailSession` |
| `credentials` | Constructor | The sender's credentials, used to set the `From` address |

---

## Sample Code

```java
// Sign up a new user
JSONObject signupRequest = new JSONObject();
signupRequest.put("email", "student@ug.bilkent.edu.tr");
signupRequest.put("password", "bilkent_password");
signupRequest.put("firstName", "Ali");
signupRequest.put("lastName", "Yilmaz");
Response signupResponse = RequestManager.sendPostRequest("api/signup", signupRequest);

// Log in and retrieve a session token
JSONObject loginRequest = new JSONObject();
loginRequest.put("email", "student@ug.bilkent.edu.tr");
loginRequest.put("password", "bilkent_password");
Response loginResponse = RequestManager.sendPostRequest("api/login", loginRequest);
String token = loginResponse.getPayload().getString("sessionToken");
Integer userId = loginResponse.getPayload().getInt("userId");

// Upload a file
JSONObject uploadRequest = new JSONObject();
uploadRequest.put("sessionToken", token);
uploadRequest.put("userId", userId);
File fileToUpload = new File("photo.png");
Response uploadResponse = RequestManager.uploadFile(uploadRequest, fileToUpload);
System.out.println(uploadResponse);
```