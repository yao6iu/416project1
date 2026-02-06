/*
*This class simulates an Ethernet learning switch.
*Core algorithm:
 *   Step1: Learn source MAC
 *   Step2: If destination known -> forward
 *   Step3: If unknown -> flood
 */

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualSwitch {

    private final String id;
    private final ConfigParser cfg;
    private final DeviceInfo me;
    // UDP socket
    private final DatagramSocket socket;

    //MAC Learning Table
    //MAC -> which port to send
    private final Map<String, InetSocketAddress> macTable = new HashMap<>();//All neighbor ports (switch ports)

    private final Map<String, InetSocketAddress> neighborPorts = new HashMap<>();

    public VirtualSwitch(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);//read config

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


    //print table
    private void printMacTable() {
        System.out.println("----- SWITCH " + id + " TABLE -----");
        for (Map.Entry<String, InetSocketAddress> e : macTable.entrySet()) {
            System.out.println(e.getKey() + " -> " + e.getValue().getAddress().getHostAddress() + ":" + e.getValue().getPort());
        }
        System.out.println("-----------------------------------");
    }


    //remember where the frame comes from
    private void learn(String srcMac, InetSocketAddress incomingPort) {
        if (!macTable.containsKey(srcMac)) {
            macTable.put(srcMac, incomingPort);

            printMacTable();
        } else {

            InetSocketAddress old = macTable.get(srcMac);
            if (!old.equals(incomingPort)) {
                macTable.put(srcMac, incomingPort);
                printMacTable();
            }
        }
    }

    //Send frame out
    private void sendFrame(String frame, InetSocketAddress out) throws Exception {
        byte[] data = frame.getBytes(StandardCharsets.UTF_8);
        DatagramPacket pkt = new DatagramPacket(data, data.length, out.getAddress(), out.getPort());
        socket.send(pkt);
    }


    // receive -> learn -> forward/flood
    public void run() {
        byte[] buf = new byte[4096];
        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                InetSocketAddress incomingPort = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                String[] parts = frame.split(":", 3);
                if (parts.length < 3) {
                    System.out.println("[SW " + id + "][DEBUG] bad frame: " + frame);
                    continue;
                }
                String src = parts[0];
                String dst = parts[1];

                learn(src, incomingPort);

                InetSocketAddress outPort = macTable.get(dst);
                if (outPort != null) {

                    if (!outPort.equals(incomingPort)) {
                        sendFrame(frame, outPort);

                    }
                } else {

                    for (InetSocketAddress nbPort : neighborPorts.values()) {
                        if (!nbPort.equals(incomingPort)) {
                            sendFrame(frame, nbPort);
                        }
                    }

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
