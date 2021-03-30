import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class Gestionnaire{
    private final int MAX_DIFFUSEUR;
    private int NBR_DIFFUSEUR;
    private final ArrayList<String> diffuseurs;
    private final int port;

    public static int CHECK_CONNECTED_TIME = 10000;
    public static int TIMEOUT_TIME = 3000;

    public Gestionnaire(int port, int maxDiffuseurs){
        MAX_DIFFUSEUR = maxDiffuseurs;
        NBR_DIFFUSEUR = 0;
        this.diffuseurs = new ArrayList<String>();
        this.port = port;
    }

    // TODO...
    // gérer les accès a concurrence a la liste des diffuseurs
    public boolean addGestionaire(String s){
        // s : id_ip1_port1_ip2_port2
        if(NBR_DIFFUSEUR >= MAX_DIFFUSEUR) return false;
        diffuseurs.add(s);
        NBR_DIFFUSEUR++;
        return true;
    }

    public boolean removeGestionaire(String s){
        NBR_DIFFUSEUR--;
        return diffuseurs.remove(s);
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
            String item = "ITEM " + diffuseurs.get(i);
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

            diffuseurRoutine(socket, String.valueOf(diffuseur));
        }else{
            System.out.println("L'ENREGISTREMENT DE " + socket.getInetAddress().toString() + " A ECHOUE.");
            writer.print("RENO");
            writer.flush();
            socket.close();
        }
    }

    public void diffuseurRoutine(Socket socket, String diffuseur_identifier) throws IOException{
        try(PrintWriter pr = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))){
            // On met le temps d'attente
            socket.setSoTimeout(TIMEOUT_TIME);
            while(true){
                // On attend le temps de vérification de connexion
                Thread.sleep(CHECK_CONNECTED_TIME);
                System.out.println("JE VERIFIE SI " + socket.getInetAddress().toString() + " EST TOUJOURS ACTIF");

                pr.print("RUOK");
                pr.flush();

                char[] resp = new char[4];
                br.read(resp, 0, 4);

                // Si la réponse est mauvaise
                if(!String.valueOf(resp).equals("IMOK")){
                    System.out.println("FERMETURE DE LA CONNEXION AVEC LE DIFFUSEUR " + socket.getInetAddress().toString());
                    removeGestionaire(diffuseur_identifier);
                    socket.close();
                    return;
                }

                System.out.println(socket.getInetAddress().toString() + " EST TOUJOURS ACTIF");
            }   
        }catch(SocketTimeoutException e){
            System.out.println("TEMPS D'ATTENTE POUR " + socket.getInetAddress().toString() + " ECOULE");
            socket.close();
        }catch(InterruptedException e){
            e.printStackTrace();
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