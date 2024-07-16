export class OnlineEmitter<T> {

    private readonly listeners: Set<(data: T) => void> = new Set();

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

    public async awaitValue(): Promise<T> {
        return new Promise(resolve => {
            const listener = (data: T) => {
                this.removeListener(listener);
                resolve(data);
            };
            this.addListener(listener);
        });
    }

    public [Symbol.asyncIterator](): AsyncIterableIterator<T> {
        const awaitValue = this.awaitValue.bind(this);
        return {
            async next(): Promise<IteratorResult<T>> {
                const value = await awaitValue();
                return { value, done: false };
            },
            [Symbol.asyncIterator](): AsyncIterableIterator<T> {
                return this;
            },
        };
    }

}
