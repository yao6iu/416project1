import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualRouterDV {

    private final String id;
    private final ConfigParser cfg;
    private final DeviceInfo me;
    private final DatagramSocket socket;

    // DV construct
    private final Map<String, Integer> distanceVector = new HashMap<>();
    private final Map<String, String> nextHop = new HashMap<>();
    private final Map<String, Map<String, Integer>> neighborDV = new HashMap<>();

    // neighborId -> UDP address
    private final Map<String, InetSocketAddress> neighborPorts = new HashMap<>();


    public VirtualRouterDV(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown router id: " + id);

        this.socket = new DatagramSocket(null);
        this.socket.bind(new InetSocketAddress(
                InetAddress.getByName(me.getIp()),
                me.getPort()
        ));

        // Initialize the neighbor port
        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);

            InetSocketAddress addr = new InetSocketAddress(
                    InetAddress.getByName(d.getIp()),
                    d.getPort()
            );
            neighborPorts.put(nb, addr);
        }

        initDV();
        startDVThread();

        System.out.println("[ROUTER " + id + "] started. Neighbors=" + neighborPorts.keySet());
    }

    //Initialize
    private void initDV() {
        for (String vip : me.getVirtualIps()) {
            String subnet = DeviceInfo.extractSubnet(vip);

            distanceVector.put(subnet, 0);
            nextHop.put(subnet, id);
        }

        System.out.println("[ROUTER " + id + "] Initial DV: " + distanceVector);
    }

    //Periodic sending

    private void startDVThread() {
        new Thread(() -> {
            while (true) {
                try {
                    sendDV();
                    Thread.sleep(2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendDV() {
        StringBuilder sb = new StringBuilder();

        for (String subnet : distanceVector.keySet()) {
            sb.append(subnet).append("=").append(distanceVector.get(subnet)).append(",");
        }

        String payload = sb.toString();

        for (String neighbor : neighborPorts.keySet()) {
            try {
                String frame = "DV|" + id + ":" + neighbor + ":-:-:" + payload;

                InetSocketAddress addr = neighborPorts.get(neighbor);

                byte[] data = frame.getBytes(StandardCharsets.UTF_8);
                DatagramPacket pkt = new DatagramPacket(data, data.length,
                        addr.getAddress(), addr.getPort());

                socket.send(pkt);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Accept processing
    private void handleDV(String frame) {
        String content = frame.substring(3); // remove DV|
        String[] parts = content.split(":", 5);

        String neighborId = parts[0];
        String payload = parts[4];

        Map<String, Integer> dv = new HashMap<>();

        String[] entries = payload.split(",");
        for (String e : entries) {
            if (e.isEmpty()) continue;

            String[] kv = e.split("=");
            dv.put(kv[0], Integer.parseInt(kv[1]));
        }

        neighborDV.put(neighborId, dv);

        recompute();
    }

    //Bellman-Ford

    private void recompute() {
        boolean updated = false;

        for (String neighbor : neighborDV.keySet()) {

            Map<String, Integer> dv = neighborDV.get(neighbor);

            for (String subnet : dv.keySet()) {

                int newCost = dv.get(subnet) + 1;

                if (!distanceVector.containsKey(subnet)
                        || newCost < distanceVector.get(subnet)) {

                    distanceVector.put(subnet, newCost);
                    nextHop.put(subnet, neighbor);

                    updated = true;
                }
            }
        }

        if (updated) {
            System.out.println("[ROUTER " + id + "] Updated DV: " + distanceVector);
            System.out.println("[ROUTER " + id + "] NextHop: " + nextHop);
        }
    }

    //DATA forwarding

    private void handleData(String frame, InetSocketAddress incomingAddr) throws Exception {

        String[] parts = frame.split(":", 5);

        String srcMac = parts[0];
        String dstMac = parts[1];
        String srcIp  = parts[2];
        String dstIp  = parts[3];
        String msg    = parts[4];

        // Only the frames sent to you are processed
        if (!dstMac.equals(id)) return;

        String dstSubnet = DeviceInfo.extractSubnet(dstIp);

        if (!nextHop.containsKey(dstSubnet)) {
            System.out.println("[ROUTER " + id + "] No route to " + dstSubnet);
            return;
        }

        String next = nextHop.get(dstSubnet);

        // If it's a direct subnet
        String newDstMac;
        if (distanceVector.get(dstSubnet) == 0) {
            // Directly to the target host
            newDstMac = dstIp.split("\\.")[1];
        } else {
            newDstMac = next;
        }

        // Prevent backposts
        InetSocketAddress outAddr = neighborPorts.get(next);
        if (outAddr == null) return;

        if (incomingAddr.getAddress().equals(outAddr.getAddress())
                && incomingAddr.getPort() == outAddr.getPort()) {
            return;
        }

        String newFrame = id + ":" + newDstMac + ":" + srcIp + ":" + dstIp + ":" + msg;

        System.out.println("[ROUTER " + id + "] Forwarding to " + next + ": " + newFrame);

        byte[] data = newFrame.getBytes(StandardCharsets.UTF_8);
        DatagramPacket pkt = new DatagramPacket(data, data.length,
                outAddr.getAddress(), outAddr.getPort());

        socket.send(pkt);
    }


    public void run() {
        byte[] buf = new byte[4096];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                InetSocketAddress incomingAddr =
                        new InetSocketAddress(pkt.getAddress(), pkt.getPort());

                String raw = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                // === DV包 ===
                if (raw.startsWith("DV|")) {
                    handleDV(raw);
                    continue;
                }

                // === DATA包 ===
                if (raw.startsWith("DATA|")) {
                    String frame = raw.substring(5);
                    handleData(frame, incomingAddr);
                }

            } catch (Exception e) {
                System.out.println("[ROUTER " + id + "][ERROR] " + e.getMessage());
            }
        }
    }

    //main
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualRouterDV <ID>");
            return;
        }

        String id = args[0];
        VirtualRouterDV router = new VirtualRouterDV(id, "C:\\Users\\Asus\\Desktop\\CS416\\CS416project2\\416project2\\src\\config.txt");
        router.run();
    }


}