package com.xujn.nettyrpc.common.exception;

import java.io.Serial;

/**
 * Base exception for all RPC framework errors.
 */
public class RpcException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcException(Throwable cause) {
        super(cause);
    }
}
