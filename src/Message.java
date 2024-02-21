import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.BitSet;

public class Message {
    private byte messageType;
    private byte[] payload;

    public Message(byte messageType, byte[] payload) {
        this.messageType = messageType;
        this.payload = payload;
    }

    public Message(byte messageType) { // No payload constructor
        this.messageType = messageType;
        this.payload = null;
    }

    public byte getType() {
        return this.messageType;
    }

    public byte[] getBytes() {
        // Function used to convert message to bytes to send

        // Calculate total length of message
        int totalLength = 1 + (payload != null ? payload.length : 0);

        // Allocate a buffer + 4 bytes for length
        ByteBuffer buffer = ByteBuffer.allocate(4 + totalLength);

        buffer.putInt(totalLength); // 4 byte message length
        buffer.put(messageType); // 1 byte message type

        if (payload != null) {
            buffer.put(payload); // remaining bytes
        }

        return buffer.array();
    }

    // Extraneous intermittent functions
    public static byte[] bitfieldToByteArray(BitSet bitfield, int numPieces) {
        // The length of the resulting byte array
        int numBytes = (int) Math.ceil(numPieces / 8.0);
        byte[] bytes = new byte[numBytes];

        for (int i = 0; i < numPieces; i++) {
            if (bitfield.get(i)) {
                bytes[i / 8] |= 1 << (7 - (i % 8));
            }
        }

        return bytes;
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

    public static Message createBitfieldMessage(BitSet bitfield, int numPieces) {
        byte[] bitfieldPayload = bitfieldToByteArray(bitfield, numPieces);
        return new Message(MessageType.BITFIELD, bitfieldPayload);
    }

    public static Message createRequestMessage() {
        return new Message(MessageType.REQUEST, null);
    }

    public static Message createPieceMessage() {
        return new Message(MessageType.PIECE, null);
    }

}