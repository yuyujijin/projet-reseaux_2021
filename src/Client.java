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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public final class Client {
    private static final String[] cmdList = { "'LISTEN' : Begin listening to a specified diffusor.",
            "'LIST' : Ask for a list of diffusor to a diffusor manager.", "'exit' : Leaves the client.",
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
                case "HELP":
                    help();
                    break;
                case "MESS":
                    mess(s);
                    break;
                case "LAST":
                    last(s);
                    break;
                case "exit":
                    return;
                default:
                    System.out.println(
                            "Unknown command \"" + cmd + "\". Type \"HELP\" to get a list of every possible commands.");
                    break;
                }
            } catch (Exception e) {
                System.out.println("An error as occured : " + e.getMessage());
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

    private String removeSharp(String s) {
        int count = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != '#')
                break;
            count++;
        }
        return s.substring(0, s.length() - count);
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
                    String formatted = removeSharp(st.substring(0, st.length() - 2));
                    pane.setText(pane.getText() + "[" + time + "] " + formatted + "\n");
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
        System.out.println("last");
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

                System.out.println(removeSharp(String.valueOf(item).strip()));

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