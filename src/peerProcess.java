import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;

public class peerProcess {

    // Peer Info for THIS peer process
    private static int peerID;
    private static String hostName;
    private static int listeningPort;
    private static boolean hasFile;
    private BitSet bitfield;
    private int numPieces;

    // List of all peers read from PeerInfo
    private static final List<PeerInfo> allPeers = new ArrayList<>();

    // Common Configs
    private static int numPreferredNeighbors;
    private static int optimisticUnchokingInterval;
    private static int pieceSize;
    private static String fileName;
    private static int unchokingInterval;
    private static int fileSize;

    // Countdown latch used to ensure StartServer is ran before connct to previous peers for concurrency issues.
    private final CountDownLatch latch = new CountDownLatch(1);


    private static FileWriter logWriter;

    public peerProcess(int peerID) { // Constructor
        this.peerID = peerID;

        // Load PeerInfo
        loadPeerInfo("PeerInfo.cfg");
        setCurrentPeerInfo(peerID);

        // Load CommonConfig
        Map<String, String> commonConfig = loadConfiguration("Common.cfg");

        // Parse and store config parameters
        this.numPreferredNeighbors = Integer.parseInt(commonConfig.get("NumberOfPreferredNeighbors"));
        this.optimisticUnchokingInterval = Integer.parseInt(commonConfig.get("OptimisticUnchokingInterval"));
        this.pieceSize = Integer.parseInt(commonConfig.get("PieceSize"));
        this.fileName = commonConfig.get("FileName");
        this.unchokingInterval = Integer.parseInt(commonConfig.get("UnchokingInterval"));
        this.fileSize = Integer.parseInt(commonConfig.get("FileSize"));

        // Calculate the number of pieces
        this.numPieces = (int) Math.ceil((double) fileSize / pieceSize);

        // Initialize the bitfield
        this.bitfield = new BitSet(numPieces); // Initializes all at 0

        if (hasFile) {
            for (int i = 0; i < this.numPieces; i++) {
                bitfield.set(i); // turn all the bits to 1 if peer has file
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java peerProcess <peerID>");
            System.exit(1);
        }

        int peerID = Integer.parseInt(args[0]);
        setUpDirectory(peerID);
        setUpLogFile(peerID);

        // After the log is created, shut down the logWriter
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (logWriter != null) {
                    System.out.println("Closing log writer...");
                    logWriter.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));


        peerProcess process = new peerProcess(peerID);
        // Now, the peerProcess has its own information set, and you can proceed to use it
        System.out.println("This peer's ID: " + process.peerID + ", Host: " + process.hostName + ", Port: " + process.listeningPort + ", Has File: " + process.hasFile);

        process.startServer(); // Start listening to messages.
        process.connectToPreviousPeers(); // Connect to all previous peers before it in the list.
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

                    // Create a new peerInfo object about peers
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
        // We passed in the peerID to the constructor and read PeerInfo
        // but now we need to go through allpeers to find the info that corresponds to the current peer
        for (PeerInfo peerInfo : allPeers) {
            if (peerInfo.peerID == currentPeerID) {
                System.out.println(peerInfo.peerID);
                peerID = peerInfo.peerID;
                hostName = peerInfo.hostName;
                listeningPort = peerInfo.listeningPort;
                hasFile = peerInfo.hasFile;
                break;
            }
        }
    }

