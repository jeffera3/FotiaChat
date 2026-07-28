package gg.fotia.chat.crossserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 跨服消息数据类。
 */
public class CrossServerMessage {

    private static final String V2_PREFIX = "FC2:";
    private static final int FIELD_COUNT = 7;
    private static final int MAX_FIELD_BYTES = 1_048_576;

    private final String type;
    private final String serverName;
    private final UUID senderUuid;
    private final String senderName;
    private final String channelId;
    private final String message;
    private final long timestamp;

    public CrossServerMessage(String type, String serverName, UUID senderUuid,
                              String senderName, String channelId, String message) {
        this(type, serverName, senderUuid, senderName, channelId, message, System.currentTimeMillis());
    }

    private CrossServerMessage(String type, String serverName, UUID senderUuid,
                               String senderName, String channelId, String message, long timestamp) {
        this.type = type;
        this.serverName = serverName;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.channelId = channelId;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public String getServerName() {
        return serverName;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 序列化为无分隔符歧义的版本化字符串。
     */
    public String serialize() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(FIELD_COUNT);
                writeString(output, type);
                writeString(output, serverName);
                writeString(output, senderUuid == null ? null : senderUuid.toString());
                writeString(output, senderName);
                writeString(output, channelId);
                writeString(output, message);
                output.writeLong(timestamp);
            }
            return V2_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("无法序列化跨服消息", exception);
        }
    }

    public static CrossServerMessage deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            return data.startsWith(V2_PREFIX) ? deserializeV2(data.substring(V2_PREFIX.length())) : deserializeLegacy(data);
        } catch (IllegalArgumentException | IOException exception) {
            return null;
        }
    }

    private static CrossServerMessage deserializeV2(String encoded) throws IOException {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != FIELD_COUNT) {
                return null;
            }
            String type = readString(input);
            String serverName = readString(input);
            String uuidText = readString(input);
            String senderName = readString(input);
            String channelId = readString(input);
            String message = readString(input);
            long timestamp = input.readLong();
            if (input.available() != 0 || type == null || serverName == null || message == null) {
                return null;
            }
            UUID senderUuid = uuidText == null || uuidText.isEmpty() ? null : UUID.fromString(uuidText);
            return new CrossServerMessage(type, serverName, senderUuid, senderName, channelId, message, timestamp);
        } catch (EOFException exception) {
            return null;
        }
    }

    private static CrossServerMessage deserializeLegacy(String data) {
        String[] parts = data.split("\\|\\|", 7);
        if (parts.length < 7) {
            return null;
        }
        UUID senderUuid = parts[2].isEmpty() ? null : UUID.fromString(parts[2]);
        long timestamp = Long.parseLong(parts[6]);
        return new CrossServerMessage(
                parts[0],
                parts[1],
                senderUuid,
                parts[3],
                parts[4].isEmpty() ? null : parts[4],
                parts[5],
                timestamp
        );
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_FIELD_BYTES || length > input.available()) {
            throw new IOException("无效的跨服消息字段长度: " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("跨服消息字段不完整");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_PRIVATE = "PRIVATE";
    public static final String TYPE_BROADCAST = "BROADCAST";
}
