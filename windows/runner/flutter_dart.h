#ifndef _FLUTTER_DART_
#define _FLUTTER_DART_
#include <flutter/binary_messenger.h>
#include <flutter/standard_method_codec.h>
#include <flutter/method_channel.h>
#include <flutter/method_result_functions.h>
#include <flutter/event_channel.h>
#include <flutter/event_stream_handler_functions.h>

#include <string>
#include <mutex>
#include <sstream>
#include <map>
#include <vector>
#include <thread>
#include <numeric>
#include <algorithm>
#include "utils.h"

class NodeAppMonitor;
class NodeBaseEventChannelHandler;
void utilEventPostMessage(std::string&& name, flutter::EncodableValue&& val);

enum NodeAppSTAT {
    BORN, READY, RUNNING, DEAD
};

class NodeAppMonitor {
public:
    NodeAppMonitor(const std::string &name, const std::string &cmd) {
        this->name = name;
        this->cmd = cmd;
        this->stat = NodeAppSTAT::BORN;
        ZeroMemory(&this->pi, sizeof(this->pi));
        this->pth = std::thread(NodeAppMonitor::Run, this);
    }
    ~NodeAppMonitor() {
        this->Stop();
        if (this->pth.joinable()) this->pth.join();
    }

    void Start() {
        this->stat = NodeAppSTAT::READY;
        STARTUPINFO si;
        ZeroMemory(&si, sizeof(si));
        si.cb = sizeof(si);
        ZeroMemory(&this->pi, sizeof(this->pi));
        std::vector<std::string> cmdL;
        this->SplitCmd(cmdL, this->cmd);
        std::wstring prog = Utf8ToUtf16(cmdL.at(0).c_str());
        cmdL.erase(cmdL.begin());
        std::wstring cmdLine = Utf8ToUtf16(
                std::accumulate(
                        cmdL.begin(), cmdL.end(), std::string(" ")
                ).c_str()
        );
        // ref: https://forums.codeguru.com/showthread.php?514716-std-string-to-LPSTR
        LPWSTR cmdLineAdapter = &cmdLine.front();
        if (!CreateProcess(
                prog.c_str(),
                cmdLineAdapter,
                nullptr,
                nullptr,
                FALSE,
                0,
                nullptr,
                nullptr,
                &si,
                &pi)
        ) {
            this->stat = NodeAppSTAT::DEAD;
            printf(
                "NodeAppMonitor [E] \"%s\" start failure on CreateProcess.",
                this->name.c_str()
            );
            return;
        }
        this->stat = NodeAppSTAT::RUNNING;

        {
            flutter::EncodableList r;
            r.push_back(flutter::EncodableValue("start"));
            r.push_back(flutter::EncodableValue(name));
            utilEventPostMessage("app", flutter::EncodableValue(r));
        }

        WaitForSingleObject( this->pi.hProcess, INFINITE );
        this->stat = NodeAppSTAT::DEAD;
        CloseHandle( this->pi.hProcess );
        CloseHandle( this->pi.hThread );

        {
            flutter::EncodableList r;
            r.push_back(flutter::EncodableValue("stop"));
            r.push_back(flutter::EncodableValue(name));
            utilEventPostMessage("app", flutter::EncodableValue(r));
        }
    }

    void Stop() {
        if (!this->IsRunning()) return;
        if (!this->pi.hProcess) return;
        TerminateProcess(this->pi.hProcess, 0);
        this->stat = NodeAppSTAT::DEAD;
        const DWORD r = WaitForSingleObject(this->pi.hProcess, 500);
        if (r == WAIT_OBJECT_0) {
            // TODO: ok
        } else {
            printf(
                "NodeAppMonitor [E] \"%s\" stop failure on TerminateProcess.",
                this->name.c_str()
            );
            // TODO: failured
        }
        CloseHandle(this->pi.hProcess);
        CloseHandle(this->pi.hThread);
    }

    NodeAppMonitor* restart() {
        this->Stop();
        return new NodeAppMonitor(this->name, this->cmd);
    }
    flutter::EncodableValue toJSON() {
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
        flutter::EncodableMap r = {
                {flutter::EncodableValue("state"), flutter::EncodableValue(rstat)},
        };
        return flutter::EncodableValue(r);
    }

