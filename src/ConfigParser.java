import java.io.*;
import java.util.*;

/*
 *Parse the configuration file and build the virtual network topology.
 * It stores:
 *   1. devices (id -> ip/port)
 *   2. links   (who connects to whom)
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
        boolean readingLinks = false;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            // skip empty line or comments
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.equals("devices")) {
                readingDevices = true;
                readingLinks = false;
                continue;
            }

            if (line.equals("links")) {
                readingDevices = false;
                readingLinks = true;
                continue;
            }
            String[] p = line.split("\\s+");
            //store device info

            if (readingDevices){
                devices.put(p[0],
                        new DeviceInfo(p[0],p[1], Integer.parseInt(p[2])));
            }
            // store bidirectional link

            else if(readingLinks){
                links.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                links.computeIfAbsent(p[1], k -> new ArrayList<>()).add(p[0]);
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

    //Print topology for debugging.
    public void printConfig() {

        System.out.println("---Devices---");
        for (DeviceInfo d : devices.values()) {
            System.out.println(d);
        }

        System.out.println("---Links---");
        for (String k : links.keySet()) {
            System.out.println(k + " -> " + links.get(k));
        }
    }
}