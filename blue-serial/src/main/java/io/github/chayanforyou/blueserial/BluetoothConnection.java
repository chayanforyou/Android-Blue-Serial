package io.github.chayanforyou.blueserial;

import java.io.IOException;
import java.util.UUID;

public interface BluetoothConnection {
    boolean isConnected();

    /// Connects to given device by hardware address
    void connect(String address, UUID uuid) throws IOException;

    /// Connects to given device by hardware address (default UUID used)
    void connect(String address) throws IOException;

    /// Disconnects current session (ignore if not connected)
    void disconnect();

    /// Writes to connected remote device
    void write(byte[] data) throws IOException;
}
