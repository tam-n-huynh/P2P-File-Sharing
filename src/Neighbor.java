import java.net.Socket;
import java.util.BitSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;




public class Neighbor {
    private int peerID;
    private boolean isChoked; // Are WE choking this neighbor or not
    private boolean isInterested; // Is THIS PEER interested in us or not
    private Socket socket;
    private BitSet pieces;
    private long prevDownloadRate;
    private Set<Integer> requestedPieces; // Pieces we are requesting from THIS PEER
    private Map<Integer, ScheduledFuture<?>> requestTimeoutTasks = new HashMap<>();

    public Neighbor(int peerID, Socket socket) {
        this.peerID = peerID;
        this.socket = socket;
        this.isChoked = true; // Default already choked
        this.isInterested = false; // default not interested
        this.pieces = new BitSet(); // Initialize bitset based on total known pieces
        this.prevDownloadRate = 0; // Initialize download rate as 0 (never downloaded before)
        this.requestedPieces = new HashSet<>();
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

    public long getPrevDownloadRate() {
        return prevDownloadRate;
    }

    public void setPrevDownloadRate(long prevDownloadRate) {
        this.prevDownloadRate = prevDownloadRate;
    }

    public void addRequestedPiece(int pieceIndex) {
        this.requestedPieces.add(pieceIndex);
    }

    public void removeRequestedPiece(int pieceIndex) {
        this.requestedPieces.remove(pieceIndex);
    }

    public boolean hasRequestedPiece(int pieceIndex) {
        return this.requestedPieces.contains(pieceIndex);
    }

    public Set<Integer> getRequestedPieces() {
        return requestedPieces;
    }

    // Methods to add, check, and remove request timeouts...
    public void addRequestTimeoutTask(int pieceIndex, ScheduledFuture<?> timeoutTask) {
        requestTimeoutTasks.put(pieceIndex, timeoutTask);
    }

    public void cancelRequestTimeout(int pieceIndex) {
        ScheduledFuture<?> timeoutTask = requestTimeoutTasks.remove(pieceIndex);
        if (timeoutTask != null) {
            timeoutTask.cancel(true); // Cancel the task and interrupt if running
        }
    }
}