export class RpcStatus {
	public static readonly OK = new RpcStatus(0);
	public static readonly UNKNOWN = new RpcStatus(999_999);

	public constructor(public readonly code: number) {}

	public equals(other: RpcStatus): boolean {
		return this.code === other.code;
	}

	public toString(): string {
		return `RpcStatus(${this.code})`;
	}
}
