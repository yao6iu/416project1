/*
 * Simulates an Ethernet host.
 * Responsibilities:
 *   1. Send frames to its directly connected switch
 *   2. Receive frames from switch
 *   3. Interact with user (input message)
 *
 * IMPORTANT:
 * Host NEVER sends packets directly to another host.
 * All traffic must go through the switch (like real Ethernet).
 */

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class VirtualHost {

    private final String id; // host ID used as virtual MAC address
    private final ConfigParser cfg; // configuration parser (topology info)
    private final DeviceInfo me; // own information

    private final DatagramSocket socket; // UDP socket used for sending and receiving frames

    // Directly connected switch
    private final String neighborSwitchId;
    private final InetAddress neighborSwitchIp;
    private final int neighborSwitchPort;

    private final String myVirtualIp;
    private final String gatewayIp;
    private final String gatewayMac;

    // initialize host and bind UDP socket
    public VirtualHost(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown host id: " + id);

        if (me.getVirtualIps() == null || me.getVirtualIps().isEmpty()) {
            throw new Exception("Host " + id + " has no virtual IP");
        }

        this.myVirtualIp = me.getVirtualIps().get(0);

        this.gatewayIp = me.getGateway(); // e.g., "net1.R1"
        if (gatewayIp == null || !gatewayIp.contains(".")) {
            throw new Exception("Host " + id + " has invalid gateway: " + gatewayIp);
        }
        this.gatewayMac = gatewayIp.split("\\.")[1]; // e.g., "R1"

        List<String> nbs = cfg.getNeighbors(id);
        if (nbs == null || nbs.isEmpty()) {
            throw new Exception("Host " + id + " has no neighbor in config");
        }
        this.neighborSwitchId = nbs.get(0);

        DeviceInfo sw = cfg.getDevice(neighborSwitchId);
        if (sw == null) {
            throw new Exception("Unknown neighbor switch id: " + neighborSwitchId);
        }

        this.neighborSwitchIp = InetAddress.getByName(sw.getIp());
        this.neighborSwitchPort = sw.getPort();

        this.socket = new DatagramSocket(me.getPort());

        System.out.println("[HOST " + id + "] bound at " + me.getIp() + ":" + me.getPort()
                + ", neighbor=" + neighborSwitchId + "(" + sw.getIp() + ":" + sw.getPort() + ")");
        System.out.println("[HOST " + id + "] myVirtualIp=" + myVirtualIp
                + ", gateway=" + gatewayIp + ", gatewayMac=" + gatewayMac);
    }

    private void startReceiverThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    byte[] buf = new byte[4096];
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);

                    String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                    if (!frame.startsWith("DATA|")) {
                        System.out.println("[HOST " + id + "][DEBUG] non-DATA frame ignored: " + frame);
                        continue;
                    }

                    String payload = frame.substring(5); // remove "DATA|"
                    String[] parts = payload.split(":", 5);

                    if (parts.length < 5) {
                        System.out.println("[HOST " + id + "][DEBUG] bad frame: " + frame);
                        continue;
                    }

                    String srcMac = parts[0];
                    String dstMac = parts[1];
                    String srcIp  = parts[2];
                    String dstIp  = parts[3];
                    String msg    = parts[4];

                    if (!dstMac.equals(id)) {
                        System.out.println("[HOST " + id + "][DEBUG] MAC address mismatch (flooded/wrong frame). dst="
                                + dstMac + ", me=" + id);
                        continue;
                    }

                    System.out.println("[HOST " + id + "] Received message from " + srcIp + ": " + msg);

                } catch (Exception e) {
                    System.out.println("[HOST " + id + "][ERROR] receiver crashed: " + e.getMessage());
                }
            }
        });

        t.setDaemon(true);
        t.start();
    }

    private void runSendLoop() {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("[HOST " + id + "] Enter: <DEST_IP> <MESSAGE>  (e.g., net3.C hello): ");
            String line = sc.nextLine().trim();

            if (line.isEmpty()) continue;

            int sp = line.indexOf(' ');
            if (sp < 0) {
                System.out.println("Format error. Use: net3.C hello");
                continue;
            }

            String dstIp = line.substring(0, sp).trim(); // e.g., "net2.B" or "net3.C"
            String msg = line.substring(sp + 1).trim();

            if (!dstIp.contains(".")) {
                System.out.println("[HOST " + id + "][ERROR] bad destination IP: " + dstIp);
                continue;
            }

            // Decide dst MAC:
            // - same subnet: send directly to destination host (dst MAC = host ID, e.g., "B")
            // - different subnet: send to gateway router (dst MAC = gatewayMac, e.g., "R1")
            String mySubnet = myVirtualIp.split("\\.")[0];
            String dstSubnet = dstIp.split("\\.")[0];

            String dstMac = gatewayMac; // default: gateway
            if (mySubnet.equals(dstSubnet)) {
                String[] ipParts = dstIp.split("\\.");
                if (ipParts.length >= 2 && !ipParts[1].isBlank()) {
                    dstMac = ipParts[1]; // net2.B -> B
                } else {
                    System.out.println("[HOST " + id + "][ERROR] bad destination IP: " + dstIp);
                    continue;
                }
            }

            String frame = "DATA|" + id + ":" + dstMac + ":" + myVirtualIp + ":" + dstIp + ":" + msg;

            try {
                byte[] data = frame.getBytes(StandardCharsets.UTF_8);
                DatagramPacket pkt = new DatagramPacket(
                        data,
                        data.length,
                        neighborSwitchIp,
                        neighborSwitchPort
                );
                socket.send(pkt);
                System.out.println("[HOST " + id + "] Sent frame to " + neighborSwitchId + ": " + frame);
            } catch (Exception e) {
                System.out.println("[HOST " + id + "][ERROR] send failed: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualHost <ID>");
            return;
        }

        String id = args[0];
        VirtualHost host = new VirtualHost(id, "config.txt");
        host.startReceiverThread();
        host.runSendLoop();
    }
}