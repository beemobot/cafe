import { OnlineEmitter } from "./OnlineEmitter.js";

/**
 * A class that allows for emitting events to multiple listeners.
 * Events emitted when no listeners are registered are buffered until a listener is registered,
 * at which point the buffered events are emitted in order.
 */
export class BufferedEmitter<T> extends OnlineEmitter<T> {
	private readonly buffer: T[] = [];

	/**
	 * Emit data to listeners if any are registered, otherwise buffer the data.
	 *
	 * @param data The data to emit.
	 */
	public override emit(data: T): void {
		if (this.listeners.size > 0) {
			super.emit(data);
		} else {
			this.buffer.push(data);
		}
	}

	public override addListener(listener: (data: T) => void): void {
		super.addListener(listener);
		for (const data of this.buffer) {
			listener(data);
		}
		this.buffer.length = 0;
	}

	/**
	 * Returns the next value from the buffer, if any, or waits for the next emitted value.
	 *
	 * @returns A promise that resolves to the next emitted value.
	 */
	public override async next(): Promise<T> {
		if (this.buffer.length > 0) {
			return this.buffer.shift()!;
		}
		return super.next();
	}
}
