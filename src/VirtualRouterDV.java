import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualRouterDV {

    private final String id;
    private final ConfigParser cfg;
    private final DeviceInfo me;
    private final DatagramSocket socket;

    // Distance Vector: subnet -> cost
    private final Map<String, Integer> distanceVector = new HashMap<>();

    // Next hop: subnet -> neighbor router
    private final Map<String, String> nextHop = new HashMap<>();

    private final Map<String, String> directOutNeighbor = new HashMap<>();

    // Neighbor DV storage: neighbor -> its DV
    private final Map<String, Map<String, Integer>> neighborDV = new HashMap<>();

    // Neighbor UDP ports
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

        // Initialize neighbor ports
        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);

            InetSocketAddress addr = new InetSocketAddress(
                    InetAddress.getByName(d.getIp()),
                    d.getPort()
            );
            neighborPorts.put(nb, addr);
        }

        initDV();
        initDirectOutNeighbor();
        startDVThread();

        System.out.println("[ROUTER " + id + "] started. Neighbors=" + neighborPorts.keySet());
    }

    private void initDirectOutNeighbor() {

        for (String subnet : distanceVector.keySet()) {
            for (String nb : cfg.getNeighbors(id)) {
                DeviceInfo d = cfg.getDevice(nb);
                if (d == null) continue;

                for (String vip : d.getVirtualIps()) {
                    String nbSubnet = DeviceInfo.extractSubnet(vip);
                    if (subnet.equals(nbSubnet)) {
                        directOutNeighbor.put(subnet, nb);
                    }
                }
            }
        }

        for (String nb : cfg.getNeighbors(id)) {
            if (nb.startsWith("S")) {
                for (String vip : me.getVirtualIps()) {
                    String subnet = DeviceInfo.extractSubnet(vip);
                    if (subnet.equals("net1") || subnet.equals("net2") || subnet.equals("net3")) {
                        directOutNeighbor.put(subnet, nb);
                    }
                }
            }
        }

        System.out.println("[ROUTER " + id + "] directOutNeighbor = " + directOutNeighbor);
    }

    // Initialize DV with directly connected subnets
    private void initDV() {
        for (String vip : me.getVirtualIps()) {
            String subnet = DeviceInfo.extractSubnet(vip);

            distanceVector.put(subnet, 0);
            nextHop.put(subnet, id);
        }

        System.out.println("[ROUTER " + id + "] Initial DV: " + distanceVector);
    }

    // Periodically send DV to neighbors
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

    // Send DV packets to all neighbors
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

                System.out.println("[ROUTER " + id + "] Sent DV to " + neighbor + ": " + payload);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Handle received DV packet
    private void handleDV(String raw) {
        String content = raw.substring(3); // remove "DV|"
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

        System.out.println("[ROUTER " + id + "] Received DV from " + neighborId + ": " + dv);

        recompute();
    }

    // Bellman-Ford update
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

    // Handle DATA packet forwarding
    private void handleData(String raw, InetSocketAddress incomingAddr) throws Exception {

        String frame = raw.substring(5); // remove "DATA|"
        String[] parts = frame.split(":", 5);

        String srcMac = parts[0];
        String dstMac = parts[1];
        String srcIp  = parts[2];
        String dstIp  = parts[3];
        String msg    = parts[4];

        // Only process packets addressed to this router
        if (!dstMac.equals(id)) return;

        String dstSubnet = DeviceInfo.extractSubnet(dstIp);

        if (!nextHop.containsKey(dstSubnet)) {
            System.out.println("[ROUTER " + id + "] No route to " + dstSubnet);
            return;
        }

        String next = nextHop.get(dstSubnet);

        String outNeighborId;
        String newDstMac;

        if (distanceVector.get(dstSubnet) == 0) {
            // direct subnet
            newDstMac = dstIp.split("\\.")[1];
            outNeighborId = directOutNeighbor.get(dstSubnet);
        } else {
            // remote subnet
            newDstMac = next;
            outNeighborId = next;
        }

        if (outNeighborId == null) {
            System.out.println("[ROUTER " + id + "] No outgoing neighbor for " + dstSubnet);
            return;
        }

        InetSocketAddress outAddr = neighborPorts.get(outNeighborId);
        if (outAddr == null) {
            System.out.println("[ROUTER " + id + "] No UDP port for neighbor " + outNeighborId);
            return;
        }

        // Prevent sending back to incoming port
        if (incomingAddr.getAddress().equals(outAddr.getAddress())
                && incomingAddr.getPort() == outAddr.getPort()) {
            return;
        }

        String newFrame = "DATA|" + id + ":" + newDstMac + ":" + srcIp + ":" + dstIp + ":" + msg;

        System.out.println("[ROUTER " + id + "] Forwarding to " + outNeighborId + ": " + newFrame);

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

                if (raw.startsWith("DV|")) {
                    handleDV(raw);
                } else if (raw.startsWith("DATA|")) {
                    handleData(raw, incomingAddr);
                }

            } catch (Exception e) {
                System.out.println("[ROUTER " + id + "][ERROR] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualRouterDV <ID>");
            return;
        }

        String id = args[0];
        VirtualRouterDV router = new VirtualRouterDV(id, "E:\\study\\cs416\\project\\project3\\CS416project3\\416project2\\src\\config.txt");
        router.run();
    }
}