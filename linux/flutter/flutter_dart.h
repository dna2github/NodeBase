#ifndef _FLUTTER_DART_
#define _FLUTTER_DART_
#include <flutter_linux/flutter_linux.h>

#include <gtk/gtk.h>
#include <string>
#include <cstring>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <limits.h>
#include <libgen.h>

#include <ifaddrs.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <mutex>
#include <sstream>
#include <map>
#include <vector>
#include <thread>
#include <sys/wait.h>

static FlEventChannel *eventHandler = nullptr;

enum NodeAppSTAT {
    BORN, READY, RUNNING, DEAD
};

class NodeAppMonitor {
public:
    NodeAppMonitor(const std::string &name, const std::vector<std::string> &cmd, const std::map<std::string, std::string> &env) {
        this->name = name;
        this->cmd = cmd;
        this->env = env;
        this->stat = NodeAppSTAT::BORN;
        this->curpid = -1;
        this->pth = std::thread(NodeAppMonitor::Run, this);
    }
    ~NodeAppMonitor() {
        this->Stop();
        if (this->pth.joinable()) this->pth.join();
    }

    void Start() {
        this->stat = NodeAppSTAT::READY;
        int argN = this->cmd.size(), envN = this->env.size();
        int status = 0;
        const char *cmd = this->cmd.at(0).c_str();
        const char *args[argN+1], *env[envN+1];
        const char **cur;

        cur = args;
        for (auto i = this->cmd.begin(); i != this->cmd.end(); i++) {
            *cur = i->c_str();
            ++ cur;
        }
        *cur = nullptr;

        int envI = 0;
        std::string envRaw[envN];
        for (auto i = this->env.begin(); i != this->env.end(); i++) {
            envRaw[envI] = std::string(i->first) + "=" + std::string(i->second);
            env[envI] = envRaw[envI].c_str();
            ++ envI;
        }
        env[envI] = nullptr;

        pid_t chpid = this->_startProcess(cmd, args, env);
        if (chpid <= 0) {
            this->stat = NodeAppSTAT::DEAD;
            printf(
                    "NodeAppMonitor [E] \"%s\" start failure on CreateProcess.",
                    this->name.c_str()
            );
            return;
        }
        this->curpid = chpid;
        this->stat = NodeAppSTAT::RUNNING;

        if (eventHandler) {
            g_autoptr(FlValue) message = fl_value_new_list();
            g_autoptr(GError) error = NULL;
            fl_value_append(message, fl_value_new_string ("start"));
            fl_value_append(message, fl_value_new_string (this->name.c_str()));
            fl_event_channel_send(eventHandler, message, NULL, &error);
        }

        waitpid(chpid, &status, 0);
        this->curpid = -1;
        this->stat = NodeAppSTAT::DEAD;

        if (eventHandler) {
            g_autoptr(FlValue) message = fl_value_new_list();
            g_autoptr(GError) error = NULL;
            fl_value_append(message, fl_value_new_string ("stop"));
            fl_value_append(message, fl_value_new_string (this->name.c_str()));
            fl_event_channel_send(eventHandler, message, NULL, &error);
        }
    }

    void Stop() {
        if (!this->IsRunning()) return;
        if (this->curpid <= 0) return;
        int status = 0;
        this->_stopProcessTree(this->curpid, SIGTERM);
        waitpid(this->curpid, &status, 0);
    }

    NodeAppMonitor* Restart() {
        this->Stop();
        return new NodeAppMonitor(this->name, this->cmd, this->env);
    }
    void toJSON(FlValue* map) {
        std::string rstat = "none";
        switch (this->stat) {
            case NodeAppSTAT::RUNNING:
                rstat = "running";
                break;
            case NodeAppSTAT::DEAD:
                rstat = "dead";
                break;
            case NodeAppSTAT::READY:
                rstat = "new";
                break;
            case NodeAppSTAT::BORN:
            default:
                break;
        }
        g_autoptr(FlValue) statval = fl_value_new_string(rstat.c_str());
        fl_value_set_string(map, "stat", statval);
    }

    bool IsRunning() { return this->stat == NodeAppSTAT::RUNNING; }
    bool IsDead() { return this->stat == NodeAppSTAT::DEAD; }

    std::string GetName() { return this->name; }
    std::vector<std::string> GetCmd() { return this->cmd; }
    std::map<std::string, std::string> GetEnv() { return this->env; }
private:
    static void Run(NodeAppMonitor* app) {
        app->Start();
    }

    pid_t _startProcess(const char *cmd, const char **args, const char **env) {
        pid_t pid = fork();
        if (pid == 0) {
            execve(cmd, (char * const *)args, (char * const *)env);
            exit(-1);
        } else if (pid > 0){
            return pid;
        } else {
            return -1;
        }
    }

