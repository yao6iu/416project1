import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
public class ConfigParser {
    public Map<String, DeviceInfo> devices = new HashMap<>();
    public Map<String, List<String>> links = new HashMap<>();

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

            if (line.equals("devices")) readingDevices = true;
            else if (line.equals("links")) {
                readingDevices = false;
                readingLinks = true;
            } else if (readingDevices) {
                String[] p = line.split("\\s+");
                devices.put(p[0], new DeviceInfo(p[0], p[1], Integer.parseInt(p[2])));
            } else if (readingLinks) {
                String[] p = line.split("\\s+");
                links.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                links.computeIfAbsent(p[1], k -> new ArrayList<>()).add(p[0]);
            }
        }
        br.close();
    }
}