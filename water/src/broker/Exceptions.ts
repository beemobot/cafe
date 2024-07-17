import type { RpcStatus } from "../index.js";

export class BrokerException extends Error {
    public constructor(message?: string) {
        super(message);
    }
}

export class RpcRequestTimeout extends BrokerException {
    public constructor(message: string) {
        super(message);
    }
}

export class IgnoreRpcRequest extends BrokerException {
    public constructor() {
        super("Ignoring RPC request");
    }
}

export class RpcException extends BrokerException {
    public constructor(public readonly status: RpcStatus) {
        super(`RPC failed with status ${status}`);
    }
}