    bool IsRunning() { return this->stat == NodeAppSTAT::RUNNING; }
    bool IsDead() { return this->stat == NodeAppSTAT::DEAD; }

    std::string GetName() { return this->name; }
    std::string GetCmd() { return this->cmd; }
private:
    int SplitCmd(std::vector<std::string> &vec, const std::string &cmdx0) {
        int count = 0;
        std::stringstream spliter(cmdx0);
        std::string item;
        vec.clear();
        while (std::getline(spliter, item, '\x01')) {
            vec.push_back(item);
            count++;
        }
        return count;
    }

    static void Run(NodeAppMonitor* app) {
        app->Start();
    }

private:
    NodeAppSTAT stat;
    std::string name;
    std::string cmd;
    std::thread pth;
    PROCESS_INFORMATION pi;
};

// ref: https://stackoverflow.com/questions/24764477/network-adapter-information-in-c
// ref: https://learn.microsoft.com/zh-cn/windows/win32/api/iphlpapi/nf-iphlpapi-getadaptersaddresses?redirectedfrom=MSDN
static int getIPAdresses(flutter::EncodableMap& out) {
    int count = 0;

    char buf[32*1024];
    ULONG pAddresses_len = sizeof(buf);
    IP_ADAPTER_ADDRESSES * pAddresses = (IP_ADAPTER_ADDRESSES *)buf;
    DWORD dwRetVal = GetAdaptersAddresses(
            AF_UNSPEC,
            GAA_FLAG_INCLUDE_PREFIX,
            nullptr,
            pAddresses,
            &pAddresses_len
    );
    if (dwRetVal == NO_ERROR) {
        IP_ADAPTER_ADDRESSES *pCurrAddresses = pAddresses;
        out.clear();
        while (pCurrAddresses) {
            int n = (int)(pCurrAddresses->PhysicalAddressLength);
            if (n > 0) {
                // pCurrAddresses->AdapterName is in a format like {xxxx-yyyy-zzzz...}
                flutter::EncodableValue name(Utf8FromUtf16(pCurrAddresses->FriendlyName));
                flutter::EncodableList arr;
                for (IP_ADAPTER_UNICAST_ADDRESS* pUnicast = pCurrAddresses->FirstUnicastAddress; pUnicast != NULL; pUnicast = pUnicast->Next) {
                    wchar_t ipAddress[INET6_ADDRSTRLEN] = {0};
                    void* pAddr = nullptr;

                    // Determine the family of the IP address
                    if (pUnicast->Address.lpSockaddr->sa_family == AF_INET) {
                        // It's an IPv4 address
                        pAddr = &((struct sockaddr_in*)pUnicast->Address.lpSockaddr)->sin_addr;
                    } else if (pUnicast->Address.lpSockaddr->sa_family == AF_INET6) {
                        // It's an IPv6 address
                        pAddr = &((struct sockaddr_in6*)pUnicast->Address.lpSockaddr)->sin6_addr;
                    }

                    // Convert the binary IP address to a string
                    if (pAddr) {
                        InetNtop(pUnicast->Address.lpSockaddr->sa_family, pAddr, ipAddress, sizeof(ipAddress));
                        arr.push_back(flutter::EncodableValue(Utf8FromUtf16(ipAddress)));
                    }
                }
                out.insert_or_assign(name, arr);
            }
            pCurrAddresses = pCurrAddresses->Next;
            count++;
        }
    } else {
        // TODO: handle error
        count = -1;
    }
    return count;
}

class NodeBaseEventChannelHandler {
public:
    NodeBaseEventChannelHandler(std::string&& channel_name, flutter::FlutterEngine* flutter_instance) {
        auto event_channel =
                std::make_unique<flutter::EventChannel<>>(
                        flutter_instance->messenger(), channel_name,
                        &flutter::StandardMethodCodec::GetInstance());

        auto event_channel_handler = std::make_unique<
                                     flutter::StreamHandlerFunctions<flutter::EncodableValue>>(
                [this](
                        const flutter::EncodableValue* arguments,
                        std::unique_ptr<flutter::EventSink<flutter::EncodableValue>>&& events
                ) -> std::unique_ptr<flutter::StreamHandlerError<flutter::EncodableValue>> {
                    std::string name = EncodableValue2String(arguments);
                    this->sink.insert_or_assign(name, std::move(events));
                    return nullptr;
                },
                [this](const flutter::EncodableValue* arguments)
                -> std::unique_ptr<flutter::StreamHandlerError<flutter::EncodableValue>> {
                    // TODO: replace as find and erase
                    std::string name = EncodableValue2String(arguments);
                    this->sink.insert_or_assign(name, nullptr);
                    return nullptr;
                });
        event_channel->SetStreamHandler(std::move(event_channel_handler));
    }

