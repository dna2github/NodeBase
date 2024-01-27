package net.seven.nodebase.nodebase;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Network {
    public static HashMap<String, ArrayList<String>> getIPs() {
        return getIPsFromAndroidApi();
    }

    public static HashMap<String, ArrayList<String>> getIPsFromAndroidApi() {
        HashMap<String, ArrayList<String>> r = new HashMap<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String nic_name = nic.getName();
                List<InterfaceAddress> nic_addr = nic.getInterfaceAddresses();
                ArrayList<String> ips = new ArrayList<>();
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
                    r.toString()
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
