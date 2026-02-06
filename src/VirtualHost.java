/*
 * Simulates an Ethernet host.
 * Responsibilities:
 *   1. Send frames to its directly connected switch
 *   2. Receive frames from switch
 *   3. Interact with user (input message)

 * IMPORTANT:
 * Host NEVER sends packets directly to another host.
 * All traffic must go through the switch (like real Ethernet).
 */


import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;


public class VirtualHost {

    private final String id; //host ID used as virtual MAC address
    private final ConfigParser cfg;//configuration parser (topology info)
    private final DeviceInfo me;//own information

    private final DatagramSocket socket;//UDP socket used for sending and receiving frames

    // Directly connected switch
    private final String neighborSwitchId;
    private final InetAddress neighborSwitchIp;
    private final int neighborSwitchPort;


    //initialize host and bind UDP socket
    public VirtualHost(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        //get my address
        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown host id: " + id);

        // a host should connect to exactly ONE switch
        List<String> nbs = cfg.getNeighbors(id);
        if (nbs.isEmpty()) throw new Exception("Host " + id + " has no neighbor in config");
        this.neighborSwitchId = nbs.get(0);

        DeviceInfo sw = cfg.getDevice(neighborSwitchId);
        if (sw == null) throw new Exception("Unknown neighbor switch id: " + neighborSwitchId);

        // bind UDP socket
        this.neighborSwitchIp = InetAddress.getByName(sw.getIp());
        this.neighborSwitchPort = sw.getPort();

        this.socket = new DatagramSocket(null);
        this.socket.bind(new InetSocketAddress(InetAddress.getByName(me.getIp()), me.getPort()));
        System.out.println("[HOST " + id + "] bound at " + me.getIp() + ":" + me.getPort()
                + ", neighbor=" + neighborSwitchId + "(" + sw.getIp() + ":" + sw.getPort() + ")");
    }//Start ready to issue your own contract, and the neighbors are ready to accept it


    // Always listen for frames from switch.
    private void startReceiverThread() {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (true) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    //block until packet arrives
                    socket.receive(pkt);


                    String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                    // Frame format: src:dst:msg
                    String[] parts = frame.split(":", 3);
                    if (parts.length < 3) {
                        System.out.println("[HOST " + id + "][DEBUG] bad frame: " + frame);
                        continue;
                    }
                    String src = parts[0];
                    String dst = parts[1];
                    String msg = parts[2];

                    System.out.println("[HOST " + id + "] Received message from " + src + ": " + msg);
                    // if not for me, it means flooding
                    if (!dst.equals(id)) {
                        System.out.println("[HOST " + id + "][DEBUG] MAC address mismatch (flooded frame). dst="
                                + dst + ", me=" + id);
                    }

                } catch (Exception e) {
                    System.out.println("[HOST " + id + "][ERROR] receiver crashed: " + e.getMessage());
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }//host ready to accept and print


    //Read user input and send frame to switch
    private void runSendLoop() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("[HOST " + id + "] Enter: <DEST> <MESSAGE>  (e.g., D hello): ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            int sp = line.indexOf(' ');
            if (sp < 0) {
                System.out.println("Format error. Use: D hello");
                continue;
            }

            String dst = line.substring(0, sp).trim();
            String msg = line.substring(sp + 1).trim();

            // build Ethernet frame
            String frame = id + ":" + dst + ":" + msg;
            try {
                byte[] data = frame.getBytes(StandardCharsets.UTF_8);
                DatagramPacket pkt = new DatagramPacket(data, data.length, neighborSwitchIp, neighborSwitchPort);
                socket.send(pkt);
                System.out.println("[HOST " + id + "] Sent frame to " + neighborSwitchId + ": " + frame);
            } catch (Exception e) {
                System.out.println("[HOST " + id + "][ERROR] send failed: " + e.getMessage());
            }
        }
    }//wait for user input


    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: VirtualHost <ID>");
            return;
        }
        String id = args[0];
        VirtualHost host = new VirtualHost(id, "src/config.txt");
        host.startReceiverThread();
        host.runSendLoop();
    }
}
