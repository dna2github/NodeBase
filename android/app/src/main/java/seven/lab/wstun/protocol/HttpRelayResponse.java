package seven.lab.wstun.protocol;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * HTTP response from a service client.
 */
public class HttpRelayResponse {

    @SerializedName("request_id")
    private String requestId;

    @SerializedName("status")
    private int status;

    @SerializedName("headers")
    private Map<String, String> headers;

    @SerializedName("body")
    private String body;

    @SerializedName("body_base64")
    private String bodyBase64;  // For binary data

    @SerializedName("streaming")
    private boolean streaming;

    @SerializedName("chunk_index")
    private int chunkIndex;

    @SerializedName("is_final")
    private boolean isFinal;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }
}
