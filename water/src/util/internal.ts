export function getOrThrow(headers: Record<string, string>, key: string): string {
	const value = headers[key];
	if (value === undefined) {
		throw new Error(`Missing broker message header '${key}'`);
	}
	return value;
}