    private static void setUpDirectory(int peerID) {
        String dirName = "peer_" + peerID;
        File dir = new File(dirName);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("Directory created: " + dirName);
            } else {
                System.out.println("Failed to create directory: " + dirName);
            }
        }
    }

    private static void setUpLogFile(int peerID) {
        try {
            File logFile = new File("log_peer_" + peerID + ".log");
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            logWriter = new FileWriter(logFile, true); // Append mode
        } catch (IOException e) {
            System.out.println("Failed to create log file for peer " + peerID);
            e.printStackTrace();
        }
    }

    public static void log(String message) {
        try {
            logWriter.write(message + "\n");
            logWriter.flush();
        } catch (IOException e) {
            System.out.println("Failed to write to log file.");
            e.printStackTrace();
        }
    }

    private void connectToPreviousPeers() {
        try {
            latch.await();
            for (PeerInfo peer : allPeers) {
                // This ensures that all previous will be connected to
                if (peer.peerID != peerID) {
                    try {
                        System.out.println("Attempting to connect to peer " + peer.peerID + " at " + peer.hostName + ":" + peer.listeningPort);
                        Socket socket = new Socket(peer.hostName, peer.listeningPort);
                        System.out.println("Connected to peer " + peer.peerID);

                        // Send handshake and wait for response
                        boolean handshakeAcknowledged = HandshakeMessage.exchangeHandshake(socket, peerID, allPeers);
                        if (handshakeAcknowledged) {
                            // Proceed with sending bitfield and other messages
                            handlePeerCommunication(socket, peer.peerID);
                        } else {
                            System.out.println("Handshake failed with peer " + peer.peerID);
                            socket.close();
                        }
                    } catch (IOException e) {
                        System.out.println("Could not connect to peer " + peer.peerID + ":" + peer.listeningPort);
                        e.printStackTrace();
                    }
                } else {
                    // Once we find our Peer, this means we haven't seen the ones ahead, so break.
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void sendBitfieldMessage(Socket peerSocket, BitSet bitfield, int numPieces) throws IOException {
        Message bitfieldMessage = Message.createBitfieldMessage(bitfield, numPieces);
        sendMessage(peerSocket, bitfieldMessage);
    }

    public void sendMessage(Socket socket, Message message) throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        byte[] messageBytes = message.getBytes();
        dos.write(messageBytes);
        dos.flush(); // Ensure the message is sent immediately
    }

    public static Message receiveMessage(Socket socket) throws IOException {
        DataInputStream dis = new DataInputStream(socket.getInputStream());

        int messageLength = dis.readInt(); // Read message length 4 bytes
        System.out.println("testing");
        byte messageType = dis.readByte(); // Read message type 1 byte

        byte[] payload = null;
        if (messageLength > 1) {
            payload = new byte[messageLength - 1]; // minus 1 for the messageType byte
            dis.readFully(payload); // Read the payload
        }



        return new Message(messageType, payload);
    }

    private void handlePeerCommunication(Socket peerSocket, int peerID) {
        new Thread(() -> {
            try {
                // Initial setup Handshake was just approved! So send bitfield
                System.out.println("Trying to send bitfield");
                sendBitfieldMessage(peerSocket, bitfield, numPieces);
                System.out.println("Done sending Bitfield");

                // Loop to continuously listen for messages.
                while (true) {
                    Message receivedMessage = receiveMessage(peerSocket);
                    switch (receivedMessage.getType()) {
                        case MessageType.BITFIELD:
                            // handle bitfield
                            System.out.println("Received a bitfield message");
                            break;
                        case MessageType.CHOKE:
                            // handle bitfield
                            break;
                        case MessageType.UNCHOKE:
                            // handle bitfield
                            break;
                        case MessageType.INTERESTED:
                            // handle bitfield
                            break;
                        case MessageType.NOT_INTERESTED:
                            // handle bitfield
                            break;
                        case MessageType.HAVE:
                            // handle bitfield
                            break;
                        case MessageType.REQUEST:
                            // handle bitfield
                            break;
                        case MessageType.PIECE:
                            // handle bitfield
                            break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Lost connection with peer " + peerID);
            }
        }).start();
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
                System.out.println("Listening for incoming connections on port " + listeningPort);
                latch.countDown();
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Connection established with peer: " + clientSocket.getInetAddress().getHostAddress());

                    // Use a separate thread to handle each connection to prevent blocking server thread
                    new Thread(() -> handleClientConnection(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClientConnection(Socket clientSocket) {
        try {
            // First, validate the incoming handshake
            int connectedPeerID = HandshakeMessage.receiveAndValidateHandshake(clientSocket, allPeers);
            if (connectedPeerID != -1) {
                System.out.println("Handshake received successfully from " + connectedPeerID);

                // Send a handshake message back to complete the handshake exchange
                HandshakeMessage.sendHandshake(clientSocket, peerID);

                // After exchanging handshakes, proceed with further communication
                handlePeerCommunication(clientSocket, connectedPeerID);
            } else {
                System.out.println("Invalid handshake received. Closing connection.");
                clientSocket.close(); // Close connection if handshake is invalid
            }
        } catch (IOException e) {
            System.out.println("Error handling client connection: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}