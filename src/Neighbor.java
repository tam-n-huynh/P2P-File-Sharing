import java.net.Socket;
import java.util.BitSet;


public class Neighbor {
    private int peerID;
    private boolean isChoked;
    private boolean isInterested;
    private Socket socket;
    private BitSet pieces;

    public Neighbor(int peerID, Socket socket) {
        this.peerID = peerID;
        this.socket = socket;
        this.isChoked = true; // Default already choked
        this.isInterested = false; // default not interested
        this.pieces = new BitSet(); // Initialize bitset based on total known pieces
    }

    // Getters and Settings
    public int getPeerID() {
        return peerID;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isChoked() {
        return isChoked;
    }

    public void setChoked(boolean isChoked) {
        this.isChoked = isChoked;
    }

    public boolean isInterested() {
        return isInterested;
    }

    public void setInterested(boolean isInterested) {
        this.isInterested = isInterested;
    }

    public BitSet getPieces() {
        return (BitSet) pieces.clone();
    }

    public void updatePieces(BitSet newPieces) {
        this.pieces.or(newPieces);
    } // can't lose pieces (in our program)

}