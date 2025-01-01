import { inspect } from "node:util";

enum LogLevels {
	VERBOSE,
	DEBUG,
	INFO,
	WARN,
	ERROR,
	WTF,
}

const LevelNames = ["verbose", "debug", "info", "warn", "error", "assert"];
const LevelColors = [
	["\u001b[97m", "\u001b[39m"],
	["\u001b[94m", "\u001b[39m"],
	["\u001b[92m", "\u001b[39m"],
	["\u001b[93m", "\u001b[39m"],
	["\u001b[91m", "\u001b[39m"],
	["\u001b[95m", "\u001b[39m"],
];

export class Logger {
	public static verbose(tag: string, message: string, error?: any): void {
		Logger.log(LogLevels.VERBOSE, tag, message, error);
	}

	public static debug(tag: string, message: string, error?: any): void {
		Logger.log(LogLevels.DEBUG, tag, message, error);
	}

	public static info(tag: string, message: string, error?: any): void {
		Logger.log(LogLevels.INFO, tag, message, error);
	}

	public static warn(tag: string, message: string, error?: any): void {
		Logger.log(LogLevels.WARN, tag, message, error);
	}

	public static error(tag: string, message: string, error?: any): void {
		Logger.log(LogLevels.ERROR, tag, message, error);
	}

	public static wtf(tag: string, message: string, error?: any): void {
		Logger.log(LogLevels.WTF, tag, message, error);
		process.exit(1);
	}

	private static log(level: LogLevels, tag: string, message: string, error?: any): void {
		const coloredLevel = Logger.getColoredLevelName(level);
		const stringifiedError = `${error ? `${inspect(error, false, 4)}` : ""}`;
		const stringifiedDate = `[${new Date().toISOString()}]`;
		const consoleLogString = `${stringifiedDate} ${coloredLevel}/${tag}: ${message} ${stringifiedError}`.trim();
		// eslint-disable-next-line no-console
		console.log(consoleLogString);
	}

	private static getColoredLevelName(level: LogLevels): string {
		const c = LevelColors[level] ?? [];
		return `${c[0]}${LevelNames[level]}${c[1]}`;
	}
}
