import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualRouter {

    private final String id;
    private final ConfigParser cfg;
    private final DeviceInfo me;
    private final DatagramSocket socket;

    // subnet -> "DIRECT" or "net2.R2" (meaning forward via next-hop R2 on net2)
    private final Map<String, String> routingTable = new HashMap<>();

    // For DIRECT subnets: subnet -> which NEIGHBOR to send the UDP packet to
    // (e.g., R1: net1 -> S1, net2 -> R2; R2: net3 -> S2, net2 -> R1)
    private final Map<String, String> directOutNeighbor = new HashMap<>();

    // neighborId -> UDP address
    private final Map<String, InetSocketAddress> neighborPorts = new HashMap<>();

    public VirtualRouter(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown router id: " + id);

        this.socket = new DatagramSocket(null);
        this.socket.bind(new InetSocketAddress(
                InetAddress.getByName(me.getIp()),
                me.getPort()
        ));

        // Build neighbor port map (ports are addressed by neighbor ID)
        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);
            if (d == null) throw new Exception("Unknown neighbor id in config: " + nb);

            InetSocketAddress addr = new InetSocketAddress(
                    InetAddress.getByName(d.getIp()),
                    d.getPort()
            );
            neighborPorts.put(nb, addr);
        }

        initRouting();

        System.out.println("[ROUTER " + id + "] bound at "
                + me.getIp() + ":" + me.getPort()
                + ", neighbors=" + neighborPorts.keySet());
    }

    private void initRouting() {
        // Hard-coded for the Project 2 topology shown on the demo sheet:
        // Subnet1: A,B,S1,R1
        // Subnet2: R1,R2
        // Subnet3: C,D,S2,R2
        if (id.equals("R1")) {
            routingTable.put("net1", "DIRECT");
            routingTable.put("net2", "DIRECT");
            routingTable.put("net3", "net2.R2");

            directOutNeighbor.put("net1", "S1");
            directOutNeighbor.put("net2", "R2");
        }

        if (id.equals("R2")) {
            routingTable.put("net3", "DIRECT");
            routingTable.put("net2", "DIRECT");
            routingTable.put("net1", "net2.R1");

            directOutNeighbor.put("net3", "S2");
            directOutNeighbor.put("net2", "R1");
        }
    }

    private String findIncomingNeighborId(InetSocketAddress incomingAddr) {
        for (Map.Entry<String, InetSocketAddress> e : neighborPorts.entrySet()) {
            InetSocketAddress nb = e.getValue();
            if (nb.getAddress().equals(incomingAddr.getAddress()) && nb.getPort() == incomingAddr.getPort()) {
                return e.getKey();
            }
        }
        return null;
    }

    private static String deviceFromVirtualIp(String ip) {
        // ip like "net3.D" -> "D"
        String[] parts = ip.split("\\.");
        if (parts.length < 2) return ip;
        return parts[1];
    }

    private static String nextHopFromAction(String action) {
        // action like "net2.R2" -> "R2"
        String[] parts = action.split("\\.");
        if (parts.length < 2) return action;
        return parts[1];
    }

    public void run() {
        byte[] buf = new byte[4096];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                InetSocketAddress incomingAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());

                String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                String[] parts = frame.split(":", 5);
                if (parts.length < 5) continue;

                String dstMac = parts[1];
                String srcIp  = parts[2];
                String dstIp  = parts[3];
                String msg    = parts[4];

                // Router only processes frames addressed to its own virtual MAC
                if (!dstMac.equals(id)) continue;

                String inNeighborId = findIncomingNeighborId(incomingAddr);

                System.out.println("[ROUTER " + id + "] Incoming:");
                System.out.println(frame);

                String dstSubnet = DeviceInfo.extractSubnet(dstIp);
                String action = routingTable.get(dstSubnet);

                if (action == null) {
                    System.out.println("[ROUTER " + id + "][DEBUG] No route for subnet " + dstSubnet + " (drop)");
                    continue;
                }

                String outNeighborId;
                String newDstMac;

                if ("DIRECT".equals(action)) {
                    // L2 destination MAC should be the final destination device on that subnet (usually a host like D)
                    newDstMac = deviceFromVirtualIp(dstIp);

                    // UDP next hop should be the neighbor that connects us to that subnet
                    outNeighborId = directOutNeighbor.get(dstSubnet);

                    // Fallback: if the destination device itself is directly connected, send to it
                    if (outNeighborId == null && neighborPorts.containsKey(newDstMac)) {
                        outNeighborId = newDstMac;
                    }

                    if (outNeighborId == null) {
                        System.out.println("[ROUTER " + id + "][DEBUG] DIRECT route but no out-neighbor for subnet " + dstSubnet + " (drop)");
                        continue;
                    }
                } else {
                    // Non-direct: forward to next-hop router
                    String nextHopId = nextHopFromAction(action);
                    outNeighborId = nextHopId;
                    newDstMac = nextHopId;

                    if (!neighborPorts.containsKey(outNeighborId)) {
                        System.out.println("[ROUTER " + id + "][DEBUG] Next-hop " + outNeighborId + " is not a neighbor (drop)");
                        continue;
                    }
                }

                // NO send-back
                if (inNeighborId != null && inNeighborId.equals(outNeighborId)) {
                    System.out.println("[ROUTER " + id + "][DEBUG] Received from " + inNeighborId + " and would send back to same port (drop)");
                    continue;
                }

                // Forwarded frame: SRC_MAC becomes this router; DST_MAC becomes next L2 hop
                String newFrame = id + ":" + newDstMac + ":" + srcIp + ":" + dstIp + ":" + msg;

                System.out.println("[ROUTER " + id + "] Outgoing (to " + outNeighborId + "):");
                System.out.println(newFrame);

                InetSocketAddress outAddr = neighborPorts.get(outNeighborId);
                if (outAddr == null) {
                    System.out.println("[ROUTER " + id + "][DEBUG] No UDP port for neighbor " + outNeighborId + " (drop)");
                    continue;
                }

                byte[] data = newFrame.getBytes(StandardCharsets.UTF_8);
                DatagramPacket outPkt = new DatagramPacket(data, data.length, outAddr.getAddress(), outAddr.getPort());
                socket.send(outPkt);

            } catch (Exception e) {
                System.out.println("[ROUTER " + id + "][ERROR] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualRouter <ID>");
            return;
        }

        String id = args[0];
        VirtualRouter router = new VirtualRouter(id, "src/config.txt");
        router.run();
    }
}