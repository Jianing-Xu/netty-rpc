package com.xujn.nettyrpc.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents the result of an RPC invocation, carrying either
 * a successful return value or an error throwable.
 */
public class RpcResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long requestId;
    private byte status; // 0 = success, 1 = error
    private Object result;
    private Throwable error;

    public RpcResponse() {
    }

    public static RpcResponse success(long requestId, Object result) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setStatus((byte) 0);
        response.setResult(result);
        return response;
    }

    public static RpcResponse error(long requestId, Throwable error) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setStatus((byte) 1);
        response.setError(error);
        return response;
    }

    public boolean isSuccess() {
        return status == 0;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public byte getStatus() {
        return status;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RpcResponse that = (RpcResponse) o;
        return requestId == that.requestId && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, status);
    }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "requestId=" + requestId +
                ", status=" + status +
                ", result=" + result +
                ", error=" + error +
                '}';
    }
}
