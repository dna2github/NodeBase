// manage platform like executable files, application files

/*
   platform_service:
   /nodebase.json {
      "version": "version",
      "platform-<os>-<arch>": "version"
   } e.g. {
      "version": "1.0.0",
      "platform-android-arm": "20240101",
      "platform-android-arm64": "20240101",
      "platform-android-x86": "20240101",
      "platform-window-x64": "20240101"
   }

   /platform-<os>-<arch>.json {
      "platforms": {
         "<name>": {
            "<version>": {
               "url": "<url>",
               "sha256": "<sha256>",
               "description": "<description>"
            }
         }
      }
   } e.g. {
   }

   /application-<os>-<arch>.json {
      "applications": {
         "<name>": {
            "<version>": {
               "url": "<url>",
               "sha256": "<sha256>",
               "description": "<description>"
            }
         }
      }
   }
 */

class Platform {
  Platform({required this.os, required this.arch});
  String os;
  String arch;
}