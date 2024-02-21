import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class HandshakeMessage {
    private static final String HEADER = "P2PFILESHARINGPROJ";
    private static final int ZERO_BITS_LENGTH = 10; // 10 zero bytes

    public static byte[] createHandshakeMessage(int peerID) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(HEADER.getBytes());
            baos.write(new byte[ZERO_BITS_LENGTH]); // Write 10 zero bytes
            baos.write(ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(peerID).array());
            return baos.toByteArray();
        } catch (IOException e) {
            // This should never happen with a ByteArrayOutputStream
            throw new RuntimeException(e);
        }
    }

    public static void sendHandshake(Socket socket, int peerID) throws IOException {
        byte[] handshakeMsg = createHandshakeMessage(peerID);
        System.out.println("Sending handshake to " + socket.getInetAddress() + ": " + Arrays.toString(handshakeMsg));
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.write(handshakeMsg);
        dos.flush();
    }

    public static int receiveAndValidateHandshake(Socket socket, List<PeerInfo> allPeers) throws IOException {
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        byte[] receivedMsg = new byte[32];
        dis.readFully(receivedMsg);

        String header = new String(receivedMsg, 0, 18); // Firest 18 Bytes for header
        int peerID = ByteBuffer.wrap(receivedMsg, 28, 4).getInt();

        boolean isHeaderValid = HEADER.equals(header);
        boolean isPeerKnown = allPeers.stream().anyMatch(p -> p.peerID == peerID);

        if (isHeaderValid && isPeerKnown) {
            //System.out.println("Handshake received successfully from " + peerID);
            return peerID; // Return the validated peerID
        } else {
            System.out.println("Handshake failed.");
            return -1;
        }
    }
}