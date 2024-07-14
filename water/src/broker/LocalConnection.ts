import type { MessageId } from "./BrokerConnection.js";
import { BrokerConnection } from "./BrokerConnection.js";
import type { BrokerMessageHeaders } from "./BrokerMessageHeaders.js";

export class LocalConnection extends BrokerConnection {

    public override readonly supportsTopicHotSwap: boolean = true;
    public override readonly deferInitialTopicCreation: boolean = false;

    public constructor(public override readonly serviceName: string, public override readonly instanceId: string) {
        super();
    }

    public override async abstractSend(topic: string, key: string, value: string, headers: BrokerMessageHeaders): Promise<MessageId> {
        if (this.shouldDispatchExternallyAfterShortCircuit(topic, key, value, headers)) {
            const isForExternalService = headers.targetServices.size > 0 && [...headers.targetServices][0] !== this.serviceName;
            const isForExternalInstance = headers.targetInstances.size > 0 && [...headers.targetInstances][0] !== this.instanceId;
            if (isForExternalService || isForExternalInstance) {
                throw new Error("Attempting to send message to other services/instances in a LocalConnection " +
                    `(services=${[...headers.targetServices]}; instances=${[...headers.targetInstances]})`);
            }
        }
        return headers.messageId;
    }

    public override async abstractStart(): Promise<void> {
        // Nothing to start :)
    }

    public override createTopic(topic: string): void {
        // noop
    }

    public override removeTopic(topic: string): void {
        // noop
    }

}
