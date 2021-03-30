import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class MessageLoader {
    private MessageLoader(){ }

    public static String[] loadMessages(String filename) throws IOException{
        // On lit le fichier
        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        String line = reader.readLine();
        // On compte le nombre de ligne
        int size = 0;
        while(line != null){
            size++;
            line = reader.readLine();
        }
        reader.close();
        // Puis on relit le fichier, en stockant les lignes cette fois ci
        String[] tab = new String[size];
        reader = new BufferedReader(new FileReader(new File(filename)));
        line = reader.readLine();
        int index = 0;
        while(line != null){
            // On coupe a 140 caractères au cas ou
            tab[index++] = line.substring(0, Math.min(140, line.length()));
            line = reader.readLine();
        }
        return tab;
    }
}
