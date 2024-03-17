#ifndef _FLUTTER_DART_
#define _FLUTTER_DART_
#include <flutter_linux/flutter_linux.h>

#include <gtk/gtk.h>
#include <string>
#include <cstring>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <limits.h>
#include <libgen.h>

void utilBrowserOpen(const std::string &url) {
    if (url.rfind("http:", 0) != 0 && url.rfind("https:", 0) != 0) return;
    pid_t pid = fork();
    if (pid == -1) {
        fprintf(stderr, "cannot open url (fork failed)\n");
        return;
    } else if (pid == 0) {
        execlp("xdg-open", "xdg-open", url.c_str(), (char*)nullptr);
        fprintf(stderr, "cannot open url (execlp failed)\n");
        exit(-1);
    }
}
std::string utilGetArch() {
    std::string arch("linux-");
    struct utsname uts;
    if (uname(&uts) < 0) {
        arch += "unknown";
    } else if (strcmp("x86_64", uts.machine) == 0 || strcmp("ia64", uts.machine) == 0) {
        arch += "x64";
    } else if (strcmp("x86", uts.machine) == 0 || strcmp("i386", uts.machine) == 0 || strcmp("i686", uts.machine) == 0) {
        arch += "x86";
    } else if (strcmp("aarch64", uts.machine) == 0 || strcmp("arm64", uts.machine) == 0 || strcmp("armv8l", uts.machine) == 0) {
        arch += "arm64";
    } else if (strcmp("arm", uts.machine) == 0 || strcmp("armv7l", uts.machine) == 0) {
        arch += "arm";
    } else {
        arch += "unknown";
    }
    return arch;
}
std::string utilWorkspaceBaseDir() {
    char buf[PATH_MAX];
    size_t len = readlink("/proc/self/exe", buf, sizeof(buf));
    if (len < 0) {
        buf[0] = '\0';
    } else {
        dirname(buf);
    }
    return std::string(buf);
}

#define RETURN_BADARG_ERR(x) { fl_method_call_respond_error(method_call, "BAD_ARGS", "Invalid argument type for '" #x "'", nullptr, nullptr); return; }
void InitMethodChannel(FlView* flutter_instance) {
    const static char *channel_name = "net.seven.nodebase/app";

    auto channel = fl_method_channel_new(
            fl_engine_get_binary_messenger(fl_view_get_engine(flutter_instance)),
            channel_name,
            FL_METHOD_CODEC(fl_standard_method_codec_new())
    );
    fl_method_channel_set_method_call_handler(
            channel,
            [](FlMethodChannel *channel, FlMethodCall *method_call, gpointer user_data) {
                g_autoptr(FlMethodResponse) response = nullptr;
                const gchar *method_name = fl_method_call_get_name(method_call);
                if (strcmp("util.browser.open", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_LIST || fl_value_get_length(args) < 1) RETURN_BADARG_ERR(util.browser.open);
                    FlValue *url_ = fl_value_get_list_value(args, 0);
                    if (fl_value_get_type(url_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(util.browser.open);
                    std::string url = std::string(fl_value_get_string(url_));
                    utilBrowserOpen(url);
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(nullptr));
                } else if (strcmp("util.arch", method_name) == 0) {
                    g_autoptr(FlValue) val = fl_value_new_string(utilGetArch().c_str());
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(val));
                } else if (strcmp("util.workspace", method_name) == 0) {
                    g_autoptr(FlValue) val = fl_value_new_string(utilWorkspaceBaseDir().c_str());
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(val));
                } else {
                    response = FL_METHOD_RESPONSE(fl_method_not_implemented_response_new());
                }
                fl_method_call_respond(method_call, response, nullptr);
            },
            g_strdup("custom_channel"), g_free
    );
}
#undef RETURN_BADARG_ERR

#endif // _FLUTTER_DART_
