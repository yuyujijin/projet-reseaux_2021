import java.io.*;
import java.net.*;

public class Diffuseur {
    private final String id;
    private String[] msgList;
    private final int port1, port2;
    private final String ipmulti, ip2;

    private int NUM_MSG = 0;
    private int MSG_INDEX = 0;

    private static int MAX_MSG = 10000;
    private static int SLEEP_TIME = 1000;

    // Diffuseur identifié par :
    // un id, un port et une adresse multi diff, et un port pour la communication
    // connectée
    public Diffuseur(String id, int port1, int port2, String ipmulti) throws IOException {
        if (id.length() < 8)
            for (int i = id.length(); i < 8; i++)
                id += "#";
        this.id = id.substring(0, 8);
        this.port1 = port1;
        this.port2 = port2;
        this.ipmulti = ipmulti;
        this.ip2 = InetAddress.getLocalHost().getHostAddress();
        msgList = new String[MAX_MSG];
    }

    // TODO...
    // Gérer la concurrence multi thread
    public void loadMessage(String filename) throws IOException {
        String[] msgs = FileLoader.loadMessages(filename);
        for (int i = 0; i < Math.min(msgs.length, MAX_MSG - MSG_INDEX); i++) {
            addMessage(msgs[i]);
        }
    }

    // TODO...
    // Gérer la concurrence multi thread
    private void addMessage(String msg) {
        msgList[NUM_MSG] = msg;
        incrMsg();
    }

    // Permet d'incrémenter le nombre de message
    // TODO...
    // Gérer la concurrence multi thread
    private void incrMsg() {
        NUM_MSG = (NUM_MSG + 1) % 10000;
    }

    // Met s dans le tableau data
    // - Remplit de \0 si trop petit
    // - Tronque si trop grand
    private void stringInBytes(byte[] data, String s) {
        byte[] newS = s.getBytes();
        for (int i = 0; i < data.length; i++) {
            if (i < newS.length)
                data[i] = newS[i];
            else
                data[i] = '\0';
        }
    }

    // Normalise un numéro de message
    private String normalizedNumMsg(int num_msg) {
        if (num_msg < 10)
            return "000" + num_msg;
        if (num_msg < 100)
            return "00" + num_msg;
        if (num_msg < 1000)
            return "0" + num_msg;
        return String.valueOf(num_msg);
    }

    // TODO...
    // gérer concurrence
    private String getMsg() {
        String msg = msgList[MSG_INDEX];
        return msg;
    }

    private void incrMsgIndex() {
        MSG_INDEX = (MSG_INDEX + 1) % NUM_MSG;
    }

    // Fonction utilisée pour la diffusion
    private void diffuse() {
        try (DatagramSocket dso = new DatagramSocket()) {
            System.out.println("JE SUIS LE DIFFUSEUR " + id);
            byte[] data = new byte[4 + 1 + 4 + 1 + 8 + 1 + 140];
            while (true) {
                // On envoie un message en multi-diffusion
                InetSocketAddress ia = new InetSocketAddress(ipmulti, port1);

                String msg = getMsg();
                stringInBytes(data, "DIFF " + normalizedNumMsg(this.MSG_INDEX) + " " + id + " " + msg);
                incrMsgIndex();

                System.out.println(new String(data));

                DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
                dso.send(paquet);

                Thread.sleep(SLEEP_TIME);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        // On créer un thread qui va diffuser
        new Thread(() -> diffuse()).start();
        // Puis on créer un nouveau thread pour récuperer les connexions TCP
        new Thread(() -> {
            try (ServerSocket sso = new ServerSocket(port2)) {
                while (true) {
                    Socket sock = sso.accept();
                    System.out.println("NOUVELLE CO");
                    // On créer un nouveau thread pour la connexion entrante
                    new Thread(() -> {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()))) {
                            char[] command = new char[4];
                            br.read(command, 0, 4);
                            // On regarde quelle commande est effectuée
                            switch (String.valueOf(command)) {
                            case "LAST":
                                lastMsgs(sock, br);
                                break;
                            case "MESS":
                                addMessageToList(sock, br);
                                break;
                            default:
                                System.out.println("COMMANDE INCONNUE");
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
        }).start();
    }

    void lastMsgs(Socket sock, BufferedReader br) throws IOException {
        System.out.println("LAST MSG");
        // On saute l'espace
        br.read();
        char[] nb = new char[3];
        // On récupère le nombre de message demandé
        if (br.read(nb, 0, 3) < 3) {
            br.close();
            return;
        }
        // Pour ne pas dépasser la taille du nombre de message diffusé
        int nbres = Math.min(Integer.valueOf(String.valueOf(nb)), NUM_MSG - 1);
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
        // Puis on envoie les `nbres` derniers messages
        for (int i = 0; i < nbres; i++) {
            String s = "OLDM " + normalizedNumMsg(this.NUM_MSG - i) + " " + id + " " + msgList[NUM_MSG - i - 1];
            byte[] data = new byte[4 + 1 + 4 + 1 + 8 + 1 + 140];
            stringInBytes(data, s);
            // Puis on envoie les données
            pw.print(new String(data));
            pw.flush();
        }
        // On finit par envoyer `ENDM` et on ferme la connexion
        pw.print("ENDM");
        pw.flush();
        sock.close();
    }

    void addMessageToList(Socket sock, BufferedReader br) {

    }

    public static void main(String[] args) throws IOException {
        Diffuseur diff = new Diffuseur("iddiff", 8192, 8999, "225.1.2.4");
        diff.loadMessage("../data/message1.data");
        diff.start();
    }
}