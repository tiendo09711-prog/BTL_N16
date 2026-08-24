package vn.ptit.btl16.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface MessageCodec {
    WireMessage read(DataInputStream input) throws IOException;

    void write(DataOutputStream output, WireMessage message) throws IOException;
}
