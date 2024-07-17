import { expect, test } from "vitest";
import { LocalConnection } from "./LocalConnection.js";

test("LocalConnection", () => {
	const connection = new LocalConnection("service-name", "instance-id");
	expect(connection.serviceName).toBe("service-name");
	expect(connection.instanceId).toBe("instance-id");
});
