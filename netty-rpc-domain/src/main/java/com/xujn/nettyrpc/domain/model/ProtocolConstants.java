package com.xujn.nettyrpc.domain.model;

/**
 * Constants defining the custom binary RPC protocol.
 */
public final class ProtocolConstants {

    /** Magic number used to identify valid RPC protocol frames. */
    public static final short MAGIC_NUMBER = (short) 0xCAFE;

    /** Current protocol version. */
    public static final byte VERSION = 1;

    /**
     * Fixed header length in bytes.
     * Layout: Magic(2) + Version(1) + MsgType(1) + SerType(1) + Status(1) + RequestId(8) + BodyLen(4) = 18
     */
    public static final int HEADER_LENGTH = 18;

    private ProtocolConstants() {
        throw new UnsupportedOperationException("Constants class");
    }
}
