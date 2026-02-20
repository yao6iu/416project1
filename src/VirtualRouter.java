import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualRouter {

    private final String id;
    private final ConfigParser cfg;
    private final DeviceInfo me;
    private final DatagramSocket socket;

    private final Map<String, String> forwardingTable = new HashMap<>();
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

        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);
            InetSocketAddress addr =
                    new InetSocketAddress(
                            InetAddress.getByName(d.getIp()),
                            d.getPort()
                    );
            neighborPorts.put(nb, addr);
        }

        initForwardingTable();

        System.out.println("[ROUTER " + id + "] bound at "
                + me.getIp() + ":" + me.getPort()
                + ", neighbors=" + neighborPorts.keySet());
    }

    private void initForwardingTable() {

        if (id.equals("R1")) {
            forwardingTable.put("net1", "DIRECT");
            forwardingTable.put("net2", "DIRECT");
            forwardingTable.put("net3", "net2.R2");
        }

        if (id.equals("R2")) {
            forwardingTable.put("net3", "DIRECT");
            forwardingTable.put("net2", "DIRECT");
            forwardingTable.put("net1", "net2.R1");
        }
    }

    public void run() {
        byte[] buf = new byte[4096];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                String frame = new String(pkt.getData(), 0,
                        pkt.getLength(), StandardCharsets.UTF_8);

                String[] parts = frame.split(":", 5);
                if (parts.length < 5) continue;

                String srcMac = parts[0];
                String dstMac = parts[1];
                String srcIp  = parts[2];
                String dstIp  = parts[3];
                String msg    = parts[4];

                if (!dstMac.equals(id)) continue;

                System.out.println("[ROUTER " + id + "] Received:");
                System.out.println(frame);

                String subnet = DeviceInfo.extractSubnet(dstIp);
                String action = forwardingTable.get(subnet);

                if (action == null) {
                    System.out.println("[ROUTER " + id + "] No route for subnet " + subnet);
                    continue;
                }

                String newDstMac;

                if (action.equals("DIRECT")) {
                    newDstMac = dstIp.split("\\.")[1];
                } else {
                    newDstMac = action.split("\\.")[1];
                }

                String newFrame =
                        id + ":" +
                                newDstMac + ":" +
                                srcIp + ":" +
                                dstIp + ":" +
                                msg;

                System.out.println("[ROUTER " + id + "] Forwarding:");
                System.out.println(newFrame);

                for (InetSocketAddress addr : neighborPorts.values()) {
                    byte[] data = newFrame.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket outPkt =
                            new DatagramPacket(data, data.length,
                                    addr.getAddress(),
                                    addr.getPort());
                    socket.send(outPkt);
                }

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
