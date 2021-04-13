import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class FileLoader {
    private FileLoader() {
    }

    // Load un fichier de message avec 1 message par ligne
    public static String[] loadMessages(String filename) throws IOException {
        // On lit le fichier
        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        String line = reader.readLine();
        // On compte le nombre de ligne
        int size = 0;
        while (line != null) {
            size++;
            line = reader.readLine();
        }
        reader.close();
        // Puis on relit le fichier, en stockant les lignes cette fois ci
        String[] tab = new String[size];
        reader = new BufferedReader(new FileReader(new File(filename)));
        line = reader.readLine();
        int index = 0;
        while (line != null) {
            // On coupe a 140 caractères au cas ou
            tab[index++] = line.substring(0, Math.min(140, line.length()));
            line = reader.readLine();
        }
        return tab;
    }

    // Charge un fichier de settings au format 'key:value', une par ligne
    public static Map<String, String> loadSettings(String filename) throws IOException {
        HashMap<String, String> settings = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        String line = reader.readLine();
        while (line != null) {
            String key = line.substring(0, line.indexOf(':'));
            String value = line.substring(line.indexOf(':') + 1, line.length());
            settings.put(key, value);
            line = reader.readLine();
        }
        reader.close();
        return settings;
    }
}
