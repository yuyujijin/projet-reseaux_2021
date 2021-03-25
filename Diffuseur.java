import java.io.*;
import java.net.*;

public class Diffuseur {
    private final String id;
    private String[] msgList;
    private final int port1, port2;
    private final String ipmulti, ip2;
    private int num_msg = 0;

    Diffuseur(String id, int port1, int port2, String ipmulti) throws IOException {
        if (id.length() < 8) {
            for (int i = id.length(); i < 8; i++) {
                id += "#";
            }
        }
        this.id = id;
        this.port1 = port1;
        this.port2 = port2;
        this.ipmulti = ipmulti;
        this.ip2 = InetAddress.getLocalHost().getHostAddress();
        msgList = new String[10000];
    }

    void incrMsg() {
        num_msg = (num_msg + 1) % 10000;
    }

    void addMsg(byte[] data, String s) {
        byte[] newS = s.getBytes();
        for (int i = 0; i < data.length; i++) {
            if (i < newS.length)
                data[i] = newS[i];
            else
                data[i] = '\0';
        }
    }

    String normalizedNumMsg(int num_msg) {
        if (num_msg < 10)
            return "000" + num_msg;
        if (num_msg < 100)
            return "00" + num_msg;
        if (num_msg < 1000)
            return "0" + num_msg;
        return String.valueOf(num_msg);
    }

    void diffuse() {
        try (DatagramSocket dso = new DatagramSocket()) {
            System.out.println("JE SUIS LE DIFFUSEUR " + id);
            byte[] data = new byte[4 + 1 + 4 + 1 + 8 + 1 + 140];
            int i = 0;
            while (true) {
                InetSocketAddress ia = new InetSocketAddress(ipmulti, port1);
                String s = "Je suis un message pret a etre envoye et j'aime la confiture sa mere " + i++;
                msgList[num_msg] = s;
                addMsg(data, "DIFF " + normalizedNumMsg(this.num_msg) + " " + id + " " + s);
                System.out.println("JE DIFFUSE UN MESSAGE À L'ADRESSE " + ipmulti + " ET SUR LE PORT " + port1);
                DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
                dso.send(paquet);
                incrMsg();
                System.out.println("PAQUET ENVOYE " + normalizedNumMsg(this.num_msg));
                Thread.sleep(5000);
            }
        } catch (Exception se) {
            se.printStackTrace();
        }
    }

    void start() {
        new Thread(() -> {
            diffuse();
        }).start();
        try (ServerSocket sso = new ServerSocket(port2)) {
            while (true) {
                Socket sock = sso.accept();
                System.out.println("NOUVELLE CO");
                new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()))) {
                        char[] command = new char[4];
                        br.read(command, 0, 4);
                        switch (String.valueOf(command)) {
                        case "LAST":
                            lastMsgs(sock, br);
                            break;
                        case "MESS":
                            addToSend(sock, br);
                            break;
                        default:
                            sock.close();
                            break;

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void lastMsgs(Socket sock, BufferedReader br) throws IOException {
        System.out.println("LAST MSG");
        br.read();
        char[] nb = new char[3];
        if (br.read(nb, 0, 3) < 3) {
            br.close();
            return;
        }
        System.out.println("NBRES");
        int nbres = Math.min(Integer.valueOf(String.valueOf(nb)), num_msg - 1);
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
        for (int i = 0; i < nbres; i++) {
            String s = "OLDM " + normalizedNumMsg(this.num_msg - i) + " " + id + " " + msgList[num_msg - i - 1];
            byte[] data = new byte[4 + 1 + 4 + 1 + 8 + 1 + 140];
            addMsg(data, s);
            System.out.println(new String(data, 0, data.length).length());
            pw.print(new String(data));
        }
        pw.print("ENDM");
        pw.flush();
        sock.close();
    }

    void addToSend(Socket sock, BufferedReader br) {

    }

    public static void main(String[] args) throws IOException {
        Diffuseur diff = new Diffuseur("iddiff", 8192, 8999, "225.1.2.4");
        diff.start();
    }
}