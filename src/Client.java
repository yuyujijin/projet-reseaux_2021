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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public final class Client {
    private static final String[] cmdList = {
            "'LISTEN' : Starts the routine that make the client starts listening to a specified diffusor." };

    private Client() {
    }

    private void start() throws UnknownHostException, IOException {
        Scanner s = new Scanner(System.in);
        while (true) {
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
            case "exit":
                return;
            default:
                System.out.println(
                        "Unknown command \"" + cmd + "\". Type \"HELP\" to get a list of every possible commands.");
                break;
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
        frame.setVisible(true);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                System.out.println("Stopped listening to " + ip + ":" + port);
                try {
                    mso.leaveGroup(InetAddress.getByName(ip));
                } catch (Exception e) {
                    e.printStackTrace();
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
                int length = 4 + 1 + NetRadio.NUMMESS + 1 + NetRadio.ID + NetRadio.MESS + 2;
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
                e.printStackTrace();
            }
        }).start();
    }

    private void list(Scanner s) throws UnknownHostException, IOException {
        String[] args = ipAndPort(s);
        String ip = args[0];
        int port = Integer.valueOf(args[1]);

        Socket socket = new Socket(ip, port);

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        pw.print("LIST\r\n");
        pw.flush();

        char[] nbr = new char[4 + NetRadio.NUMDIFF + 2];
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        br.read(nbr, 0, 4 + NetRadio.NUMDIFF + 2);

        int n = Integer.valueOf(String.valueOf(nbr).strip().split(" ")[1]);
        System.out.print(n + " diffusor" + ((n > 1) ? "s are" : " is") + " registered here.");
        if (n > 0) {
            System.out.println(" Here is the list of every of them :");
            System.out.printf("%-5s %-9s %-16s %-5s %-16s %-5s%n", "#", "ID", "IP1", "PORT1", "IP2", "PORT2");
        } else
            System.out.println();
        for (int i = 0; i < n; i++) {
            int length = 4 + 1 + NetRadio.ID + 1 + NetRadio.IP + 1 + NetRadio.PORT + 1 + NetRadio.IP + 1 + NetRadio.PORT
                    + 2;

            char[] item = new char[length];

            br.read(item, 0, length);

            String[] elems = String.valueOf(item).split(" ");
            System.out.printf("%-5d %-9s %-16s %-5s %-16s %-5s%n", i + 1, elems[1], elems[2], elems[3], elems[4],
                    elems[5]);
        }

        socket.close();
    }

    private void help() {
        System.out.println("List of every commands : ");
        for (int i = 0; i < cmdList.length; i++) {
            System.out.println("- " + cmdList[i]);
        }
    }

    public static void main(String[] args) throws UnknownHostException, IOException {
        Client c = new Client();
        c.start();
    }
}