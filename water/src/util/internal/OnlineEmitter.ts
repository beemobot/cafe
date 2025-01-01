/**
 * A class that allows for emitting events to multiple listeners.
 * Events emitted when no listeners are registered are lost.
 */
export class OnlineEmitter<T> {
	protected readonly listeners: Set<(data: T) => void> = new Set();

	public emit(data: T): void {
		for (const listener of this.listeners) {
			listener(data);
		}
	}

	public addListener(listener: (data: T) => void): void {
		this.listeners.add(listener);
	}

	public removeListener(listener: (data: T) => void): void {
		this.listeners.delete(listener);
	}

	public async next(): Promise<T> {
		return new Promise(resolve => {
			const listener = (data: T) => {
				this.removeListener(listener);
				resolve(data);
			};
			this.addListener(listener);
		});
	}

	public [Symbol.asyncIterator](): AsyncIterableIterator<T> {
		const next = this.next.bind(this);
		return {
			async next(): Promise<IteratorResult<T>> {
				const value = await next();
				return { value, done: false };
			},
			[Symbol.asyncIterator](): AsyncIterableIterator<T> {
				return this;
			},
		};
	}
}
