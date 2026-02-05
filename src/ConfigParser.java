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
    //设备id到设备信息的映射
    private Map<String, DeviceInfo> devices = new HashMap<>();
    // adjacency list: id -> neighbor ids
    //邻接表：记录每个设备链接了哪些邻居
    private Map<String, List<String>> links = new HashMap<>();

    public ConfigParser(String filename) throws Exception {
        parse(filename);
    }
    //Parse config file line by line.
    //逐行读取配置文件并解析

    private void parse(String filename) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        // which section we are reading
        // 当前正在读取哪个区域（devices or links）
        boolean readingDevices = false;
        boolean readingLinks = false;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            // skip empty line or comments
            // 跳过空行或注释
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
            //保存设备信息
            if (readingDevices){
                devices.put(p[0],
                        new DeviceInfo(p[0],p[1], Integer.parseInt(p[2])));
            }
            // store bidirectional link
            // 保存双向链路（无向图）
            else if(readingLinks){
                links.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                links.computeIfAbsent(p[1], k -> new ArrayList<>()).add(p[0]);
            }
        }
        br.close();
    }
    //getters
    // get device info by id
    // 根据ID获取设备信息
    public DeviceInfo getDevice(String id) {
        return devices.get(id);
    }

    // get neighbors of a device
    // 获取某个设备的所有邻居
    public List<String> getNeighbors(String id) {
        return links.getOrDefault(id, new ArrayList<>());
    }

    // return all device ids
    // 获取所有设备ID
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