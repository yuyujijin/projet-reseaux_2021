import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public final class Client {
    private static final String[] cmdList = { "'LISTEN' : Begin listening to a specified diffusor.",
            "'LIST' : Ask for a list of diffusor to a diffusor manager.",
            "'LAST' : Ask for the n last messages of a diffusor", "'exit' : Leaves the client.",
            "'HELP' : Print every possible commands.", "'MESS' : Send a client message to a diffusor." };

    private String id;

    private Client() {
    }

    private void start() {
        System.out.println("Welcome to the NetRadio client !");
        Scanner s = new Scanner(System.in);
        System.out.println("Please enter your username :-)");
        id = NetRadio.fillWithSharp(s.nextLine().strip(), NetRadio.ID);
        System.out.println("Type 'HELP' to print every commands and 'exit' to exit the client.");
        while (true) {
            try {
                System.out.print(id + " > ");
                String cmd = s.nextLine();
                switch (cmd.strip()) {
                    case "LISTEN":
                        listen(s);
                        break;
                    case "LIST":
                        list(s);
                        break;
                    case "LSFI":
                        lsfi(s);
                        break;
                    case "HELP":
                        help();
                        break;
                    case "MESS":
                        mess(s);
                        break;
                    case "LAST":
                        last(s);
                        break;
                    case "DLFI":
                        dlfi(s);
                        break;
                    case "exit":
                        return;
                    default:
                        System.out.println("Unknown command \"" + cmd
                                + "\". Type \"HELP\" to get a list of every possible commands.");
                        break;
                }
            } catch (Exception e) {
                System.out.println("An error as occured : [" + e.getClass() + "] " + e.getMessage());
            }
        }
    }

    private String[] ipAndPort(Scanner s) {
        System.out.println("Please specify the ip and the port of the diffusor you would "
                + "like to listen to in this format : \"IP PORT\" (without the quotes).");
        String[] args = s.nextLine().strip().split(" ");
        while (args.length != 2) {
            System.out.println("Wrong number of arguments. Format : \"IP PORT\" (without the quotes).");
            args = s.nextLine().strip().split(" ");
        }
        return args;
    }

    private JTextArea createPrompWindow(String ip, int port, MulticastSocket mso) {
        JFrame frame = new JFrame(ip + ":" + port);
        frame.setSize(540, 320);

        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent windowEvent) {
                System.out.println("Stopped listening to " + ip + ":" + port);
                try {
                    mso.leaveGroup(InetAddress.getByName(ip));
                } catch (Exception e) {
                    System.out.println("Something went wrong when unsubscribing to the diffusor...");
                }
            }
        });

        JTextArea pane = new JTextArea();
        pane.setLineWrap(true);
        pane.setWrapStyleWord(true);
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setEditable(false);
        pane.setSize(frame.getSize());

        JScrollPane jspane = new JScrollPane(pane);
        jspane.setSize(frame.getSize());
        frame.add(jspane, BorderLayout.CENTER);

        frame.setVisible(true);
        return pane;
    }

    private void listen(Scanner s) {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        new Thread(() -> {
            try {
                MulticastSocket mso = new MulticastSocket(port);
                mso.joinGroup(InetAddress.getByName(ip));
                int length = 4 + 1 + NetRadio.NUMMESS + 1 + NetRadio.ID + 1 + NetRadio.MESS + 2;
                byte[] data = new byte[length];
                DatagramPacket paquet = new DatagramPacket(data, data.length);

                System.out.println("Now listening to " + ip + ":" + port + "...");
                // On créer une nouvelle fenêtre pour afficher les entrées
                JTextArea pane = createPrompWindow(ip, port, mso);
                while (true) {
                    mso.receive(paquet);
                    String st = new String(paquet.getData(), 0, paquet.getLength());

                    String time = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
                    String formatted = NetRadio.removeSharp(st.substring(0, st.length() - 2));
                    pane.append("[" + time + "] " + formatted + "\n");
                    pane.setCaretPosition(pane.getDocument().getLength());
                    pane.revalidate();
                }
            } catch (Exception e) {
                System.out.println(
                        "Something went wrong when trying to listen to the diffusor... (Are your sure the diffusor is online?)");
            }
        }).start();
    }

    private void list(Scanner s) throws IOException {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        try (Socket socket = new Socket(ip, port)) {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            pw.print("LIST\r\n");
            pw.flush();

            char[] nbr = new char[4 + 1 + NetRadio.NUMDIFF + 2];
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            br.read(nbr, 0, 4 + 1 + NetRadio.NUMDIFF + 2);

            int n = Integer.valueOf(String.valueOf(nbr).strip().split(" ")[1]);
            System.out.print(n + " diffusor" + ((n != 1) ? "s are" : " is") + " registered here.");
            if (n > 0) {
                System.out.println(" Here is the list of every of them :");
                System.out.printf("%-5s %-9s %-16s %-5s %-16s %-5s%n", "#", "ID", "IP1", "PORT1", "IP2", "PORT2");
            } else
                System.out.println();
            for (int i = 0; i < n; i++) {
                int length = 4 + 1 + NetRadio.ID + 1 + NetRadio.IP + 1 + NetRadio.PORT + 1 + NetRadio.IP + 1
                        + NetRadio.PORT + 2;

                char[] item = new char[length];

                br.read(item, 0, length);

                String[] elems = String.valueOf(item).strip().split(" ");
                System.out.printf("%-5d %-9s %-16s %-5s %-16s %-5s%n", i + 1, elems[1], elems[2], elems[3], elems[4],
                        elems[5]);
            }
            socket.close();
        } catch (UnknownHostException uhe) {
            System.out.println("Could not resolve host name " + ip + ":" + port + "...");
        }
    }

    private void last(Scanner s) throws IOException {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        try (Socket socket = new Socket(ip, port)) {
            System.out.println("Enter a number between 0 and 999 (included)");
            int n = Integer.valueOf(s.nextLine().strip());
            while (n < 0 || n > 999) {
                System.out.println("Enter a number between 0 and 999 (included)");
                n = Integer.valueOf(s.nextLine().strip());
            }
            String nStr = NetRadio.fillWithZero(n, NetRadio.NBMESS);

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

            System.out.println(nStr);
            pw.print("LAST " + nStr + "\r\n");
            pw.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            char[] cmd = new char[4];
            br.read(cmd, 0, 4);
            while (!String.valueOf(cmd).equals("ENDM")) {
                br.read();

                int size = NetRadio.NUMMESS + 1 + NetRadio.ID + 1 + NetRadio.MESS + 2;

                char[] item = new char[size];
                br.read(item, 0, size);

                System.out.println(NetRadio.removeSharp(String.valueOf(item).strip()));

                br.read(cmd, 0, 4);
            }
            br.read(cmd, 0, 2);
        } catch (UnknownHostException uhe) {
            System.out.println("Could not resolve host name " + ip + ":" + port + "...");
        }
    }

    private void mess(Scanner s) throws IOException {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        Socket socket = new Socket(ip, port);

        System.out.println("Please enter your message (Remember: If your message is longer than" + NetRadio.MESS
                + "characters, it will be truncated): ");

        String mess = NetRadio.fillWithSharp(s.nextLine().strip(), NetRadio.MESS);

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

        pw.print("MESS " + id + " " + mess + "\r\n");
        pw.flush();

        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        char[] resp = new char[6];
        br.read(resp, 0, 6);
        if (!String.valueOf(resp).equals("ACKM\r\n"))
            System.out.println("We couldn't send your message as there was an error with it, sorry!");
        else
            System.out.println("Message sent successfully !");
    }

    private void lsfi(Scanner s) throws IOException {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        Socket socket = new Socket(ip, port);

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

        pw.print("LSFI\r\n");
        pw.flush();

        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        char[] resp = new char[4 + 1 + NetRadio.NBFILE + 2];
        br.read(resp, 0, 4 + 1 + NetRadio.NBFILE + 2);

        int n = Integer.valueOf(String.valueOf(resp).substring(4).strip());

        System.out.println(n + " file.s registered here.");

        char[] cmd = new char[4];

        for (int i = 0; i < n; i++) {
            br.read(cmd, 0, 4);
            br.read();

            char[] file = new char[NetRadio.FILENAME + 2];
            br.read(file, 0, NetRadio.FILENAME + 2);

            System.out.println(" * " + NetRadio.removeSharp(String.valueOf(file).strip()));
        }

        char[] endf = new char[6];
        br.read(endf, 0, 6);

        socket.close();
    }

    private void dlfi(Scanner s) throws IOException {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        Socket socket = new Socket(ip, port);

        System.out.println("Which file would you like to download?");

        String f = s.nextLine().trim();

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        pw.print("LSDL " + NetRadio.fillWithSharp(f, NetRadio.FILENAME));
        pw.flush();

        // On utilise un input stream car on va récuperer des bytes
        // On évite de mélanger les streams

        InputStream in = socket.getInputStream();

        byte[] resp = new byte[4];
        in.read(resp, 0, 4);
        if (new String(resp).equals("FIOK")) {
            in.read();
            byte[] size = new byte[NetRadio.FILESIZE + 2];
            in.read(size, 0, NetRadio.FILESIZE + 2);

            int filesize = Integer.valueOf(new String(size).trim());

            System.out.println("The file " + f + " weight " + filesize + "B. It will be saved at `downloads/" + f
                    + "`.\n" + "(If a file of the same name already exists, it will be removed.)");

            // Si le dossier downloads n'existe pas on le créer
            File downloads = new File("downloads");
            if (!downloads.exists()) {
                downloads.mkdir();
            }

            // Puis on créer le fichier
            File nFile = new File("downloads/" + f);

            if (nFile.exists())
                nFile.delete();

            if (!nFile.createNewFile()) {
                System.err.println("Error while creating the file...");
                socket.close();
                return;
            }

            System.out.println("Beginning download...");

            FileOutputStream fos = new FileOutputStream(nFile);

            int buff_size = 8192;

            byte[] buff = new byte[buff_size];
            int len;
            int read = 0;

            while ((len = in.read(buff, 0, Math.min(buff_size, filesize - read))) > 0) {
                read += len;

                fos.write(buff, 0, len);

                System.out.println(((float) read / filesize) * 100 + "% done...");

                buff = new byte[buff_size];
            }

            fos.close();

            in.read();
            in.read();

            byte[] endm = new byte[6];
            in.read(endm, 0, 6);

            if (new String(endm).trim().equals("ENDL")) {
                System.out.println("File downloaded successfully !");
            } else {
                System.out.println("Something went wrong while downloading the file...");
            }
        } else {
            System.out.println("Unknow file");
        }

        socket.close();
    }

    private void help() {
        System.out.println("List of every commands : ");
        for (int i = 0; i < cmdList.length; i++) {
            System.out.println("- " + cmdList[i]);
        }
    }

    public static void main(String[] args) {
        Client c = new Client();
        c.start();
    }
}