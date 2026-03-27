import java.io.*;
import java.util.*;

/*
 * Parses the configuration file and builds the virtual network topology.
 * It stores:
 *   1. devices (id -> DeviceInfo)
 *   2. links   (adjacency list)
 *   3. gateways (stored inside DeviceInfo)
 *
 * Supported sections in config.txt
 */

public class ConfigParser {
    // id -> device information

    private Map<String, DeviceInfo> devices = new HashMap<>();
    // adjacency list: id -> neighbor ids

    private Map<String, List<String>> links = new HashMap<>();

    public ConfigParser(String filename) throws Exception {
        parse(filename);
    }
    //Parse config file line by line.


    private void parse(String filename) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;

        // which section we are reading
        boolean readingDevices = false;
        boolean readingGateways = false;
        boolean readingLinks = false;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            // skip empty line or comments
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.equals("devices")) {
                readingDevices = true;
                readingGateways = false;
                readingLinks = false;
                continue;
            }

            if (line.equals("gateways")) {
                readingDevices = false;
                readingGateways = true;
                readingLinks = false;
                continue;
            }

            if (line.equals("links")) {
                readingDevices = false;
                readingGateways = false;
                readingLinks = true;
                continue;
            }

            String[] p = line.split("\\s+");

            //Parse devices
            if (readingDevices) {

                String id = p[0];
                String ip = p[1];
                int port = Integer.parseInt(p[2]);

                List<String> virtualIps = new ArrayList<>();

                // Optional virtual IPs (from index 3 onward)
                for (int i = 3; i < p.length; i++) {
                    virtualIps.add(p[i]);
                }

                devices.put(id, new DeviceInfo(id, ip, port, virtualIps));
            }

            //Parse gateways
            else if (readingGateways) {

                String hostId = p[0];
                String gatewayIp = p[1];

                DeviceInfo host = devices.get(hostId);
                if (host != null) {
                    host.setGateway(gatewayIp);
                }
            }

            // Parse links (bidirectional)
            else if (readingLinks) {

                String a = p[0];
                String b = p[1];

                links.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
                links.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            }
        }
        br.close();
    }

    //getters
    // get device info by id
    public DeviceInfo getDevice(String id) {
        return devices.get(id);
    }

    // get neighbors of a device
    public List<String> getNeighbors(String id) {
        return links.getOrDefault(id, new ArrayList<>());
    }

    // return all device ids
    public Set<String> getAllDevices() {
        return devices.keySet();
    }

    // Get gateway of a host
    public String getGateway(String id) {
        DeviceInfo d = devices.get(id);
        if (d == null) return null;
        return d.getGateway();
    }

    //Print topology for debugging.
    public void printConfig() {

        System.out.println("========== DEVICES ==========");

        for (DeviceInfo d : devices.values()) {
            System.out.println("Device: " + d.getId());
            System.out.println("  Real Address: " + d.getAddress());
            System.out.println("  Virtual IPs: " + d.getVirtualIps());
            System.out.println("  Gateway: " + d.getGateway());
            System.out.println();
        }

        System.out.println("========== LINKS ==========");

        for (String k : links.keySet()) {
            System.out.println(k + " -> " + links.get(k));
        }
    }

    public static void main(String[] args) throws Exception {
        ConfigParser cfg = new ConfigParser("src/config.txt");
        cfg.printConfig();
    }
}