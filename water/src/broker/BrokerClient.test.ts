import { describe, expect, test } from "vitest";
import { null_, object, string } from "valibot";
import { LocalConnection } from "./LocalConnection.js";
import { BrokerClient } from "./BrokerClient.js";
import { Logger, RpcException, RpcStatus } from "@";

describe("BrokerClient", () => {
	const client = new TestBrokerClient(new LocalConnection("service", "instance"));
	test("greeting RPC", async () => {
		const response = await client.greetingRpc.call({ name: "Beemo" });
		expect(response.value.greeting).toBe("Hello, Beemo");
		expect(response.status.code).toBe(RpcStatus.OK.code);
	});
	test("null RPC", async () => {
		const response = await client.nullRpc.call(null);
		expect(response.value).toBe(null);
		expect(response.status.code).toBe(1337);
	});
	test("exception RPC", async () => {
		// Ideally the exception status should be checked here, but vitest
		// doesn't yet seem to have an API to inspect an error's properties.
		await expect(client.exceptionRpc.call(null)).rejects.toThrowError(RpcException);
	});
});

const greetingRequest = object({
	name: string(),
});

const greetingResponse = object({
	greeting: string(),
});

class TestBrokerClient extends BrokerClient {
	private static readonly TAG = "TestBrokerClient";

	public readonly greetingRpc = this.rpc("rpc.greetings", "greet", greetingRequest, greetingResponse, async msg => {
		Logger.info(TestBrokerClient.TAG, `greetingRpc received request: ${msg.value.name}`);
		return [RpcStatus.OK, { greeting: `Hello, ${msg.value.name}` }];
	});

	public readonly nullRpc = this.rpc("null", "null", null_(), null_(), async msg => {
		Logger.info(TestBrokerClient.TAG, `nullRpc received request: ${msg.value}`);
		expect(msg.value).toBe(null);
		return [new RpcStatus(1337), null];
	});

	public readonly exceptionRpc = this.rpc("exception", "exception", null_(), null_(), async msg => {
		Logger.info(TestBrokerClient.TAG, `exceptionRpc received request: ${msg.value}`);
		throw new RpcException(new RpcStatus(1337));
	});
}
