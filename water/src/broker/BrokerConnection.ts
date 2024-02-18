import { Logger } from "../index.js";
import type { BrokerMessageHeaders } from "./BrokerMessageHeaders.js";


export type TopicListener = (topic: string, key: string, value: string, headers: BrokerMessageHeaders) => void;

export type MessageId = string;

const TAG = "BrokerConnection";

export abstract class BrokerConnection {

    public abstract serviceName: string;
    public abstract instanceId: string;
    public abstract supportsTopicHotSwap: boolean;

    protected topicListeners: Map<string, Set<TopicListener>> = new Map();

    public abstract start(): Promise<void>;
    public async destroy(): Promise<void> {
        this.topicListeners.clear();
    }

    public abstract abstractSend(topic: string, key: string, value: string, headers: BrokerMessageHeaders): Promise<MessageId>;

    public send(topic: string, key: string, value: string, headers: BrokerMessageHeaders): Promise<MessageId> {
        Logger.verbose(TAG, `Sending message ${headers.messageId} with key '${key}' in topic '${topic}'`);
        return this.abstractSend(topic, key, value, headers);
    }

    protected abstract createTopic(topic: string): void;
    protected abstract removeTopic(topic: string): void;

    public on(topic: string, cb: TopicListener): void {
        let listeners = this.topicListeners.get(topic);
        if (!listeners) {
            listeners = new Set();
            Logger.debug(TAG, `Creating new topic '${topic}'`);
            this.createTopic(topic);
            this.topicListeners.set(topic, listeners);
        }
        listeners.add(cb);
    }

    public off(topic: string, cb: TopicListener): void {
        const listeners = this.topicListeners.get(topic);
        if (listeners) {
            listeners.delete(cb);
            if (listeners.size === 0) {
                Logger.debug(TAG, `Removing topic '${topic}'`);
                this.removeTopic(topic);
                this.topicListeners.delete(topic);
            }
        }
    }

    // To be called by implementers
    protected dispatchIncomingMessage(topic: string, key: string, value: string, headers: BrokerMessageHeaders): void {
        if (
            (headers.targetServices.size >= 1 && !headers.targetServices.has(this.serviceName)) ||
            (headers.targetInstances.size >= 1 && !headers.targetInstances.has(this.instanceId))
        ) {
            // If there is a target cluster restriction and this message wasn't meant for us,
            // discard it immediately without notifying any listeners.
            return;
        }
        if (headers.sourceInstance === this.instanceId && headers.sourceService === this.serviceName) {
            // If this message was sent by ourselves, discard it too, as we already dispatch events
            // to our listeners in `send()` to avoid the round trip through an external service.
            return;
        }
        this.invokeLocalCallbacks(topic, key, value, headers);
    }

    protected shouldDispatchExternallyAfterShortCircuit(topic: string, key: string, value: string, headers: BrokerMessageHeaders): boolean {
        const targetServices = headers.targetServices;
        const targetInstances = headers.targetInstances;
        const isThisConnectionTargeted = (targetServices.size === 0 || targetServices.has(this.serviceName)) &&
            (targetInstances.size === 0 || targetInstances.has(this.instanceId));

        // If the message is meant for ourselves (amongst other clusters),
        // immediately dispatch it to the listeners.
        if (isThisConnectionTargeted) {
            this.invokeLocalCallbacks(topic, key, value, headers);
        }

        // Return whether implementers should dispatch this message to external services
        return (
            // For all services/instances
            targetServices.size === 0 || targetInstances.size === 0 ||
            // Not for us, so it must be for somebody else
            !isThisConnectionTargeted ||
            // For us, so check if it is also for someone else
            targetServices.size > 1 || targetInstances.size > 1
        );
    }

    private invokeLocalCallbacks(topic: string, key: string, value: string, headers: BrokerMessageHeaders): void {
        const listeners = this.topicListeners.get(topic);
        if (listeners) {
            for (const listener of listeners) {
                try {
                    listener(topic, key, value, headers);
                } catch (e) {
                    Logger.error(TAG, `Uncaught error in BrokerConnection listener for key '${key}' in topic '${topic}'`, e);
                }
            }
        }
    }

}
