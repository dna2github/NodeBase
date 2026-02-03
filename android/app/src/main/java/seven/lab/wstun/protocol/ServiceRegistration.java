package seven.lab.wstun.protocol;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Service registration data sent by clients.
 */
public class ServiceRegistration {

    @SerializedName("name")
    private String name;

    @SerializedName("type")
    private String type;

    @SerializedName("description")
    private String description;

    @SerializedName("endpoints")
    private List<Endpoint> endpoints;

    @SerializedName("static_resources")
    private Map<String, String> staticResources;  // path -> content

    public static class Endpoint {
        @SerializedName("path")
        private String path;

        @SerializedName("method")
        private String method;  // GET, POST, etc.

        @SerializedName("description")
        private String description;

        @SerializedName("relay")
        private boolean relay;  // Whether to relay requests to the client

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRelay() {
            return relay;
        }

        public void setRelay(boolean relay) {
            this.relay = relay;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public Map<String, String> getStaticResources() {
        return staticResources;
    }

    public void setStaticResources(Map<String, String> staticResources) {
        this.staticResources = staticResources;
    }
}
