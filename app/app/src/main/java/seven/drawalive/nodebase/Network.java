package seven.drawalive.nodebase;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Network {
   public static HashMap<String, String[]> getNicIps() {
      HashMap<String, String[]> name_ip = new HashMap<>();
      try {
         for (NetworkInterface nic :
               Collections.list(NetworkInterface.getNetworkInterfaces())) {
            List<InterfaceAddress> nic_addr = nic.getInterfaceAddresses();
            if (nic_addr.size() == 0) continue;
            String[] ips = new String[nic_addr.size()];
            String name = nic.getName();
            int index = 0;
            for (InterfaceAddress ia : nic_addr) {
               String addr = ia.getAddress().getHostAddress();
               if (addr.indexOf('%') >= 0) {
                  addr = addr.split("%")[0];
               }
               ips[index++] = addr;
            }
            name_ip.put(name, ips);
         }
      } catch (SocketException e) {
      }
      return name_ip;
   }

   public static String getWifiIpv4(Context context) {
      WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
      WifiInfo wifiInfo = wifiManager.getConnectionInfo();
      int ip = wifiInfo.getIpAddress();
      return String.format("%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
   }
}