    bool _stopProcessTree(pid_t pid, int signal) {
        DIR *dir;
        struct dirent *entry;
        char path[PATH_MAX];
        FILE *fp;
        pid_t child_pid;

        // Open the /proc directory
        dir = opendir("/proc");
        if (!dir) return false;

        // Iterate over each entry in /proc
        while ((entry = readdir(dir)) != NULL) {
            // Check if the entry is a directory and starts with a digit (representing a PID)
            if (entry->d_type == DT_DIR && entry->d_name[0] >= '0' && entry->d_name[0] <= '9') {
                sprintf(path, "/proc/%s/stat", entry->d_name);
                fp = fopen(path, "r");
                if (fp) {
                    // Read the process information from the stat file
                    if (fscanf(fp, "%*d %*s %*c %d", &child_pid) == 1) {
                        // Check if the process is a child of the target process
                        if (child_pid == pid) {
                            // Kill the child process and its subprocesses recursively
                            this->_stopProcessTree(atoi(entry->d_name), signal);
                        }
                    }
                    fclose(fp);
                }
            }
        }

        closedir(dir);

        // Kill the target process
        kill(pid, signal);
        return true;
    }

private:
    NodeAppSTAT stat;
    std::string name;
    std::vector<std::string> cmd;
    std::map<std::string, std::string> env;
    std::thread pth;
    pid_t curpid;
};

static std::map<std::string, NodeAppMonitor*> services;
static std::mutex service_lock;

void appStart(const std::string &name, const std::vector<std::string> &cmd, const std::map<std::string, std::string> &env) {
    std::lock_guard<std::mutex> guard(service_lock);
    auto app_ = services.find(name);
    NodeAppMonitor *app;
    if (app_ != services.end()) {
        app = app_->second;
        services.erase(name);
        delete app;
    }
    app = new NodeAppMonitor(name, cmd, env);
    // TODO: if (!app) {} // memory allocate failure
    services.insert({name, app});
}
void appStop(const std::string &name) {
    std::lock_guard<std::mutex> guard(service_lock);
    auto app_ = services.find(name);
    if (app_ == services.end()) return;
    NodeAppMonitor *app = app_->second;
    app->Stop();
}
bool appRestart(const std::string &name) {
    std::lock_guard<std::mutex> guard(service_lock);
    auto app_ = services.find(name);
    if (app_ == services.end()) return false;
    NodeAppMonitor *app = app_->second;
    app->Stop();
    NodeAppMonitor *newapp = app->Restart();
    if (newapp == nullptr) {
        return false;
    }
    delete app;
    services.insert({name, newapp});
    return true;
}
void appStat(const std::string &name, FlValue *map) {
    auto app_ = services.find(name);
    if (app_ == services.end()) return;
    NodeAppMonitor *app = app_->second;
    app->toJSON(map);
}

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

bool utilFileMarkExecutable(const std::string &filename) {
    struct stat fileStat;
    if (!stat(filename.c_str(), &fileStat)) return false;
    mode_t newPerms = fileStat.st_mode | S_IXUSR;
    if (!chmod(filename.c_str(), newPerms)) return false;
    return true;
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
    if (len <= 0) {
        buf[0] = '\0';
    } else {
        dirname(buf);
    }
    return std::string(buf);
}

bool _convertIpBinary2String(struct sockaddr *addr, char* buf, size_t buf_len) {
    void *binAddr;
    if (addr->sa_family == AF_INET) {
        binAddr = &((struct sockaddr_in *)addr)->sin_addr;
    } else if (addr->sa_family == AF_INET6) {
        binAddr = &((struct sockaddr_in6 *)addr)->sin6_addr;
    } else {
        return false;
    }
    if (!inet_ntop(addr->sa_family, binAddr, buf, buf_len)) return false;
    return true;
}
bool utilGetIps(std::map<std::string, std::vector<std::string>> &map) {
    struct ifaddrs *ifaddr, *cur;
    const char *ifa_name;
    char addrBuf[INET6_ADDRSTRLEN];
    if (getifaddrs(&ifaddr)) return false;
    for (cur = ifaddr; cur != NULL; cur = cur->ifa_next) {
        if (!cur->ifa_addr) continue;
        ifa_name = cur->ifa_name;
        if (!_convertIpBinary2String(cur->ifa_addr, addrBuf, INET6_ADDRSTRLEN)) continue;
        std::string name = std::string(ifa_name);
        std::string ipval = std::string(addrBuf);
        auto list_ = map.find(name);
        if (list_ == map.end()) {
            std::vector<std::string> newlist;
            newlist.push_back(ipval);
            map.insert({name, newlist});
        } else {
            list_->second.push_back(ipval);
        }
    }
    return true;
}

