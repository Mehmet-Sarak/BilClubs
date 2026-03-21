import org.json.JSONObject;

public class Response {
    private boolean nullResponse = true;
    private int code = 0;
    private boolean succeeded = false;
    private String errorMessage = "";
    private JSONObject rawData;

    Response() {}

    Response(JSONObject response) {
        code = response.optInt("responseCode", 418);
        succeeded = response.optBoolean("success", false);
        rawData = response.optJSONObject("data", new JSONObject());
        if (!succeeded) {
            try {
                errorMessage = response.getJSONObject("error").optString("message", "No error message provided.");
            } catch (Exception e) {
                errorMessage = "";
            }
        }
        nullResponse = false;
    }

    public int getCode() {
        return code;
    }

    public boolean isSuccess() {
        return succeeded;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JSONObject getPayload() {
        return rawData;
    }

    public boolean payloadHasField(String key) {
        return rawData.has(key);
    }

    public boolean isNullResponse() {
        return nullResponse;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Response{");
        builder.append("code=");
        builder.append(getCode());
        builder.append(", succeeded=");
        builder.append(isSuccess());
        if (!isSuccess()) {
            builder.append(", message=");
            builder.append(getErrorMessage());
        }
        builder.append(", payload=");
        builder.append(getPayload().toString());
        builder.append("}");
        return builder.toString();
    }
}