    void postMessage(std::string&& name, flutter::EncodableValue message) {
        auto target_ = this->sink.find(name);
        if (target_ == this->sink.end()) return;
        if (!target_->second) return;
        //auto target = target_->second;
        target_->second->Success(message);
    }
private:
    std::string EncodableValue2String(const flutter::EncodableValue* val) {
        if (val->IsNull()) {
            return std::string("");
        }

        if (std::holds_alternative<std::string>(*val)) {
            return std::get<std::string>(*val);
        }

        return std::string("");
    }
private:
    std::map<std::string, std::unique_ptr<flutter::EventSink<flutter::EncodableValue>>> sink;
};

static std::map<std::string, NodeAppMonitor*> services;
static std::mutex service_lock;
static std::unique_ptr<NodeBaseEventChannelHandler> eventHandler;

void utilEventPostMessage(std::string&& name, flutter::EncodableValue&& val) {
    if (eventHandler == nullptr) return;
    eventHandler->postMessage(std::move(name), val);
}

void appStart(const std::string &name, const std::string &cmd) {
    std::lock_guard<std::mutex> guard(service_lock);
    auto app_ = services.find(name);
    NodeAppMonitor *app;
    if (app_ != services.end()) {
        app = app_->second;
        services.erase(name);
        delete app;
    }
    app = new NodeAppMonitor(name, cmd);
    // TODO: if (!app) {} // memory allocate failure
    services.insert_or_assign(name, app);
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
    NodeAppMonitor *newapp = app->restart();
    if (newapp == nullptr) {
        return false;
    }
    delete app;
    services.insert_or_assign(name, newapp);
    return true;
}
flutter::EncodableValue appStat(const std::string &name) {
    auto app_ = services.find(name);
    if (app_ == services.end()) return flutter::EncodableValue(flutter::EncodableMap());
    NodeAppMonitor *app = app_->second;
    return app->toJSON();
}
flutter::EncodableValue utilGetIPs() {
    flutter::EncodableMap ips;
    getIPAdresses(ips);
    return flutter::EncodableValue(ips);
}
void utilBrowserOpen(const std::string &url) {
    if (url.rfind("http:", 0) != 0 && url.rfind("https:", 0) != 0) return;
    ShellExecuteA(0, 0, url.c_str(), 0, 0 , SW_SHOW );
}
flutter::EncodableValue utilGetArch() {
    std::string arch("windows-");
    SYSTEM_INFO si;
    // XXX: GetSystemInfo(&si) for old windows, maybe we can use LoadLibrary for test
    GetNativeSystemInfo(&si);
    switch(si.wProcessorArchitecture) {
        // compatible with GetSystemInfo
        case PROCESSOR_ARCHITECTURE_AMD64:
            arch += "x64|x86";
            break;
        case PROCESSOR_ARCHITECTURE_INTEL:
            arch += "x86";
            break;
        // new
        case PROCESSOR_ARCHITECTURE_ARM:
            arch += "arm";
            break;
        case PROCESSOR_ARCHITECTURE_ARM64:
            arch += "arm64|arm";
            break;
        case PROCESSOR_ARCHITECTURE_IA64:
            arch += "x64|x86";
            break;
    }
    return flutter::EncodableValue(arch);
}
flutter::EncodableValue utilWorkspaceBaseDir() {
    TCHAR buf[1024];
    if (!GetModuleFileName(NULL, buf, 1024)) return flutter::EncodableValue(std::string(""));
    buf[1023] = 0;
    TCHAR* last = wcsrchr(buf, L'\\');
    if (last == nullptr || last == buf || last == &(buf[1]) || last == &(buf[2])) {
        // not found, buf = "\\", buf = "\\\\", buf = "c:\\"
        return flutter::EncodableValue(std::string(""));
    }
    last[0] = 0;
    return flutter::EncodableValue(Utf8FromUtf16(buf));
}

