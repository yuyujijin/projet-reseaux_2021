import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Gestionnaire{
    private final int MAX_DIFFUSEUR;
    private int NBR_DIFFUSEUR;
    private final String[] diffuseurs;
    private final int port;

    public Gestionnaire(int port, int maxDiffuseurs){
        MAX_DIFFUSEUR = maxDiffuseurs;
        NBR_DIFFUSEUR = 0;
        this.diffuseurs = new String[MAX_DIFFUSEUR];
        this.port = port;
    }

    public boolean addGestionaire(String s){
        // s : id_ip1_port1_ip2_port2
        if(NBR_DIFFUSEUR >= MAX_DIFFUSEUR) return false;
        diffuseurs[NBR_DIFFUSEUR++] = s;
        return true;
    }

    public void start() throws IOException{
        // On boucle en TCP en attendant des connexions
        ServerSocket server = new ServerSocket(port);
        System.out.println("GESTIONNAIRE : JE ME LANCE SUR LE PORT " + port);
        while(true){
            Socket socket = server.accept();
            System.out.println("CONNEXION ENTRANTE DE " + socket.getInetAddress().toString());
            new Thread(() -> {
                try(BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))){
                    char[] command = new char[4];
                    reader.read(command, 0, 4);
                    switch(String.valueOf(command)){
                        case "LIST" : workWithClient(socket); break;
                        case "REGI" : workWithDiffuseur(reader,socket); break;
                        default : System.out.println("Je n'ai pas compris cette commande..."); socket.close(); break;
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public void workWithClient(Socket socket) throws IOException{
        System.out.println("JE LANCE MA ROUTINE DE CLIENT AVEC " + socket.getInetAddress().toString());
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        // On commence par envoyer le nombre de diffuseurs
        // le nombre de diffuseur doit être envoyé sur 2 octets, on ajoute donc un 0 si requis
        String linb = "LINB " + ((NBR_DIFFUSEUR >= 10)? "" : "0") + NBR_DIFFUSEUR;
        writer.print(linb);
        writer.flush();

        // Puis on envoie l'item pour chaque diffuseur
        for(int i = 0; i < NBR_DIFFUSEUR; i++){
            // ITEM id ip1 port1 ip2 port2
            // id : 8 octets, ip1/2 : 15 octets, port1/2 : 4 octets
            String item = "ITEM " + diffuseurs[i];
            writer.print(item);
            writer.flush();
        }
        System.out.println("MA ROUTINE AVEC " + socket.getInetAddress().toString() + " EST FINI, JE TERMINE LA CONNEXION");
        // Et on ferme la connexion
        socket.close();
    }

    public void workWithDiffuseur(BufferedReader reader, Socket socket) throws IOException{
        System.out.println("JE LANCE MA ROUTINE D'ENREGISTREMENT DE DIFFUSEUR AVEC " + socket.getInetAddress().toString());
        // On saute l'espace
        reader.read();
        // Puis on récupère le message
        int length = 8 + 1 + 15 + 1 + 4 + 1 + 15 + 1 + 4;
        char[] diffuseur = new char[length];
        System.out.println();
        if(reader.read(diffuseur, 0, length) != length){ 
            System.out.println("ERREUR DE FORMATAGE DANS L'ENREGISTREMENT DE " + socket.getInetAddress().toString() + ". JE FERME LA CONNEXION");
            socket.close(); return; 
        }
        // On tente de l'ajouter
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        if(addGestionaire(String.valueOf(diffuseur))){
            System.out.println("L'ENREGISTREMENT DE " + socket.getInetAddress().toString() + " A ETE EFFECTUE AVEC SUCCES.");
            writer.print("REOK");
            writer.flush();
        }else{
            System.out.println("L'ENREGISTREMENT DE " + socket.getInetAddress().toString() + " A ECHOUE.");
            writer.print("RENO");
            writer.flush();
            socket.close();
        }
    }

    public static void main(String[] args){
        Gestionnaire g = new Gestionnaire(Integer.valueOf(args[0]), 10);
        try{
            g.start();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}