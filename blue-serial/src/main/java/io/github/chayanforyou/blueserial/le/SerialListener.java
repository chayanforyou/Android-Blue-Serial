package io.github.chayanforyou.blueserial.le;

public interface SerialListener {
    void onSerialConnect();

    void onSerialConnectError(Exception e);

    void onSerialRead(byte[] data);                // socket -> service

    void onSerialIoError(Exception e);
}
