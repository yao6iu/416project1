import java.util.ArrayList;
import java.util.List;

/*
* Data model representing one network device in Project 2.
* It stores:
 *   Device ID (also used as virtual MAC address)
 *   Real IP address (for UDP socket)
 *   Real UDP port
 *   Virtual IP address(es)
 *   Gateway (only for hosts)
* Notes:
 *   Switch: no virtual IP, no gateway
 *   Host: one virtual IP + one gateway
 *   Router: multiple virtual IPs, no gateway
*/


public class DeviceInfo {

    private String id;  //device id
    private String ip;  //Real Ip address
    private int port;  //Real UDP port
    private List<String> virtualIps = new ArrayList<>(); // Virtual IP(s)
    private String gateway; // Gateway virtual IP (hosts only)

    public DeviceInfo(String id, String ip, int port, List<String> virtualIps) {
        this.id = id;
        this.ip = ip;
        this.port = port;

        if (virtualIps == null) {
            this.virtualIps = new ArrayList<>();
        } else {
            this.virtualIps = virtualIps;
        }

        this.gateway = null;   // default
    }

    //getters
    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public List<String> getVirtualIps() {
        return virtualIps;
    }

    public String getGateway() {
        return gateway;
    }

    //setter
    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public void addVirtualIp(String virtualIp) {
        this.virtualIps.add(virtualIp);
    }

    //helper
    public String getAddress() {
        return ip + ":" + port;
    }

    // Get subnet prefix from a virtual IP (e.g., net1 from net1.A)
    public static String extractSubnet(String virtualIp) {
        if (virtualIp == null) return null;
        int dot = virtualIp.indexOf(".");
        if (dot < 0) return null;
        return virtualIp.substring(0, dot);
    }

    //debug print
    @Override
    public String toString() {
        return "DeviceInfo{" +
                "id='" + id + '\'' +
                ", real=" + ip + ":" + port +
                ", virtualIps=" + virtualIps +
                ", gateway=" + gateway +
                '}';
    }
}