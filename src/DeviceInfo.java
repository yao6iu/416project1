public class DeviceInfo {

    private String id;
    private String ip;
    private int port;

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