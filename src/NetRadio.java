public class NetRadio {
    public static final int NUMMESS = 4;
    public static final int ID = 8;
    public static final int MESS = 140;
    public static final int NBMESS = 3;
    public static final int IP = 15;
    public static final int PORT = 4;
    public static final int NUMDIFF = 2;

    private NetRadio() {
    }

    public static String normalizeIp(String ip) {
        String[] parts = ip.split("\\.");
        String formattedIp = "";
        for (int i = 0; i < parts.length; i++) {
            while (parts[i].length() < 3) {
                parts[i] = '0' + parts[i];
            }
            if (i != 0)
                formattedIp += '.';
            formattedIp += parts[i];
        }
        return formattedIp;
    }

    public static String fillWithSharp(String s, int requieredSize) {
        if (s.length() > requieredSize)
            return s.substring(0, requieredSize);
        while (s.length() != requieredSize)
            s += '#';
        return s;
    }

    public static String fillWithZero(int x, int requieredSize) {
        String s = String.valueOf(x);
        while (s.length() != requieredSize)
            s = '0' + s;
        return s;
    }
}
