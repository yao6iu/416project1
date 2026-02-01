import java.io.*;
import java.util.*;

public class ConfigParser {

    private Map<String, DeviceInfo> devices = new HashMap<>();
    private Map<String, List<String>> links = new HashMap<>();

    public ConfigParser(String filename) throws Exception {
        parse(filename);
    }

    private void parse(String filename) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        boolean readingDevices = false;
        boolean readingLinks = false;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.equals("links")) {
                readingDevices = false;
                readingLinks = true;
                continue;
            }
            String[] p = line.split("\\s+");

            if (readingDevices){
                devices.put(p[0],
                        new DeviceInfo(p[0],p[1], Integer.parseInt(p[2])));
            }
            else if(readingLinks){
                links.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                links.computeIfAbsent(p[1], k -> new ArrayList<>()).add(p[0]);
            }
        }
        br.close();
    }
    //getters
    public DeviceInfo getDevice(String id) {
        return devices.get(id);
    }

    public List<String> getNeighbors(String id) {
        return links.getOrDefault(id, new ArrayList<>());
    }

    public Set<String> getAllDevices() {
        return devices.keySet();
    }

    //print debug
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