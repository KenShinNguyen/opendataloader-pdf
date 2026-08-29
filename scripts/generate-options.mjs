#!/usr/bin/env node
/**
 * Generates CLI option definitions for Node.js, Python, and documentation
 * from the single source of truth (options.json).
 *
 * Usage: node scripts/generate-options.mjs
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { escapeMarkdown, formatTable } from './utils.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(__dirname, '..');

// Read options.json
const optionsPath = join(ROOT_DIR, 'options.json');
const options = JSON.parse(readFileSync(optionsPath, 'utf-8'));

const AUTO_GENERATED_HEADER = `// AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
// Run \`npm run generate-options\` to regenerate
`;

const AUTO_GENERATED_HEADER_PYTHON = `# AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
# Run \`npm run generate-options\` to regenerate
`;

const AUTO_GENERATED_HEADER_MDX = `{/* AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY */}
{/* Run \`npm run generate-options\` to regenerate */}

`;

/**
 * Convert kebab-case to camelCase
 */
function toCamelCase(str) {
  return str.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
}

/**
 * Convert kebab-case to snake_case
 */
function toSnakeCase(str) {
  return str.replace(/-/g, '_');
}

/**
 * Options that accept comma-separated list values.
 */
const LIST_OPTIONS = new Set(['format', 'content-safety-off']);

/**
 * Options whose values are numbers, and which number kind they take.
 *
 * options.json types every non-boolean option as "string" because that is all
 * the Java exporter records, so the numeric ones have to be named here — the
 * same way LIST_OPTIONS names the comma-separated ones. Moving this to the
 * exporter would make options.json the single source of truth; until then this
 * map is what lets the bindings accept a real number.
 */
const NUMERIC_OPTIONS = new Map([
  ['threads', 'int'],
  ['hybrid-timeout', 'int'],
  ['image-resolution', 'float'],
  ['space-ratio', 'float'],
]);

/**
 * Options naming a filesystem path, which may be given as a pathlib.Path.
 */
const PATH_OPTIONS = new Set(['output-dir', 'image-dir']);

/**
 * Check if option supports list values.
 */
function isListOption(opt) {
  return LIST_OPTIONS.has(opt.name);
}

/**
 * Number kind for an option, or undefined when it is not numeric.
 */
function numericKind(opt) {
  return NUMERIC_OPTIONS.get(opt.name);
}

/**
 * Escape string for use in generated code.
 * @param {string} str - The string to escape
 * @param {string} quote - The quote character (' or ")
 * @param {object} options - Additional escape options
 * @param {boolean} options.escapePercent - Escape % as %% for Python argparse
 */
function escapeString(str, quote = "'", { escapePercent = false } = {}) {
  let result = str.replace(/\\/g, '\\\\'); // escape backslashes first
  if (quote === "'") {
    result = result.replace(/'/g, "\\'");
  } else {
    result = result.replace(/"/g, '\\"');
  }
  if (escapePercent) {
    result = result.replace(/%/g, '%%');
  }
  return result;
}

/**
 * Generate Node.js CLI options file
 */
function generateNodeCliOptions() {
  const lines = [AUTO_GENERATED_HEADER];
  lines.push(`import { Command } from 'commander';`);
  lines.push('');
  lines.push('/**');
  lines.push(' * Register all CLI options on the given Commander program.');
  lines.push(' */');
  lines.push('export function registerCliOptions(program: Command): void {');

  for (const opt of options.options) {
    const flags = opt.shortName
      ? `-${opt.shortName}, --${opt.name}${opt.type === 'string' ? ' <value>' : ''}`
      : `--${opt.name}${opt.type === 'string' ? ' <value>' : ''}`;

    const description = escapeString(opt.description, "'");
    lines.push(`  program.option('${flags}', '${description}');`);
  }

  lines.push('}');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'node/opendataloader-pdf/src/cli-options.generated.ts');
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Node.js ConvertOptions interface and helper functions
 */
function generateNodeConvertOptions() {
  const lines = [AUTO_GENERATED_HEADER];

  // Generate ConvertOptions interface
  lines.push('/**');
  lines.push(' * Options for the convert function.');
  lines.push(' */');
  lines.push('export interface ConvertOptions {');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    let tsType = 'string';

    if (opt.type === 'boolean') {
      tsType = 'boolean';
    } else if (isListOption(opt)) {
      tsType = 'string | string[]';
    } else if (numericKind(opt)) {
      // number as well as string: `threads: 4` is the natural call.
      tsType = 'string | number';
    }

    lines.push(`  /** ${opt.description} */`);
    lines.push(`  ${camelName}?: ${tsType};`);
  }

  lines.push('}');
  lines.push('');

  // Generate CliOptions interface (for CLI parsing - all values are strings from commander)
  lines.push('/**');
  lines.push(' * Options as parsed from CLI (all values are strings from commander).');
  lines.push(' */');
  lines.push('export interface CliOptions {');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    const tsType = opt.type === 'boolean' ? 'boolean' : 'string';
    lines.push(`  ${camelName}?: ${tsType};`);
  }

  lines.push('}');
  lines.push('');

  // Generate buildConvertOptions function
  lines.push('/**');
  lines.push(' * Convert CLI options to ConvertOptions.');
  lines.push(' */');
  lines.push('export function buildConvertOptions(cliOptions: CliOptions): ConvertOptions {');
  lines.push('  const convertOptions: ConvertOptions = {};');
  lines.push('');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    if (opt.type === 'boolean') {
      lines.push(`  if (cliOptions.${camelName}) {`);
      lines.push(`    convertOptions.${camelName} = true;`);
      lines.push('  }');
    } else {
      // commander omits absent options; an explicitly passed '' must survive.
      lines.push(`  if (cliOptions.${camelName} !== undefined && cliOptions.${camelName} !== null) {`);
      lines.push(`    convertOptions.${camelName} = cliOptions.${camelName};`);
      lines.push('  }');
    }
  }

  lines.push('');
  lines.push('  return convertOptions;');
  lines.push('}');
  lines.push('');

  // Generate buildArgs function
  lines.push('/**');
  lines.push(' * Build CLI arguments array from ConvertOptions.');
  lines.push(' */');
  lines.push('export function buildArgs(options: ConvertOptions): string[] {');
  lines.push('  const args: string[] = [];');
  lines.push('');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    const cliFlag = `--${opt.name}`;

    if (opt.type === 'boolean') {
      lines.push(`  if (options.${camelName}) {`);
      lines.push(`    args.push('${cliFlag}');`);
      lines.push('  }');
    } else if (isListOption(opt)) {
      // An empty array means "no value given", so it is skipped like undefined.
      lines.push(`  if (options.${camelName} !== undefined && options.${camelName} !== null) {`);
      lines.push(`    if (Array.isArray(options.${camelName})) {`);
      lines.push(`      if (options.${camelName}.length > 0) {`);
      lines.push(`        args.push('${cliFlag}', options.${camelName}.join(','));`);
      lines.push('      }');
      lines.push('    } else {');
      lines.push(`      args.push('${cliFlag}', String(options.${camelName}));`);
      lines.push('    }');
      lines.push('  }');
    } else {
      // Explicit null/undefined check, not truthiness: 0 is meaningful for the
      // numeric options (--hybrid-timeout 0 means "no timeout") and '' is
      // meaningful for --replace-invalid-chars. String() so a number argument
      // is not dropped or passed through as a non-string.
      lines.push(`  if (options.${camelName} !== undefined && options.${camelName} !== null) {`);
      lines.push(`    args.push('${cliFlag}', String(options.${camelName}));`);
      lines.push('  }');
    }
  }

  lines.push('');
  lines.push('  return args;');
  lines.push('}');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'node/opendataloader-pdf/src/convert-options.generated.ts');
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Python CLI options file (cli_options.py)
 */
function generatePythonCliOptions() {
  const lines = [AUTO_GENERATED_HEADER_PYTHON];
  lines.push('"""');
  lines.push('CLI option definitions for opendataloader-pdf.');
  lines.push('"""');
  lines.push('from typing import Any, Dict, List');
  lines.push('');
  lines.push('');
  lines.push('# Option metadata list');
  lines.push('CLI_OPTIONS: List[Dict[str, Any]] = [');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    const defaultValue = opt.default === null ? 'None'
      : typeof opt.default === 'boolean' ? (opt.default ? 'True' : 'False')
      : `"${opt.default}"`;

    lines.push('    {');
    lines.push(`        "name": "${opt.name}",`);
    lines.push(`        "python_name": "${snakeName}",`);
    lines.push(`        "short_name": ${opt.shortName ? `"${opt.shortName}"` : 'None'},`);
    lines.push(`        "type": "${opt.type}",`);
    lines.push(`        "required": ${opt.required ? 'True' : 'False'},`);
    lines.push(`        "default": ${defaultValue},`);
    lines.push(`        "description": "${escapeString(opt.description, '"', { escapePercent: true })}",`);
    lines.push('    },');
  }

  lines.push(']');
  lines.push('');
  lines.push('');
  lines.push('def add_options_to_parser(parser) -> None:');
  lines.push('    """Add all CLI options to an argparse.ArgumentParser."""');
  lines.push('    for opt in CLI_OPTIONS:');
  lines.push('        flags = []');
  lines.push('        if opt["short_name"]:');
  lines.push("            flags.append(f'-{opt[\"short_name\"]}')");
  lines.push("        flags.append(f'--{opt[\"name\"]}')");
  lines.push('');
  lines.push('        kwargs = {"help": opt["description"]}');
  lines.push('        if opt["type"] == "boolean":');
  lines.push('            kwargs["action"] = "store_true"');
  lines.push('        else:');
  lines.push('            kwargs["default"] = None');
  lines.push('');
  lines.push('        parser.add_argument(*flags, **kwargs)');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'python/opendataloader-pdf/src/opendataloader_pdf/cli_options_generated.py');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Python convert function (convert.py)
 */
function generatePythonConvert() {
  const lines = [AUTO_GENERATED_HEADER_PYTHON];
  lines.push('"""');
  lines.push('Auto-generated convert function for opendataloader-pdf.');
  lines.push('"""');
  lines.push('from os import PathLike');
  lines.push('from typing import List, Optional, Union');
  lines.push('');
  lines.push('from .runner import run_jar');
  lines.push('');
  lines.push('');

  // Generate function signature
  lines.push('def convert(');
  lines.push('    input_path: Union[str, PathLike, List[Union[str, PathLike]]],');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    let typeHint;
    let defaultVal;

    const numeric = numericKind(opt);
    if (opt.type === 'boolean') {
      typeHint = 'bool';
      defaultVal = opt.default ? 'True' : 'False';
    } else if (isListOption(opt)) {
      typeHint = 'Optional[Union[str, List[str]]]';
      defaultVal = 'None';
    } else if (numeric === 'int') {
      // str stays accepted: callers already pass "4".
      typeHint = 'Optional[Union[int, str]]';
      defaultVal = 'None';
    } else if (numeric === 'float') {
      typeHint = 'Optional[Union[float, int, str]]';
      defaultVal = 'None';
    } else if (PATH_OPTIONS.has(opt.name)) {
      typeHint = 'Optional[Union[str, PathLike]]';
      defaultVal = 'None';
    } else {
      typeHint = 'Optional[str]';
      defaultVal = 'None';
    }

    lines.push(`    ${snakeName}: ${typeHint} = ${defaultVal},`);
  }

  lines.push(') -> None:');
  lines.push('    """');
  lines.push('    Convert PDF(s) into the requested output format(s).');
  lines.push('');
  lines.push('    Args:');
  lines.push('        input_path: One or more input PDF file paths or directories');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    lines.push(`        ${snakeName}: ${opt.description}`);
  }

  lines.push('    """');

  // Generate function body
  lines.push('    args: List[str] = []');
  lines.push('');
  lines.push('    # Build input paths (str() so pathlib.Path inputs work too)');
  lines.push('    if isinstance(input_path, (list, tuple)):');
  lines.push('        args.extend(str(p) for p in input_path)');
  lines.push('    else:');
  lines.push('        args.append(str(input_path))');
  lines.push('');

  // Generate args building for each option
  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    const cliFlag = `--${opt.name}`;

    if (opt.type === 'boolean') {
      lines.push(`    if ${snakeName}:`);
      lines.push(`        args.append("${cliFlag}")`);
    } else if (isListOption(opt)) {
      // An empty list means "no value given", so it is skipped like None.
      lines.push(`    if ${snakeName} is not None:`);
      lines.push(`        if isinstance(${snakeName}, (list, tuple)):`);
      lines.push(`            if ${snakeName}:`);
      lines.push(
        `                args.extend(["${cliFlag}", ",".join(str(v) for v in ${snakeName})])`,
      );
      lines.push(`        else:`);
      lines.push(`            args.extend(["${cliFlag}", str(${snakeName})])`);
    } else {
      // `is not None`, not truthiness: 0 is a meaningful value for the numeric
      // options (--hybrid-timeout 0 means "no timeout") and "" is meaningful
      // for --replace-invalid-chars. str() so an int, float or Path argument
      // reaches subprocess as text instead of raising TypeError.
      lines.push(`    if ${snakeName} is not None:`);
      lines.push(`        args.extend(["${cliFlag}", str(${snakeName})])`);
    }
  }

  lines.push('');
  lines.push('    run_jar(args, quiet)');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Python convert() options table (MDX snippet)
 */
function generatePythonConvertOptionsMdx() {
  const lines = [];
  lines.push('---');
  lines.push('title: Python Convert Options');
  lines.push('description: Options for the Python convert function');
  lines.push('---');
  lines.push('');
  lines.push(AUTO_GENERATED_HEADER_MDX);

  // Build rows array
  const rows = [];

  // Add input_path first (not in options.json)
  rows.push(['`input_path`', String.raw`\`str \| list[str]\``, 'required', 'One or more input PDF file paths or directories']);

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    let pyType = 'str';
    if (opt.type === 'boolean') {
      pyType = 'bool';
    } else if (isListOption(opt)) {
      pyType = String.raw`str \| list[str]`;
    }

    const defaultVal = opt.default === null ? '-'
      : typeof opt.default === 'boolean' ? (opt.default ? '`True`' : '`False`')
      : `\`"${opt.default}"\``;

    const description = escapeMarkdown(opt.description);
    rows.push([`\`${snakeName}\``, `\`${pyType}\``, defaultVal, description]);
  }

  lines.push(...formatTable(['Parameter', 'Type', 'Default', 'Description'], rows));
  lines.push('');

  const outputPath = join(ROOT_DIR, 'content/docs/reference/python-convert-options.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Node.js convert() options table (MDX snippet)
 */
function generateNodeConvertOptionsMdx() {
  const lines = [];
  lines.push('---');
  lines.push('title: Node.js Convert Options');
  lines.push('description: Options for the Node.js convert function');
  lines.push('---');
  lines.push('');
  lines.push(AUTO_GENERATED_HEADER_MDX);

  // Build rows array
  const rows = [];

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    let tsType = 'string';
    if (opt.type === 'boolean') {
      tsType = 'boolean';
    } else if (isListOption(opt)) {
      tsType = String.raw`string \| string[]`;
    }

    const defaultVal = opt.default === null ? '-'
      : typeof opt.default === 'boolean' ? `\`${opt.default}\``
      : `\`"${opt.default}"\``;

    const description = escapeMarkdown(opt.description);
    rows.push([`\`${camelName}\``, `\`${tsType}\``, defaultVal, description]);
  }

  lines.push(...formatTable(['Option', 'Type', 'Default', 'Description'], rows));
  lines.push('');

  const outputPath = join(ROOT_DIR, 'content/docs/reference/node-convert-options.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate options reference documentation (MDX)
 */
function generateOptionsReferenceMdx() {
  // Build rows array
  const rows = [];

  for (const opt of options.options) {
    const longOpt = `\`--${opt.name}\``;
    const shortOpt = opt.shortName ? `\`-${opt.shortName}\`` : '-';
    const type = `\`${opt.type}\``;
    const defaultVal = opt.default === null ? '-'
      : typeof opt.default === 'boolean' ? `\`${opt.default}\``
      : `\`"${opt.default}"\``;
    const description = escapeMarkdown(opt.description);

    rows.push([longOpt, shortOpt, type, defaultVal, description]);
  }

  const lines = [
    '---',
    'title: CLI Options Reference',
    'description: Complete reference for all CLI options',
    '---',
    '',
    AUTO_GENERATED_HEADER_MDX.trimEnd(),
    '# CLI Options Reference',
    '',
    'This page documents all available CLI options for opendataloader-pdf.',
    '',
    '## Options',
    '',
    ...formatTable(['Option', 'Short', 'Type', 'Default', 'Description'], rows),
    '',
    '## Examples',
    '',
    '### Basic conversion',
    '',
    '```bash',
    'opendataloader-pdf document.pdf -o ./output -f json,markdown',
    '```',
    '',
    '### Convert entire folder',
    '',
    '```bash',
    'opendataloader-pdf ./pdf-folder -o ./output -f json',
    '```',
    '',
    '### Save images as external files',
    '',
    '```bash',
    'opendataloader-pdf document.pdf -f markdown --image-output external',
    '```',
    '',
    '### Disable reading order sorting',
    '',
    '```bash',
    'opendataloader-pdf document.pdf -f json --reading-order off',
    '```',
    '',
    '### Add page separators in output',
    '',
    '```bash',
    'opendataloader-pdf document.pdf -f markdown --markdown-page-separator "--- Page %page-number% ---"',
    '```',
    '',
    '### Encrypted PDF',
    '',
    '```bash',
    'opendataloader-pdf encrypted.pdf -p mypassword -o ./output',
    '```',
    '',
  ];

  const outputPath = join(ROOT_DIR, 'content/docs/reference/cli-options.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

// Run all generators
console.log('Generating files from options.json...\n');

generateNodeCliOptions();
generateNodeConvertOptions();
generatePythonCliOptions();
generatePythonConvert();
generateOptionsReferenceMdx();
generatePythonConvertOptionsMdx();
generateNodeConvertOptionsMdx();

console.log('\nDone!');
