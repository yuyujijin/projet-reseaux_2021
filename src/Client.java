import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Scanner;

public final class Client {
    private static final String[] cmdList = {
            "'LISTEN' : Starts the routine that make the client starts listening to a specified diffusor." };

    private Client() {
    }

    private void start() {
        Scanner s = new Scanner(System.in);
        while (true) {
            String cmd = s.nextLine();
            switch (cmd.strip()) {
            case "LISTEN":
                listen(s);
                break;
            case "HELP":
                help();
                break;
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
                while (true) {
                    mso.receive(paquet);
                    String st = new String(paquet.getData(), 0, paquet.getLength());
                    System.out.println(removeSharp(st.substring(0, st.length() - 2)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

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