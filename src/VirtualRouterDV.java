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

    // Next hop: subnet -> next-hop router ID (or self if directly connected)
    private final Map<String, String> nextHop = new HashMap<>();

    // For directly connected subnet, which neighbor should the router send to
    // Example: R1 -> net1 goes out to S1
    private final Map<String, String> directOutNeighbor = new HashMap<>();

    // Neighbor DV cache: neighbor router -> its DV
    private final Map<String, Map<String, Integer>> neighborDV = new HashMap<>();

    // Neighbor UDP ports: neighbor ID -> socket address
    private final Map<String, InetSocketAddress> neighborPorts = new HashMap<>();

    public VirtualRouterDV(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) {
            throw new Exception("Unknown router id: " + id);
        }

        this.socket = new DatagramSocket(me.getPort());

        // Build neighbor address map
        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);
            if (d == null) continue;

            InetSocketAddress addr = new InetSocketAddress(
                    InetAddress.getByName(d.getIp()),
                    d.getPort()
            );
            neighborPorts.put(nb, addr);
        }

        initDV();
        initDirectOutNeighbor();
        startDVThread();

        System.out.println("[ROUTER " + id + "] started. Neighbors = " + neighborPorts.keySet());
        System.out.println("[ROUTER " + id + "] Initial DV = " + distanceVector);
        System.out.println("[ROUTER " + id + "] Initial nextHop = " + nextHop);
        System.out.println("[ROUTER " + id + "] directOutNeighbor = " + directOutNeighbor);
    }

    // Initialize DV with all directly connected subnets
    private void initDV() {
        for (String vip : me.getVirtualIps()) {
            String subnet = DeviceInfo.extractSubnet(vip);
            distanceVector.put(subnet, 0);
            nextHop.put(subnet, id);
        }
    }

    // For each directly connected subnet, determine which immediate neighbor leads to it
    private void initDirectOutNeighbor() {
        for (String myVip : me.getVirtualIps()) {
            String subnet = DeviceInfo.extractSubnet(myVip);

            if (!(subnet.equals("net1") || subnet.equals("net2") || subnet.equals("net3"))) {
                continue;
            }

            for (String nb : cfg.getNeighbors(id)) {
                if (!nb.startsWith("R")) {
                    directOutNeighbor.put(subnet, nb);
                    break;
                }
            }
        }
    }

    // Periodically send DV only to router neighbors
    private void startDVThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    sendDV();
                    Thread.sleep(2000);
                } catch (Exception e) {
                    System.out.println("[ROUTER " + id + "][DV-THREAD-ERROR] " + e.getMessage());
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void sendDV() {
        StringBuilder sb = new StringBuilder();
        List<String> subnets = new ArrayList<>(distanceVector.keySet());
        Collections.sort(subnets);

        for (String subnet : subnets) {
            sb.append(subnet)
                    .append("=")
                    .append(distanceVector.get(subnet))
                    .append(",");
        }

        String payload = sb.toString();

        for (String neighbor : neighborPorts.keySet()) {
            // Only send DV to routers
            if (!neighbor.startsWith("R")) continue;

            try {
                String frame = "DV|" + id + "|" + payload;

                InetSocketAddress addr = neighborPorts.get(neighbor);
                byte[] data = frame.getBytes(StandardCharsets.UTF_8);
                DatagramPacket pkt = new DatagramPacket(
                        data, data.length,
                        addr.getAddress(), addr.getPort()
                );

                socket.send(pkt);
                System.out.println("[ROUTER " + id + "] Sent DV to " + neighbor + ": " + payload);
            } catch (Exception e) {
                System.out.println("[ROUTER " + id + "][SEND-DV-ERROR] to " + neighbor + ": " + e.getMessage());
            }
        }
    }

    private void handleDV(String raw) {
        try {
            String[] parts = raw.split("\\|", 3);
            if (parts.length < 3) {
                System.out.println("[ROUTER " + id + "] Bad DV packet: " + raw);
                return;
            }

            String neighborId = parts[1];
            String payload = parts[2];

            if (!neighborPorts.containsKey(neighborId)) {
                System.out.println("[ROUTER " + id + "] Ignored DV from unknown neighbor " + neighborId);
                return;
            }

            Map<String, Integer> dv = new HashMap<>();
            String[] entries = payload.split(",");

            for (String entry : entries) {
                if (entry == null || entry.isBlank()) continue;

                String[] kv = entry.split("=");
                if (kv.length != 2) continue;

                String subnet = kv[0].trim();
                int cost = Integer.parseInt(kv[1].trim());
                dv.put(subnet, cost);
            }

            neighborDV.put(neighborId, dv);

            System.out.println("[ROUTER " + id + "] Received DV from " + neighborId + ": " + dv);
            recompute();
        } catch (Exception e) {
            System.out.println("[ROUTER " + id + "][HANDLE-DV-ERROR] " + e.getMessage());
        }
    }

    // Bellman-Ford recomputation
    private void recompute() {
        boolean updated = false;

        // Start fresh from directly connected networks
        Map<String, Integer> newDistanceVector = new HashMap<>();
        Map<String, String> newNextHop = new HashMap<>();

        for (String vip : me.getVirtualIps()) {
            String subnet = DeviceInfo.extractSubnet(vip);
            newDistanceVector.put(subnet, 0);
            newNextHop.put(subnet, id);
        }

        // Learn routes from neighbors
        for (String neighbor : neighborDV.keySet()) {
            Map<String, Integer> dv = neighborDV.get(neighbor);
            if (dv == null) continue;

            for (String subnet : dv.keySet()) {
                int newCost = dv.get(subnet) + 1;

                if (!newDistanceVector.containsKey(subnet)
                        || newCost < newDistanceVector.get(subnet)) {
                    newDistanceVector.put(subnet, newCost);
                    newNextHop.put(subnet, neighbor);
                }
            }
        }

        if (!newDistanceVector.equals(distanceVector) || !newNextHop.equals(nextHop)) {
            distanceVector.clear();
            distanceVector.putAll(newDistanceVector);

            nextHop.clear();
            nextHop.putAll(newNextHop);

            updated = true;
        }

        if (updated) {
            System.out.println("[ROUTER " + id + "] Updated DV: " + distanceVector);
            System.out.println("[ROUTER " + id + "] Updated nextHop: " + nextHop);
        }
    }

    private void handleData(String raw, InetSocketAddress incomingAddr) throws Exception {
        String frame = raw.substring(5); // remove "DATA|"
        String[] parts = frame.split(":", 5);

        if (parts.length < 5) {
            System.out.println("[ROUTER " + id + "] Bad DATA packet: " + raw);
            return;
        }

        String srcMac = parts[0];
        String dstMac = parts[1];
        String srcIp  = parts[2];
        String dstIp  = parts[3];
        String msg    = parts[4];

        // Router only processes frames addressed to itself
        if (!dstMac.equals(id)) return;

        String dstSubnet = DeviceInfo.extractSubnet(dstIp);

        if (!distanceVector.containsKey(dstSubnet) || !nextHop.containsKey(dstSubnet)) {
            System.out.println("[ROUTER " + id + "] No route to " + dstSubnet);
            return;
        }

        String outNeighborId;
        String newDstMac;

        if (distanceVector.get(dstSubnet) == 0) {
            // Destination subnet is directly connected to this router
            outNeighborId = directOutNeighbor.get(dstSubnet);

            if (outNeighborId == null) {
                System.out.println("[ROUTER " + id + "] No direct outgoing neighbor for subnet " + dstSubnet);
                return;
            }

            // Example: dstIp = net3.C -> dst MAC = C
            int dotIndex = dstIp.indexOf(".");
            if (dotIndex < 0 || dotIndex == dstIp.length() - 1) {
                System.out.println("[ROUTER " + id + "] Bad destination virtual IP: " + dstIp);
                return;
            }
            newDstMac = dstIp.substring(dotIndex + 1);
        } else {
            // Forward to next-hop router
            outNeighborId = nextHop.get(dstSubnet);
            newDstMac = outNeighborId;
        }

        InetSocketAddress outAddr = neighborPorts.get(outNeighborId);
        if (outAddr == null) {
            System.out.println("[ROUTER " + id + "] No UDP port for neighbor " + outNeighborId);
            return;
        }

        // Prevent immediate send-back to incoming socket
        if (incomingAddr.getAddress().equals(outAddr.getAddress())
                && incomingAddr.getPort() == outAddr.getPort()) {
            return;
        }

        String newFrame = "DATA|" + id + ":" + newDstMac + ":" + srcIp + ":" + dstIp + ":" + msg;

        System.out.println("[ROUTER " + id + "] Forwarding to " + outNeighborId + ": " + newFrame);

        byte[] data = newFrame.getBytes(StandardCharsets.UTF_8);
        DatagramPacket pkt = new DatagramPacket(
                data, data.length,
                outAddr.getAddress(), outAddr.getPort()
        );
        socket.send(pkt);
    }

    public void run() {
        while (true) {
            try {
                byte[] buf = new byte[4096];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                InetSocketAddress incomingAddr =
                        new InetSocketAddress(pkt.getAddress(), pkt.getPort());

                String raw = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                if (raw.startsWith("DV|")) {
                    handleDV(raw);
                } else if (raw.startsWith("DATA|")) {
                    handleData(raw, incomingAddr);
                } else {
                    System.out.println("[ROUTER " + id + "] Unknown packet type: " + raw);
                }

            } catch (Exception e) {
                System.out.println("[ROUTER " + id + "][RUN-ERROR] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualRouterDV <ID>");
            return;
        }

        String id = args[0];

        String configFile = "config.txt";
        VirtualRouterDV router = new VirtualRouterDV(id, configFile);
        router.run();
    }
}