package net.seven.nodebase.nodebase;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

public class Network {
    public static JSONobject getIPs() {
        return getIPsFromAndroidApi();
    }

    public static JSONobject getIPsFromAndroidApi() {
        JSONobject r = new JSONobject();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String nic_name = nic.getName();
                List<InterfaceAddress> nic_addr = nic.getInterfaceAddresses();
                JSONarray ips = new JSONarray();
                for (InterfaceAddress ia : nic_addr) {
                    String addr = ia.getAddress().getHostAddress();
                    // skip the address <ip>%<name>
                    if (addr == null || addr.indexOf('%') >= 0) continue;
                    ips.add(addr);
                }
                if (ips.size() > 0) r.put(nic_name, ips);
            }
            Logger.d(
                    "NodeBase",
                    "getIPsFromAndroidApi",
                    r.toJSONstring()
            );
        } catch (SocketException e) {
            Logger.e(
                    "NodeBase",
                    "getIPsFromAndroidApi",
                    e.toString()
            );
        }
        return r;
    }

}
