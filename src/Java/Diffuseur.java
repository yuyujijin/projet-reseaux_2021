import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Diffuseur {
    private final String id;
    private String[] msgList;
    private final int port1, port2;
    private final String ipmulti, ip2;

    private AtomicInteger NUM_MSG = new AtomicInteger(0);
    private AtomicInteger MSG_INDEX = new AtomicInteger(0);
    private int NBR_SENT = 0;

    private static int MAX_MSG = 10000;
    private static int SLEEP_TIME = 1000;

    // Diffuseur identifié par :
    // un id, un port et une adresse multi diff, et un port pour la communication
    // connectée
    public Diffuseur(String id, int port1, int port2, String ipmulti) throws IOException {
        this.id = NetRadio.fillWithSharp(id, NetRadio.ID);
        this.port1 = port1;
        this.port2 = port2;
        this.ipmulti = ipmulti;
        this.ip2 = InetAddress.getLocalHost().getHostAddress();
        msgList = new String[MAX_MSG];

        startMessage(id, ipmulti, ip2, port1, port2);
    }

    public void loadMessage(String filename) throws IOException {
        String[] msgs = FileLoader.loadMessages(filename);
        synchronized (msgList) {
            for (int i = 0; i < Math.min(msgs.length, (MAX_MSG - MSG_INDEX.get())); i++) {
                addMessage(id + " " + msgs[i]);
            }
        }
    }

    private void addMessage(String msg) {
        synchronized (msgList) {
            msgList[NUM_MSG.get()] = NetRadio.fillWithSharp(msg, NetRadio.ID + 1 + NetRadio.MESS);
            incrMsg();
        }
    }

    // Permet d'incrémenter le nombre de message
    private void incrMsg() {
        NUM_MSG.set((NUM_MSG.incrementAndGet()) % 10000);
    }

    private void incrMsgIndex() {
        MSG_INDEX.set((MSG_INDEX.incrementAndGet()) % NUM_MSG.get());
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

    private String getMsg() {
        synchronized (msgList) {
            return msgList[MSG_INDEX.get()];
        }
    }

    public void start() throws UnknownHostException, IOException {
        // On créer un thread qui va diffuser
        new Thread(() -> diffuse()).start();
        // Puis on créer un nouveau thread pour récuperer les connexions TCP
        new Thread(() -> {
            try (ServerSocket sso = new ServerSocket(port2)) {
                while (true) {
                    Socket sock = sso.accept();
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
                                sock.close();
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (Exception e) {
                System.out.println("An error as occured : " + e.getMessage());
            }
        }).start();
        // Puis on lit en boucle les inputs pour récuperer les demandes de REGI
        Scanner s = new Scanner(System.in);
        while (true) {
            String rq = s.nextLine();
            String[] opts = rq.split(" ");
            if (opts[0].equals("REGI")) {
                new Thread(() -> {
                    try {
                        String ip = opts[1];
                        int port = Integer.valueOf(opts[2]);
                        Socket sock = new Socket(ip, port);
                        PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
                        pw.print("REGI " + this.id + " " + NetRadio.normalizeIp(this.ipmulti) + " " + this.port1 + " "
                                + NetRadio.normalizeIp(this.ip2) + " " + this.port2 + "\r\n");
                        pw.flush();
                        BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                        char[] data = new char[6];
                        br.read(data, 0, 6);
                        if (String.valueOf(data).equals("REOK\r\n")) {
                            System.out.println("Enregistrement réussi !");
                            workWithGestionnaire(sock);
                        } else {
                            System.out.println("Echec de l'enregistrement...");
                            sock.close();
                        }
                    } catch (Exception e) {
                        System.out.println("Erreur lors de la reception de la commande...");
                    }
                }).start();
            }
        }
    }

    private void workWithGestionnaire(Socket socket) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        while (true) {
            char[] data = new char[6];
            br.read(data, 0, 6);
            if (String.valueOf(data).equals("RUOK\r\n")) {
                pw.print("IMOK\r\n");
                pw.flush();
            } else {
                break;
            }
        }
        socket.close();
    }

    // Fonction utilisée pour la diffusion
    private void diffuse() {
        try (DatagramSocket dso = new DatagramSocket()) {
            byte[] data = new byte[4 + 1 + NetRadio.NUMMESS + 1 + NetRadio.ID + 1 + NetRadio.MESS + 2];
            while (true) {
                // On envoie un message en multi-diffusion
                InetSocketAddress ia = new InetSocketAddress(ipmulti, port1);

                String msg = getMsg();
                stringInBytes(data, "DIFF " + NetRadio.fillWithZero(NBR_SENT, NetRadio.NUMMESS) + " " + msg + "\r\n");
                incrMsgIndex();
                NBR_SENT = (NBR_SENT + 1) % MAX_MSG;

                DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
                dso.send(paquet);

                Thread.sleep(SLEEP_TIME);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void lastMsgs(Socket sock, BufferedReader br) throws IOException {
        // On saute l'espace
        br.read();
        char[] nb = new char[5];
        // On récupère le nombre de message demandé
        if (br.read(nb, 0, 5) < 5) {
            br.close();
            return;
        }

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
        synchronized (msgList) {
            // On prend une copie de NBR_SENT, qui ne bougera pas
            // (une sorte d'image figée au moment de la requête)
            int nb_sent_cpy = NBR_SENT;
            // Pour ne pas dépasser la taille du nombre de message diffusé
            int nbres = Math.min(Integer.valueOf(String.valueOf(nb).strip()), nb_sent_cpy + 1);
            // Puis on envoie les `nbres` derniers messages
            for (int i = 0; i < nbres; i++) {
                String s = "OLDM " + NetRadio.fillWithZero(nb_sent_cpy - i, NetRadio.NUMMESS) + " "
                        + msgList[(nb_sent_cpy - i) % NUM_MSG.get()] + "\r\n";
                byte[] data = new byte[4 + 1 + NetRadio.NUMMESS + 1 + NetRadio.ID + 1 + NetRadio.MESS + 2];
                stringInBytes(data, s);
                // Puis on envoie les données
                pw.print(new String(data));
                pw.flush();
            }
        }
        // On finit par envoyer `ENDM` et on ferme la connexion
        pw.print("ENDM\r\n");
        pw.flush();
        sock.close();
    }

    void addMessageToList(Socket sock, BufferedReader br) throws IOException {
        // On saute l'espace
        br.read();

        char[] msgChars = new char[NetRadio.ID + 1 + NetRadio.MESS + 2];
        br.read(msgChars, 0, NetRadio.ID + 1 + NetRadio.MESS + 2);
        addMessage(String.valueOf(msgChars).strip());

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
        pw.print("ACKM\r\n");
        pw.flush();

        sock.close();
    }

    private static void startMessage(String id, String ip1, String ip2, int port1, int port2) {
        System.out.println("#-----------------------------------#");
        System.out.println(" * id : " + id);
        System.out.println(" * ip multi diffusion : " + ip1);
        System.out.println(" * port multi diffusion : " + port1);
        System.out.println(" * ip TCP : " + ip2);
        System.out.println(" * port TCP : " + port2);
        System.out.println("#-----------------------------------#");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Veuillez indiquer un fichier de réglage");
            return;
        } else if (args.length < 2) {
            System.err.println("Veuillez indiquer un fichier de message");
            return;
        }
        HashMap<String, String> settings = (HashMap<String, String>) FileLoader.loadSettings(args[0]);

        String id = settings.get("id");
        String ip1 = settings.get("ip1");
        int port1 = Integer.valueOf(settings.get("port1"));
        int port2 = Integer.valueOf(settings.get("port2"));
        Diffuseur diff = new Diffuseur(id, port1, port2, ip1);
        diff.loadMessage(args[1]);
        diff.start();
    }
}