#define RETURN_BADARG_ERR(x) { result->Error("BAD_ARGS", "Invalid argument type for '" #x "'"); return; }
void InitMethodChannel(flutter::FlutterEngine* flutter_instance) {
    // name your channel
    const static std::string channel_name("net.seven.nodebase/app");

    auto channel =
            std::make_unique<flutter::MethodChannel<>>(
                    flutter_instance->messenger(), channel_name,
                    &flutter::StandardMethodCodec::GetInstance());

    channel->SetMethodCallHandler(
            [](const flutter::MethodCall<flutter::EncodableValue>& call,
               std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result) {

                const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
                if (call.method_name().compare("app.stat") == 0) {
                    if (args == nullptr) RETURN_BADARG_ERR(app.stat);
                    auto name_ = args->find(flutter::EncodableValue("name"));
                    if (name_ == args->end()) RETURN_BADARG_ERR(app.stat);
                    std::string name = std::get<std::string>(name_->second);
                    result->Success(appStat(name));
                }
                else if (call.method_name().compare("app.start") == 0) {
                    if (args == nullptr) RETURN_BADARG_ERR(app.start);
                    auto name_ = args->find(flutter::EncodableValue("name"));
                    if (name_ == args->end()) RETURN_BADARG_ERR(app.start);
                    auto cmd_ = args->find(flutter::EncodableValue("cmd"));
                    if (cmd_ == args->end()) RETURN_BADARG_ERR(app.start);
                    std::string name = std::get<std::string>(name_->second);
                    std::string cmd = std::get<std::string>(cmd_->second);
                    appStart(name, cmd);
                    result->Success();
                }
                else if (call.method_name().compare("app.restart") == 0) {
                    if (args == nullptr) RETURN_BADARG_ERR(app.restart);
                    auto name_ = args->find(flutter::EncodableValue("name"));
                    if (name_ == args->end()) RETURN_BADARG_ERR(app.restart);
                    std::string name = std::get<std::string>(name_->second);
                    if (!appRestart(name)) {
                        std::ostringstream msg;
                        msg << "Restart failed for app \"" << name << "\"";
                        result->Error("FAILURE", msg.str());
                        return;
                    }
                    result->Success();
                }
                else if (call.method_name().compare("app.stop") == 0) {
                    if (args == nullptr) RETURN_BADARG_ERR(app.stop);
                    auto name_ = args->find(flutter::EncodableValue("name"));
                    if (name_ == args->end()) RETURN_BADARG_ERR(app.stop);
                    std::string name = std::get<std::string>(name_->second);
                    appStop(name);
                    result->Success();
                }
                else if (call.method_name().compare("util.ip") == 0) {
                    result->Success(utilGetIPs());
                }
                else if (call.method_name().compare("util.file.executable") == 0) {
                    // in windows, we may not need to implement this;
                    // PE file can be executable directly without additional flag by default
                    /*
                     if (args == nullptr) RETURN_BADARG_ERR(util.file.executable);
                     auto name = args->find(flutter::EncodableValue("filename"))->second;
                     if (name.IsNull()) RETURN_BADARG_ERR(util.file.executable);
                     ...
                     */
                    result->Success();
                }
                else if (call.method_name().compare("util.browser.open") == 0) {
                    if (args == nullptr) RETURN_BADARG_ERR(util.browser.open);
                    auto url_ = args->find(flutter::EncodableValue("url"));
                    if (url_ == args->end()) RETURN_BADARG_ERR(util.browser.open);
                    std::string url = std::get<std::string>(url_->second);
                    utilBrowserOpen(url);
                    result->Success();
                }
                else if (call.method_name().compare("util.arch") == 0) {
                    result->Success(utilGetArch());
                }
                else if (call.method_name().compare("util.workspace") == 0) {
                    result->Success(utilWorkspaceBaseDir());
                }
                else {
                    result->NotImplemented();
                }
            });
}
#undef RETURN_BADARG_ERR

void InitEventChannel(flutter::FlutterEngine* flutter_instance) {
    eventHandler = std::make_unique<NodeBaseEventChannelHandler>(
            std::string("net.seven.nodebase/event"), flutter_instance);
}
#endif // _FLUTTER_DART_