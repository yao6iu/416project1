/*
* A simple data model that represents one network device
* In this project:
    * IP + port = physical address (UDP socket address)
*/

public class DeviceInfo {

    private String id;  //device id
    private String ip;  //Ip address
    private int port;  //UDP port

    public DeviceInfo(String id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    // ===== getters =====
    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    // ===== helper =====
    public String getAddress() {
        return ip + ":" + port;
    }

    // ===== debug print =====
    @Override
    public String toString() {
        return id + " (" + ip + ":" + port + ")";
    }
}