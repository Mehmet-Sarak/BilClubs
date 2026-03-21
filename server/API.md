# BilClubs API Reference

This document covers all HTTP API endpoints exposed by the BilClubs server. For class-level documentation of the handler itself, see the [API Handler](README.md#apihandler).

---

## Conventions

- **Base URL:** `http://<host>:5000`
- **Method:** only `POST` for API endpoints
- **Content-Type:** `application/json; charset=UTF-8`
- **Max body size:** `16 MB` (see `ServerConfig.MAX_REQUEST_BYTES`)
- **Authentication:** Endpoints marked with 🔒 require a `userId` and `sessionToken` pair obtained from [`/api/login`](#post-apilogin)

---

## Response envelope

Every response, success or failure, is wrapped in the same envelope:

```json
{
  "responseCode": 200,
  "success": true,
  "data": {...},
  "error": {
    "message": "Human readable error description"
  }
}
```

| Field | Type | Description |
| :--- | :--- | :--- |
| `responseCode` | `int` | Standard HTTP status code |
| `success` | `boolean` | `true` when `responseCode` is in the 2xx range |
| `data` | `object` | Present on success. Shape varies per endpoint, see below |
| `error` | `object` | Present on failure. Contains a `message` string |

---

## Endpoints

### `POST /api/signup`

Creates a new user account. Credentials are verified against Bilkent WebMail before the account is created. On success, a welcome email is sent asynchronously to the provided address.

#### Request body

```json
{
  "email":     "student@ug.bilkent.edu.tr",
  "password":  "bilkent_webmail_password",
  "firstName": "Ozan",
  "lastName":  "Özbek"
}
```

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `email` | `string` | ✅ | Bilkent WebMail address |
| `password` | `string` | ✅ | Bilkent WebMail password |
| `firstName` | `string` | ✅ | User's first name |
| `lastName` | `string` | ✅ | User's last name |

#### Success response `200`

```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "email":    "student@ug.bilkent.edu.tr",
    "fullName": "Ozan Özbek"
  }
}
```

#### Error responses

| Code | Condition |
| :--- | :--- |
| `400` | Missing required fields |
| `400` | An account with this email already exists |
| `400` | WebMail credential verification failed |
| `413` | Request body exceeds the maximum allowed size |
| `500` | Database error |

---

### `POST /api/login`

Verifies credentials against Bilkent WebMail, generates a new session token, persists it and returns it alongside the user ID. Every login invalidates the previous token, the token returned here is the only valid one until the next login.

#### Request body

```json
{
  "email":    "student@ug.bilkent.edu.tr",
  "password": "bilkent_webmail_password"
}
```

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `email` | `string` | ✅ | Bilkent WebMail address |
| `password` | `string` | ✅ | Bilkent WebMail password |

#### Success response `200`

```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "sessionToken": "A3FX9KQZ1BNW7YRC2PLT8VHD4EMJ5SUI:1735000000000", // sample token structure
    "userId": 42
  }
}
```

| Field | Type | Description |
| :--- | :--- | :--- |
| `sessionToken` | `string` | Opaque session token valid for 24 hours. Format: `<32 char alphanumeric string>:<expiry epoch ms>` |
| `userId` | `int` | Unique numeric ID of the authenticated user |

#### Error responses

| Code | Condition |
| :--- | :--- |
| `400` | Missing required fields |
| `401` | WebMail credential verification failed |
| `404` | No account found for this email |
| `413` | Request body exceeds the maximum allowed size |

---

### `POST /api/upload` 🔒

Uploads one or more files on behalf of an authenticated user. Each file must be base64 encoded in the request body. On success, the server saves each file to disk under `./static/` and records a `Media` entry in the database.

Files with an unrecognised `fileType` are silently skipped, they do not cause the entire request to fail.

#### Request body

```json
{
  "userId":       42,
  "sessionToken": "A3FX9KQZ1BNW7YRC2PLT8VHD4EMJ5SUI:1735000000000",
  "files": [
    {
      "fileName": "photo.png",
      "fileData": "<base64 encoded file content>",
      "fileType": "png"
    },
    {
      "fileName": "document.pdf",
      "fileData": "<base64 encoded file content>",
      "fileType": "pdf"
    }
  ]
}
```

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `userId` | `int` | ✅ | ID returned by `/api/login` |
| `sessionToken` | `string` | ✅ | Token returned by `/api/login` |
| `files` | `array` | ✅ | Array of file objects, see below |

##### File object

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `fileName` | `string` | ✅ | Original filename including extension |
| `fileData` | `string` | ✅ | Base64-encoded file content |
| `fileType` | `string` | ✅ | File extension without the leading dot. Allowed values: `png` `jpg` `jpeg` `pdf` `gif` |

#### Success response `200`

```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "fileMap": {
      "photo.png":    "7391057234891234.png",
      "document.pdf": "1234567890123456.pdf"
    }
  }
}
```

| Field | Type | Description |
| :--- | :--- | :--- |
| `fileMap` | `object` | Maps each accepted original `fileName` to its server assigned stored filename under `/static/` |

#### Error responses

| Code | Condition |
| :--- | :--- |
| `401` | `userId` or `sessionToken` field missing |
| `403` | No user found for the given `userId` |
| `403` | Session token is invalid or expired |
| `413` | Request body exceeds the maximum allowed size |

---

## Error reference

| Code | Meaning |
| :--- | :--- |
| `400` | Bad request, missing or invalid fields |
| `401` | Unauthenticated, credentials not provided |
| `403` | Forbidden, credentials provided but invalid or expired |
| `404` | Resource not found |
| `413` | Payload too large |
| `500` | Internal server error |
| `501` | Endpoint not implemented |

---

## Code example

```java
// Sign up
JSONObject signupRequest = new JSONObject();
signupRequest.put("email",     "student@ug.bilkent.edu.tr");
signupRequest.put("password",  "bilkent_password");
signupRequest.put("firstName", "Ozan");
signupRequest.put("lastName",  "Özbek");
Response signupResponse = RequestManager.sendPostRequest("api/signup", signupRequest);

// Log in
JSONObject loginRequest = new JSONObject();
loginRequest.put("email",    "student@ug.bilkent.edu.tr");
loginRequest.put("password", "bilkent_password");
Response loginResponse = RequestManager.sendPostRequest("api/login", loginRequest);

String token  = loginResponse.getPayload().getString("sessionToken");
int    userId = loginResponse.getPayload().getInt("userId");

// Upload a file
JSONObject uploadRequest = new JSONObject();
uploadRequest.put("userId",       userId);
uploadRequest.put("sessionToken", token);
Response uploadResponse = RequestManager.uploadFile(uploadRequest, new File("photo.png"));

System.out.println(uploadResponse);
// Response{code=200, succeeded=true, payload={"fileMap":{"photo.png":"7391057234.png"}}}
```