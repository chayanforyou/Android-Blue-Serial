package io.github.chayanforyou.blueserial;

public abstract class BluetoothConnectionBase implements BluetoothConnection {

    /// Callback for reading data.
    public interface OnReadCallback {
        void onRead(byte[] data);
    }

    /// Callback for disconnection.
    public interface OnDisconnectedCallback {
        void onDisconnected(boolean byRemote);
    }

    final OnReadCallback onReadCallback;
    final OnDisconnectedCallback onDisconnectedCallback;

    public BluetoothConnectionBase(OnReadCallback onReadCallback, OnDisconnectedCallback onDisconnectedCallback) {
        this.onReadCallback = onReadCallback;
        this.onDisconnectedCallback = onDisconnectedCallback;
    }

    public void onRead(byte[] data) {
        onReadCallback.onRead(data);
    }

    public void onDisconnected(boolean byRemote) {
        onDisconnectedCallback.onDisconnected(byRemote);
    }
}
