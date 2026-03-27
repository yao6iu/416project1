/*
 * This class simulates an Ethernet learning switch.
 *
 * Core algorithm:
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

    // MAC table: MAC -> port
    private final Map<String, InetSocketAddress> macTable = new HashMap<>();

    // neighborId -> UDP address
    private final Map<String, InetSocketAddress> neighborPorts = new HashMap<>();

    public VirtualSwitch(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown switch id: " + id);

        this.socket = new DatagramSocket(me.getPort());

        for (String nb : cfg.getNeighbors(id)) {
            DeviceInfo d = cfg.getDevice(nb);
            if (d == null) throw new Exception("Unknown neighbor: " + nb);

            neighborPorts.put(nb,
                    new InetSocketAddress(
                            InetAddress.getByName(d.getIp()),
                            d.getPort()
                    ));
        }

        System.out.println("[SW " + id + "] started at " + me.getIp() + ":" + me.getPort());
        System.out.println("[SW " + id + "] neighbors = " + neighborPorts.keySet());
    }

    private void learn(String srcMac, InetSocketAddress port) {
        InetSocketAddress old = macTable.get(srcMac);

        if (old == null || !old.equals(port)) {
            macTable.put(srcMac, port);
            System.out.println("[SW " + id + "] Learned MAC " + srcMac + " -> " + port);
            printTable();
        }
    }

    private void printTable() {
        System.out.println("[SW " + id + "] MAC Table:");
        for (Map.Entry<String, InetSocketAddress> e : macTable.entrySet()) {
            System.out.println("   " + e.getKey() + " -> " + e.getValue());
        }
    }

    private void sendFrame(String frame, InetSocketAddress out) throws Exception {

        System.out.println("[SW " + id + "] Sending to " + out + " : " + frame);

        byte[] data = frame.getBytes(StandardCharsets.UTF_8);
        DatagramPacket pkt = new DatagramPacket(
                data, data.length,
                out.getAddress(),
                out.getPort()
        );
        socket.send(pkt);
    }

    public void run() {
        byte[] buf = new byte[4096];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                InetSocketAddress incomingPort =
                        new InetSocketAddress(pkt.getAddress(), pkt.getPort());

                String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                if (!frame.startsWith("DATA|")) {
                    System.out.println("[SW " + id + "] Invalid frame: " + frame);
                    continue;
                }

                String payload = frame.substring(5);

                String[] parts = payload.split(":", 5);
                if (parts.length < 5) {
                    System.out.println("[SW " + id + "] Bad frame: " + frame);
                    continue;
                }

                String srcMac = parts[0];
                String dstMac = parts[1];

                System.out.println("[SW " + id + "] Received: " + frame);

                // Step 2: 学习
                learn(srcMac, incomingPort);

                // Project 3：still use flooding
                int sent = 0;

                for (Map.Entry<String, InetSocketAddress> entry : neighborPorts.entrySet()) {
                    InetSocketAddress out = entry.getValue();

                    if (out.equals(incomingPort)) continue;

                    sendFrame(frame, out);
                    sent++;
                }

                System.out.println("[SW " + id + "] Flooded to " + sent + " ports");

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

        VirtualSwitch sw = new VirtualSwitch(id, "config.txt");

        sw.run();
    }
}