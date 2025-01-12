import path from "node:path";
import { fileURLToPath } from "node:url";
import eslint from "@eslint/js";
import { includeIgnoreFile } from "@eslint/compat";
import tseslint from "typescript-eslint";
import eslintConfigPrettier from "eslint-config-prettier";
import importPlugin from "eslint-plugin-import";

const gitignorePath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), ".gitignore");

export default tseslint.config(
	includeIgnoreFile(gitignorePath),
	{
		ignores: ["**/*.cjs", "eslint.config.mjs"],
	},
	eslint.configs.recommended,
	tseslint.configs.strictTypeChecked,
	tseslint.configs.stylisticTypeChecked,
	eslintConfigPrettier,
	{
		files: ["**/*.{ts}"],
		extends: [importPlugin.flatConfigs.recommended, importPlugin.flatConfigs.typescript],
	},
	// https://typescript-eslint.io/getting-started/typed-linting/
	{
		languageOptions: {
			parserOptions: {
				projectService: true,
				tsconfigRootDir: import.meta.dirname,
			},
		},
	},
	{
		rules: {
			"@typescript-eslint/consistent-type-assertions": "error",
			"@typescript-eslint/consistent-type-definitions": ["error", "interface"],
			"@typescript-eslint/consistent-type-imports": "error",
			"@typescript-eslint/restrict-template-expressions": [
				"error",
				{
					allowAny: true,
					allowArray: true,
					allowBoolean: true,
					allowNullish: true,
					allowNumber: true,
					allowRegExp: true,
				},
			],
		},
	},
);
