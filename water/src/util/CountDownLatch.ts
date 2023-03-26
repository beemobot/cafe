type PromiseResolver = (timeout: boolean) => void;

export class CountDownLatch {

    private counter: number;
    private resolvers = new Map<PromiseResolver, NodeJS.Timeout | null>();

    public constructor(initialCount: number) {
        if (!Number.isInteger(initialCount)) {
            throw new Error("Initial count must be an integer");
        }
        if (initialCount <= 0) {
            throw new Error(`Initial count has to be greater than zero (is ${initialCount})`);
        }
        this.counter = initialCount;
    }

    public countDown(): void {
        if (this.counter <= 0) {
            return;
        }
        this.counter--;

        if (this.counter === 0) {
            for (const [resolver, timeout] of this.resolvers.entries()) {
                if (timeout) {
                    clearTimeout(timeout);
                }
                resolver(true);
            }
            this.resolvers.clear();
        }
    }

    public getCount(): number {
        return this.counter;
    }

    public await(waitTime: number | null = null): Promise<boolean> {
        if (this.counter === 0) {
            return Promise.resolve(true);
        }

        let resolver: PromiseResolver;
        const promise = new Promise<boolean>(r => {
            resolver = r;
            this.resolvers.set(r, null);
        });
        if (!waitTime) {
            return promise;
        }

        const timeout = setTimeout(() => {
            this.resolvers.delete(resolver);
            resolver(false);
        }, waitTime);
        timeout.unref();
        return promise;
    }

}
