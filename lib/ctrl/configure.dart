bool localMode = true;

/*
 * /nodebase.json
 * /plm-<os>-<arch>.json
 * /plm/<os>/<arch>/<hash>(<name>-<version>).json
 * /app-<os>-<arch>.json
 * /app/<os>/<arch>/<hash>(<name>-<version>).json
 */
String defaultPlatformBaseUrl = "https://raw.githubusercontent.com/wiki/dna2github/NodeBase/market/v1";
//String defaultPlatformBaseUrl = "http://127.0.0.1:8000";

/*
 * /app/running     GET
 * /app/start       POST
 * /app/stop        POST
 * /app/stat        GET
 * /config/nodebase GET
 * /config/list/plm GET
 * /config/list/installed_plm GET
 * /config/install/plm/<hash> POST
 * /config/meta/plm/<hash>    GET
 * /config/list/app GET
 * /config/list/installed_app GET
 * /config/install/app/<hash> POST
 * /config/meta/app/<hash>    GET
 */
String defaultRemoteBaseUrl = "http://127.0.0.1:8580";