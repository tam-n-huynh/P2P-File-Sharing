public class PeerInfo {
    public int peerID;
    public String hostName;
    public int listeningPort;
    public boolean hasFile;

    public PeerInfo(int peerID, String hostName, int listeningPort, boolean hasFile) {
        this.peerID = peerID;
        this.hostName = hostName;
        this.listeningPort = listeningPort;
        this.hasFile = hasFile;
    }
}