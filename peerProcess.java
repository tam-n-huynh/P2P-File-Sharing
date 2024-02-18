import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    public static Map<String, String> loadCommonConfig(String filePath) throws Exception {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    config.put(parts[0], parts[1]);
                }
            }
        }
        return config;
    }
}

public class peerProcess {
    private int peerId;
    private String hostName;
    private int portNumber;
    private int haveFile;
    private int[] bitfield;
    private int numPieces = 0;
    public static void main(Stringp[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java peerProcess <peerID>");
            System.exit(1);
        }

        String peerID = args[0];

        try {
            // Load common configuration
            Map<String, String> commonConfig = ConfigLoader.loadCommonConfig("Common.cfg");
            commonConfig.forEach((key, value) -> System.out.println(key + ": " + value));

            // Initiliazion based on comonConfig
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}