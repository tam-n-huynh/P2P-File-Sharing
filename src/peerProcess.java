import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

public class peerProcess {

    // Peer Info for THIS peer process
    private static int peerID;
    private static String hostName;
    private static int listeningPort;
    private static boolean hasFile;

    // List of all peers from PeerInfo
    private static final List<PeerInfo> allPeers = new ArrayList<>();

    // Common Configs
    private static int numPreferredNeighbors;
    private static int optimisticUnchokingInterval;
    private static int pieceSize;
    private static String fileName;
    private static int unchokingInterval;
    private static int fileSize;

    public peerProcess(int peerID) {
        this.peerID = peerID;

        // Load PeerInfo
        loadPeerInfo("PeerInfo.cfg");
        setCurrentPeerInfo(peerID);

        // Load CommonConfig
        Map<String, String> commonConfig = loadConfiguration("Common.cfg");

        // Parse and store configuration parameters
        this.numPreferredNeighbors = Integer.parseInt(commonConfig.get("NumberOfPreferredNeighbors"));
        this.optimisticUnchokingInterval = Integer.parseInt(commonConfig.get("OptimisticUnchokingInterval"));
        this.pieceSize = Integer.parseInt(commonConfig.get("PieceSize"));
        this.fileName = commonConfig.get("FileName");
        this.unchokingInterval = Integer.parseInt(commonConfig.get("UnchokingInterval"));
        this.fileSize = Integer.parseInt(commonConfig.get("FileSize"));
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java peerProcess <peerID>");
            System.exit(1);
        }

        int peerID = Integer.parseInt(args[0]);
        peerProcess process = new peerProcess(peerID);
        // Now, the peerProcess has its own information set, and you can proceed to use it
        System.out.println("This peer's ID: " + process.peerID + ", Host: " + process.hostName + ", Port: " + process.listeningPort + ", Has File: " + process.hasFile);

        process.connectToPreviousPeers();
        process.startServer();

    }

    private static Map<String, String> loadConfiguration(String filePath) {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    config.put(parts[0], parts[1]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return config;
    }

    private static void loadPeerInfo(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 4) {
                    int id = Integer.parseInt(parts[0]);
                    String hostname = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    boolean file = Integer.parseInt(parts[3]) == 1;

                    PeerInfo peerInfo = new PeerInfo(id, hostname, port, file);
                    // Add the peer information into our list.
                    allPeers.add(peerInfo);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setCurrentPeerInfo(int currentPeerID) {
        for (PeerInfo peerInfo : allPeers) {
            if (peerInfo.peerID == currentPeerID) {
                System.out.println(peerInfo.peerID);
                peerID = peerInfo.peerID;
                hostName = peerInfo.hostName;
                listeningPort = peerInfo.listeningPort;
                hasFile = peerInfo.hasFile;
                break;
            } else {
                // ADD IT TO SET OF PORTS TO CONNECT TO
            }
        }
    }

    private static void connectToPreviousPeers() {
        for (PeerInfo peer : allPeers) {
            // This ensures that all previous will be connected to
            if (peer.peerID != peerID) {
                try {
                    System.out.println("Attempting to connect to peer " + peer.peerID + " at " + peer.hostName + ":" + peer.listeningPort);
                    Socket socket = new Socket(peer.hostName, peer.listeningPort);

                    // Connection Established
                    System.out.println("Connected to peer " + peer.peerID);

                    // Send handshake after establishing connection
                    HandshakeMessage.sendHandshake(socket, peerID);
                } catch (IOException e) {
                    System.out.println("Could not connect to peer " + peer.peerID + ":" + peer.listeningPort);
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
                System.out.println("Listening for incoming connections on port " + listeningPort);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Connection established with peer: " + clientSocket.getInetAddress().getHostAddress());

                    // Immediately try to receive and validate the handshake
                    int connectedPeerID = HandshakeMessage.receiveAndValidateHandshake(clientSocket, allPeers);
                    if (connectedPeerID != -1) {
                        System.out.println("Handshake received successfully from " + connectedPeerID);
                    } else {
                        System.out.println("Handshake failed.");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

}