#define RETURN_BADARG_ERR(x) { fl_method_call_respond_error(method_call, "BAD_ARGS", "Invalid argument type for '" #x "'", nullptr, nullptr); return; }
void InitMethodChannel(FlView* flutter_instance) {
    const static char *channel_name = "net.seven.nodebase/app";
    g_autoptr(FlStandardMethodCodec) codec = fl_standard_method_codec_new();

    auto channel = fl_method_channel_new(
            fl_engine_get_binary_messenger(fl_view_get_engine(flutter_instance)),
            channel_name,
            FL_METHOD_CODEC(codec)
    );
    fl_method_channel_set_method_call_handler(
            channel,
            [](FlMethodChannel *channel, FlMethodCall *method_call, gpointer user_data) {
                g_autoptr(FlMethodResponse) response = nullptr;
                const gchar *method_name = fl_method_call_get_name(method_call);
                if (strcmp("app.stat", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_MAP || fl_value_get_length(args) < 1) RETURN_BADARG_ERR(app.stat);
                    FlValue *name_ = fl_value_lookup_string(args, "name");
                    if (fl_value_get_type(name_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.stat);
                    std::string name = std::string(fl_value_get_string(name_));
                    g_autoptr(FlValue) appstat = fl_value_new_map();
                    appStat(name, appstat);
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(appstat));
                } else if (strcmp("app.start", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_MAP || fl_value_get_length(args) < 3) RETURN_BADARG_ERR(app.start);
                    FlValue *name_ = fl_value_lookup_string(args, "name");
                    if (fl_value_get_type(name_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.start);
                    FlValue *cmd_ = fl_value_lookup_string(args, "cmd");
                    if (fl_value_get_type(cmd_) != FL_VALUE_TYPE_LIST) RETURN_BADARG_ERR(app.start);
                    FlValue *env_ = fl_value_lookup_string(args, "env");
                    if (fl_value_get_type(env_) != FL_VALUE_TYPE_MAP) env_ = nullptr;
                    std::string name = std::string(fl_value_get_string(name_));

                    std::vector<std::string> cmd;
                    for (size_t i = 0; i < fl_value_get_length(cmd_); i++) {
                        FlValue *cmdi = fl_value_get_list_value(cmd_, i);
                        if (fl_value_get_type(cmdi) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.start);
                        cmd.push_back(std::string(fl_value_get_string(cmdi)));
                    }

                    std::map<std::string, std::string> env;
                    for (size_t i = 0; env_ && i < fl_value_get_length(env_); i++) {
                        FlValue *envk_ = fl_value_get_map_key(env_, i);
                        if (fl_value_get_type(envk_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.start);
                        FlValue *envv_ = fl_value_get_map_value(env_, i);
                        if (fl_value_get_type(envv_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.start);
                        std::string envk = std::string(fl_value_get_string(envk_));
                        std::string envv = std::string(fl_value_get_string(envv_));
                        env.insert({envk, envv});
                    }

                    appStart(name, cmd, env);
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(nullptr));
                } else if (strcmp("app.restart", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_MAP || fl_value_get_length(args) < 1) RETURN_BADARG_ERR(app.restart);
                    FlValue *name_ = fl_value_lookup_string(args, "name");
                    if (fl_value_get_type(name_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.restart);
                    std::string name = std::string(fl_value_get_string(name_));
                    appRestart(name);
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(nullptr));
                } else if (strcmp("app.stop", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_MAP || fl_value_get_length(args) < 1) RETURN_BADARG_ERR(app.stop);
                    FlValue *name_ = fl_value_lookup_string(args, "name");
                    if (fl_value_get_type(name_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(app.stop);
                    std::string name = std::string(fl_value_get_string(name_));
                    appStop(name);
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(nullptr));
                } else if (strcmp("util.ip", method_name) == 0) {
                    std::map<std::string, std::vector<std::string>> iptblmap;
                    utilGetIps(iptblmap);
                    g_autoptr(FlValue) iptbl = fl_value_new_map();
                    for (auto i = iptblmap.begin(); i != iptblmap.end(); i++) {
                        g_autoptr(FlValue) list = fl_value_new_list();
                        for (auto j = i->second.begin(); j != i->second.end(); j++) {
                            g_autoptr(FlValue) ip = fl_value_new_string(j->c_str());
                            fl_value_append(list, ip);
                        }
                        fl_value_set_string(iptbl, i->first.c_str(), list);
                    }
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(iptbl));
                } else if (strcmp("util.file.executable", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_MAP || fl_value_get_length(args) < 1) RETURN_BADARG_ERR(util.file.executable);
                    FlValue *filename_ = fl_value_lookup_string(args, "filename");
                    if (fl_value_get_type(filename_) != FL_VALUE_TYPE_STRING) RETURN_BADARG_ERR(util.file.executable);
                    std::string filename = std::string(fl_value_get_string(filename_));
                    g_autoptr(FlValue) val = fl_value_new_bool(utilFileMarkExecutable(filename));
                    response = FL_METHOD_RESPONSE(fl_method_success_response_new(val));
                } else if (strcmp("util.browser.open", method_name) == 0) {
                    FlValue *args = fl_method_call_get_args(method_call);
                    if (fl_value_get_type(args) != FL_VALUE_TYPE_MAP || fl_value_get_length(args) < 1) RETURN_BADARG_ERR(util.browser.open);
                    FlValue *url_ = fl_value_lookup_string(args, "url");
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

void InitEventChannel(FlView* flutter_instance) {
    const static char *channel_name = "net.seven.nodebase/event";
    g_autoptr(FlStandardMethodCodec) codec = fl_standard_method_codec_new();
    eventHandler = fl_event_channel_new(
            fl_engine_get_binary_messenger(fl_view_get_engine(flutter_instance)),
            channel_name,
            FL_METHOD_CODEC(codec)
    );
}
#endif // _FLUTTER_DART_
