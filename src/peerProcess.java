import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.stream.IntStream;

public class peerProcess {
    // Peer Info for THIS peer process
    private static int peerID;
    private static String hostName;
    private static int listeningPort;
    private static boolean hasFile;
    private BitSet bitfield;
    private int numPieces;
    private Map<Integer, Neighbor> neighbors = new HashMap<>(); // Used to maintain neighbors that are CONNECTED
    private Map<Integer, byte[]> filePieces = new HashMap<>();

    // List of all peers read from PeerInfo
    // contains SELF in the arrayList as well
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
        // Setting current peer
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
            initializeFilePieces();
            for (int i = 0; i < this.numPieces; i++) {
                bitfield.set(i); // turn all the bits to 1 if peer has file
            }
        }
    }

    private void initializeFilePieces() {
        File file = new File(this.fileName);
        try (FileInputStream fis = new FileInputStream(file)) {
            long fileSize = file.length();
            int numberOfPieces = (int) Math.ceil((double) fileSize / this.pieceSize);

            for (int pieceIndex = 0; pieceIndex < numberOfPieces; pieceIndex++) {
                int currentPieceSize = this.pieceSize;
                if (pieceIndex == numberOfPieces - 1) { // Last piece might be smaller
                    currentPieceSize = (int) (fileSize - (long) this.pieceSize * pieceIndex);
                }

                byte[] piece = new byte[currentPieceSize];
                fis.read(piece, 0, currentPieceSize);
                this.filePieces.put(pieceIndex, piece);
            }
            System.out.println("File pieces initialized successfully.");
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + this.fileName);
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error reading the file: " + this.fileName);
            e.printStackTrace();
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

    // Reading config
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

    // Reading peer info
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

    // Setting process peer info
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

    // Set up the directory
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

    // HANDLE FUNCTIONS FOR RECEIVING MESSAGES
    private void handleChoke(int peerID) {
        // Mark the peer as choking current peer
        Neighbor neighbor = neighbors.get(peerID);

        if (neighbor != null) {
            neighbor.setChoked(true);
            System.out.println("Peer " + peerID + " is choking us.");
        }
    }

    private void handleUnchoke(int peerID) {
        Neighbor unchokingNeighbor = neighbors.get(peerID);
        Random random = new Random();

        if (unchokingNeighbor != null) {
            unchokingNeighbor.setChoked(false);
            System.out.println("Peer " + peerID + " is unchoking us.");

            // Determine the pieces this peer needs that the unchoking neighbor has
            BitSet neededPieces = (BitSet) bitfield.clone();
            neededPieces.flip(0, numPieces); // Flip the bits to represent needed pieces
            neededPieces.and(unchokingNeighbor.getPieces()); // AND with the pieces the neighbor has

            if (!neededPieces.isEmpty()) {
                // Convert BitSet to an IntStream, then to an array
                int[] neededPiecesArray = IntStream.range(0, neededPieces.length())
                        .filter(neededPieces::get)
                        .toArray();
                // Select a random piece from the array
                int randomPieceIndex = neededPiecesArray[random.nextInt(neededPiecesArray.length)];

                // Send request for this randomly selected piece
                try {
                    sendRequestMessage(unchokingNeighbor, randomPieceIndex);
                } catch (IOException e) {
                    System.out.println("Failed to send request message to " + peerID);
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("Received unchoke from unknown peer: " + peerID);
        }
    }



    private void handleInterested(int peerID) {
        // Mark the peer as interested
        Neighbor neighbor = neighbors.get(peerID);

        if (neighbor != null) {
            neighbor.setInterested(true);
            System.out.println("Peer " + peerID + " is interested in us.");
        }
    }

    private void handleNotInterested(int peerID) {
        // Mark the peer as not interested
        Neighbor neighbor = neighbors.get(peerID);

        if (neighbor != null) {
            neighbor.setInterested(false);
            System.out.println("Peer " + peerID + " is not interested in us anymore.");
        }
    }

    private void handleHave(int peerID, int pieceIndex) {
        Neighbor neighbor = neighbors.get(peerID);

        if (neighbor != null) {
            BitSet newPieces = neighbor.getPieces();
            newPieces.set(pieceIndex);
            neighbor.updatePieces(newPieces);

            System.out.println("Peer " + peerID + " has piece " + pieceIndex);

            if (!this.bitfield.get(pieceIndex)) {
                // We do not have this piece, so send an interested message.
                sendInterestedMessage(neighbor);
            }
        }
    }

    private void handleRequest(int peerID, int pieceIndex) throws IOException {
        Neighbor neighbor = neighbors.get(peerID);
        if (neighbor != null && !neighbor.isChoked() && filePieces.containsKey(pieceIndex)) {
            Socket peerSocket = neighbor.getSocket();
            byte[] pieceContent = filePieces.get(pieceIndex);

            // Construct and send the piece message
            sendPieceMessage(neighbor, pieceIndex, pieceContent);
        } else {
            System.out.println("Request for piece " + pieceIndex + " by peer " + peerID + " cannot be fulfilled.");
        }
    }

    private void handlePiece(int pieceIndex, byte[] pieceContent, int senderPeerID) throws IOException {
        // Store the received piece in memory
        filePieces.put(pieceIndex, pieceContent);

        // Update local bitfield
        bitfield.set(pieceIndex);
        System.out.println("Received piece " + pieceIndex + " from peer " + senderPeerID);

        // Broadcast "have" message to all peers
        sendHaveMessage(pieceIndex);

        // Check if still interested in sender
        Neighbor senderNeighbor = neighbors.get(senderPeerID);
        if (senderNeighbor != null) {
            if (!isInterestedIn(senderNeighbor)) {
                sendNotInterestedMessage(senderNeighbor);
                System.out.println("Not interested in peer " + senderPeerID + " anymore.");
            }
        }

        // Check for completion and assemble file if done
        if (isDownloadComplete()) {
            assembleFile();
        }
    }

    private boolean isDownloadComplete() {
        return bitfield.cardinality() == numPieces; // Check if all pieces are received
    }

    private void assembleFile() throws IOException {
        String directoryPath = "peer_" + peerID; // Directory name
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            directory.mkdirs(); // Create directory if it doesn't exist
        }

        // Construct the path for the assembled file within the directory
        File assembledFile = new File(directory, fileName);

        try (FileOutputStream fos = new FileOutputStream(assembledFile)) {
            for (int i = 0; i < numPieces; i++) {
                byte[] piece = filePieces.get(i);
                if (piece != null) {
                    fos.write(piece);
                } else {
                    System.err.println("Missing piece " + i + ", cannot assemble the file.");
                    return;
                }
            }
        }
        System.out.println("File assembled successfully in " + assembledFile.getAbsolutePath());
        hasFile = true; // Set hasFile to true as the file is now assembled successfully
    }

    private boolean isInterestedIn(Neighbor neighbor) {
        BitSet neighborPieces = neighbor.getPieces();
        BitSet interestingPieces = (BitSet) neighborPieces.clone();
        interestingPieces.andNot(bitfield); // Find pieces neighbor has that we don't
        return !interestingPieces.isEmpty();
    }


    // SEND MESSAGES FOR BROADCASTING
    private void sendChokeMessage(Neighbor neighbor) {
        try {
            Message chokeMessage = new Message(MessageType.CHOKE);
            sendMessage(neighbor.getSocket(), chokeMessage);
            System.out.println("Sent Choke message to peer " + neighbor.getPeerID());
        } catch (IOException e) {
            System.out.println("Error sending Choke message to peer " + neighbor.getPeerID());
            e.printStackTrace();
        }
    }

    private void sendUnchokeMessage(Neighbor neighbor) {
        try {
            Message unchokeMessage = new Message(MessageType.UNCHOKE);
            sendMessage(neighbor.getSocket(), unchokeMessage);
            System.out.println("Sent Unchoke message to peer " + neighbor.getPeerID());
        } catch (IOException e) {
            System.out.println("Error sending Unchoke message to peer " + neighbor.getPeerID());
            e.printStackTrace();
        }
    }

    private void sendInterestedMessage(Neighbor neighbor) {
        try {
            Message interestedMessage = new Message(MessageType.INTERESTED);
            sendMessage(neighbor.getSocket(), interestedMessage);
            System.out.println("Sent Interested message to peer " + neighbor.getPeerID());
        } catch (IOException e) {
            System.out.println("Error sending Interested message to peer " + neighbor.getPeerID());
            e.printStackTrace();
        }
    }

    private void sendNotInterestedMessage(Neighbor neighbor) {
        try {
            Message notInterestedMessage = new Message(MessageType.NOT_INTERESTED);
            sendMessage(neighbor.getSocket(), notInterestedMessage);
            System.out.println("Sent Not Interested message to peer " + neighbor.getPeerID());
        } catch (IOException e) {
            System.out.println("Error sending Not Interested message to peer " + neighbor.getPeerID());
            e.printStackTrace();
        }
    }

    private void sendRequestMessage(Neighbor neighbor, int pieceIndex) throws IOException {
        // Construct the request message payload with the piece index
        ByteBuffer buffer = ByteBuffer.allocate(4); // Size of an integer
        buffer.putInt(pieceIndex);
        byte[] payload = buffer.array();

        // Create and send the request message
        Message requestMessage = new Message(MessageType.REQUEST, payload);
        sendMessage(neighbor.getSocket(), requestMessage);
        System.out.println("Sent REQUEST message for piece " + pieceIndex + " to peer " + neighbor.getPeerID());
    }


    private void sendPieceMessage(Neighbor neighbor, int pieceIndex, byte[] pieceContent) {
        try {
            // Initialize a ByteArrayOutputStream to hold the message content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Write the piece index followed by the piece content to the ByteArrayOutputStream
            dos.writeInt(pieceIndex);
            dos.write(pieceContent);

            // Convert the ByteArrayOutputStream to a byte array, which will be the payload
            byte[] payload = baos.toByteArray();

            // Create a new PIECE message with the constructed payload
            Message pieceMessage = new Message(MessageType.PIECE, payload);

            // Send the PIECE message to the specified neighbor
            sendMessage(neighbor.getSocket(), pieceMessage);
            System.out.println("Sent PIECE message for piece " + pieceIndex + " to peer " + neighbor.getPeerID());
        } catch (IOException e) {
            // Log any IOException that occurs during message creation or sending
            System.out.println("Error sending PIECE message for piece " + pieceIndex + " to peer " + neighbor.getPeerID());
            e.printStackTrace();
        }
    }


    // Function doesn't take a neighbor param because it needs to send the message to ALL neighbors
    private void sendHaveMessage(int pieceIndex) {
        // Convert pieceIndex to byte array
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);
        byte[] payload = buffer.array();

        // Broadcast to all neighbors
        neighbors.values().forEach(neighbor -> {
            try {
                Message haveMessage = new Message(MessageType.HAVE, payload);
                sendMessage(neighbor.getSocket(), haveMessage);
                System.out.println("Broadcasted have for piece " + pieceIndex + " to peer " + neighbor.getPeerID());
            } catch (IOException e) {
                System.out.println("Failed to send have message to " + neighbor.getPeerID());
                e.printStackTrace();
            }
        });
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
                            // handle CHOKE
                            handleChoke(peerID);
                            break;
                        case MessageType.UNCHOKE:
                            // handle UNCHOKE
                            handleUnchoke(peerID);
                            break;
                        case MessageType.INTERESTED:
                            // handle INTERESTED
                            handleInterested(peerID);
                            break;
                        case MessageType.NOT_INTERESTED:
                            // handle NOT INTERESTED
                            handleNotInterested(peerID);
                            break;
                        case MessageType.HAVE:
                            // handle HAVE
                            // Extract the piece index from the payload of the HAVE message
                            ByteBuffer wrappedHave = ByteBuffer.wrap(receivedMessage.getPayload());
                            int havePieceIndex = wrappedHave.getInt();
                            handleHave(peerID, havePieceIndex);
                            break;
                        case MessageType.REQUEST:
                            // handle REQUEST
                            ByteBuffer wrapped = ByteBuffer.wrap(receivedMessage.getPayload());
                            int requestedPieceIndex = wrapped.getInt();
                            handleRequest(peerID, requestedPieceIndex);
                            break;
                        case MessageType.PIECE:
                            // handle PIECE
                            ByteBuffer pieceBuffer = ByteBuffer.wrap(receivedMessage.getPayload());
                            int pieceIndex = pieceBuffer.getInt();
                            byte[] pieceContent = new byte[pieceBuffer.remaining()];
                            pieceBuffer.get(pieceContent); // extract the piece content

                            handlePiece(pieceIndex, pieceContent, peerID);
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

    private void addNeighbor(int peerID, Socket socket) {
        // Check if the neighbor already exists to avoid duplication
        if (!neighbors.containsKey(peerID)) {
            neighbors.put(peerID, new Neighbor(peerID, socket));
            System.out.println("Neighbor added: " + peerID);
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        try {
            // First, validate the incoming handshake
            int connectedPeerID = HandshakeMessage.receiveAndValidateHandshake(clientSocket, allPeers);

            if (connectedPeerID != -1) {
                System.out.println("Handshake received successfully from " + connectedPeerID);

                // Send a handshake message back to complete the handshake exchange
                HandshakeMessage.sendHandshake(clientSocket, peerID);
                addNeighbor(connectedPeerID, clientSocket);

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


    // MAIN CODE FOR HANDLING THE LOGIC OF SENDING MESSAGES AND GETTING PIECES.
    // Next Step: Scheduler for choking and unchoking neighbors.
}