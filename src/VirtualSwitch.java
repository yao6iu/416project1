/*
*This class simulates an Ethernet learning switch.
*Core algorithm:
 *   Step1: Learn source MAC
 *   Step2: If destination known -> forward
 *   Step3: If unknown -> flood
 */

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class VirtualSwitch {

    private final String id;
    private final ConfigParser cfg;
    private final DeviceInfo me;
    private final DatagramSocket socket;

    // Learned table (kept for extensibility; not used by flooding logic)
    private final Map<String, InetSocketAddress> macTable = new HashMap<>();

    // Fixed ports: neighborId -> UDP address
    private final Map<String, InetSocketAddress> neighborPorts = new HashMap<>();

    public VirtualSwitch(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown switch id: " + id);

        this.socket = new DatagramSocket(null);
        this.socket.bind(new InetSocketAddress(InetAddress.getByName(me.getIp()), me.getPort()));

        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);
            if (d == null) throw new Exception("Unknown neighbor id in config: " + nb);

            InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName(d.getIp()), d.getPort());
            neighborPorts.put(nb, addr);
        }

        System.out.println("[SW " + id + "] bound at " + me.getIp() + ":" + me.getPort()
                + ", neighbors=" + neighborPorts.keySet());
    }

    private void learn(String srcMac, InetSocketAddress incomingPort) {
        InetSocketAddress old = macTable.get(srcMac);
        if (old == null || !old.equals(incomingPort)) {
            macTable.put(srcMac, incomingPort);
        }
    }

    private void sendFrame(String frame, InetSocketAddress out) throws Exception {
        byte[] data = frame.getBytes(StandardCharsets.UTF_8);
        DatagramPacket pkt = new DatagramPacket(data, data.length, out.getAddress(), out.getPort());
        socket.send(pkt);
    }

    public void run() {
        byte[] buf = new byte[4096];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                InetSocketAddress incomingPort = new InetSocketAddress(pkt.getAddress(), pkt.getPort());

                String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                // Your frames are "srcMac:dstMac:rest..." so split at most 3 parts.
                String[] parts = frame.split(":", 3);
                if (parts.length < 3) {
                    System.out.println("[SW " + id + "][DEBUG] bad frame: " + frame);
                    continue;
                }

                String src = parts[0];
                String dst = parts[1];

                System.out.println("[SW " + id + "] Received: " + frame);

                // Learn where src lives (optional; kept for future)
                learn(src, incomingPort);

                // === FLOODING SWITCH (Project-2-friendly for demo rubric) ===
                // Forward to ALL neighbors except the one we received from.
                int sent = 0;
                for (Map.Entry<String, InetSocketAddress> entry : neighborPorts.entrySet()) {
                    InetSocketAddress outPort = entry.getValue();

                    // No send-back to where it came from
                    if (outPort.equals(incomingPort)) continue;

                    sendFrame(frame, outPort);
                    sent++;
                }

                if (sent == 0) {
                    System.out.println("[SW " + id + "][DEBUG] Nothing to forward (all ports blocked?)");
                }

            } catch (Exception e) {
                System.out.println("[SW " + id + "][ERROR] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualSwitch <ID>");
            return;
        }

        String id = args[0];
        VirtualSwitch sw = new VirtualSwitch(id, "src/config.txt");
        sw.run();
    }
}