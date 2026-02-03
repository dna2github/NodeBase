package seven.lab.wstun.protocol;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * HTTP request relayed to a service client.
 */
public class HttpRelayRequest {

    @SerializedName("request_id")
    private String requestId;

    @SerializedName("method")
    private String method;

    @SerializedName("path")
    private String path;

    @SerializedName("query")
    private String query;

    @SerializedName("headers")
    private Map<String, String> headers;

    @SerializedName("body")
    private String body;

    @SerializedName("body_base64")
    private String bodyBase64;  // For binary data

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBodyBase64() {
        return bodyBase64;
    }

    public void setBodyBase64(String bodyBase64) {
        this.bodyBase64 = bodyBase64;
    }
}
