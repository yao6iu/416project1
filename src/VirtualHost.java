import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
//把从电脑输入的内容传给交换机
public class VirtualHost {

    private final String id;
    private final ConfigParser cfg;//检查每个设备邻居是谁
    private final DeviceInfo me;//自己设备信息

    private final DatagramSocket socket;//udp负责发送接收

    private final String neighborSwitchId;//交换机是谁
    private final InetAddress neighborSwitchIp;//交换机udp地址
    private final int neighborSwitchPort;//host发包地址

    public VirtualHost(String id, String configFile) throws Exception {
        this.id = id;
        this.cfg = new ConfigParser(configFile);

        this.me = cfg.getDevice(id);
        if (me == null) throw new Exception("Unknown host id: " + id);

        List<String> nbs = cfg.getNeighbors(id);
        if (nbs.isEmpty()) throw new Exception("Host " + id + " has no neighbor in config");
        this.neighborSwitchId = nbs.get(0);

        DeviceInfo sw = cfg.getDevice(neighborSwitchId);
        if (sw == null) throw new Exception("Unknown neighbor switch id: " + neighborSwitchId);

        this.neighborSwitchIp = InetAddress.getByName(sw.getIp());
        this.neighborSwitchPort = sw.getPort();

        this.socket = new DatagramSocket(null);
        this.socket.bind(new InetSocketAddress(InetAddress.getByName(me.getIp()), me.getPort()));
        System.out.println("[HOST " + id + "] bound at " + me.getIp() + ":" + me.getPort()
                + ", neighbor=" + neighborSwitchId + "(" + sw.getIp() + ":" + sw.getPort() + ")");
    }//启动准备自己发包，邻居准备接受

    private void startReceiverThread() {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (true) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);

                    String frame = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                    String[] parts = frame.split(":", 3);
                    if (parts.length < 3) {
                        System.out.println("[HOST " + id + "][DEBUG] bad frame: " + frame);
                        continue;
                    }
                    String src = parts[0];
                    String dst = parts[1];
                    String msg = parts[2];

                    System.out.println("[HOST " + id + "] Received message from " + src + ": " + msg);

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
    }//host准备接受和打印

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
    }//等待用户输入

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
