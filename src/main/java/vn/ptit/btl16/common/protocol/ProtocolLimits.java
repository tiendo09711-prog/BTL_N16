package vn.ptit.btl16.common.protocol;

public final class ProtocolLimits {
    public static final int CURRENT_VERSION = 1;
    public static final int MAGIC = 0x42544C31;
    public static final int MAX_DATA_ENTRIES = 4096;
    public static final int MAX_STRING_BYTES = 1024 * 1024;

    private ProtocolLimits() {
    }
}
