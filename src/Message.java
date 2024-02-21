import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.Socket;

public class Message {
    private byte messageType;
    private byte[] payload;

    public Message(byte messageType, byte[] payload) {
        this.messageType = messageType;
        this.payload = payload;
    }

    public Message(byte messageType) {
        this.messageType = messageType;
        this.payload = null;
    }

    public byte getType() {
        return this.messageType;
    }

    public byte[] getBytes() { // Function used to convert message to bytes to send
        int messageLength = 1 + (payload != null ? payload.length : 0);
        ByteBuffer buffer = ByteBuffer.allocate(4 + messageLength);
        buffer.putInt(messageLength);
        buffer.put(messageType);
        if (payload != null) {
            buffer.put(payload);
        }
        return buffer.array();
    }

    // Method for creating all the individual messages
    // Parameters TBD
    public static Message createChokeMessage() {
        return new Message(MessageType.CHOKE, null);
    }

    public static Message createUnchokeMessage() {
        return new Message(MessageType.UNCHOKE, null);
    }

    public static Message createInterestedMessage() {
        return new Message(MessageType.INTERESTED, null);
    }

    public static Message createNotInterestedMessage() {
        return new Message(MessageType.NOT_INTERESTED, null);
    }

    public static Message createHaveMessage() {
        return new Message(MessageType.HAVE, null);
    }

    public static Message createBitfieldMessage() {
        return new Message(MessageType.BITFIELD, null);
    }

    public static Message createRequestMessage() {
        return new Message(MessageType.REQUEST, null);
    }

    public static Message createPieceMessage() {
        return new Message(MessageType.PIECE, null);
    }

}