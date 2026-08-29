// AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
// Run `npm run generate-options` to regenerate

/**
 * Options for the convert function.
 */
export interface ConvertOptions {
  /** Directory where output files are written. Default: input file directory */
  outputDir?: string;
  /** Password for encrypted PDF files */
  password?: string;
  /** Output formats (comma-separated). Values: json, text, html, pdf, markdown, tagged-pdf. Default: json. For HTML inside Markdown use --markdown-with-html. For image extraction control use --image-output. */
  format?: string | string[];
  /** Suppress console logging output */
  quiet?: boolean;
  /** Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg */
  contentSafetyOff?: string | string[];
  /** Enable sensitive data sanitization. Replaces emails, phone numbers, IPs, credit cards, and URLs with placeholders */
  sanitize?: boolean;
  /** Preserve original line breaks in extracted text */
  keepLineBreaks?: boolean;
  /** Replacement character for invalid/unrecognized characters. Default: space */
  replaceInvalidChars?: string;
  /** Use PDF structure tree (tagged PDF) for reading order and semantic structure. Output quality depends on tag quality. Takes precedence over --hybrid: when both are set on a tagged PDF, the structure tree is used and the hybrid backend is not called */
  useStructTree?: boolean;
  /** Table detection method. Values: default (border-based), cluster (border + cluster). Default: default */
  tableMethod?: string;
  /** Reading order algorithm. Values: off, xycut. Default: xycut */
  readingOrder?: string;
  /** Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none */
  markdownPageSeparator?: string;
  /** Allow HTML tags inside Markdown output for complex structures such as multi-row-span tables. Implies --format markdown. */
  markdownWithHtml?: boolean;
  /** Separator between pages in text output. Use %page-number% for page numbers. Default: none */
  textPageSeparator?: string;
  /** Separator between pages in HTML output. Use %page-number% for page numbers. Default: none */
  htmlPageSeparator?: string;
  /** Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external */
  imageOutput?: string;
  /** Output format for extracted images. Values: png, jpeg. Default: png */
  imageFormat?: string;
  /** Directory for extracted images (applies only with --image-output external) */
  imageDir?: string;
  /** Pages to extract (e.g., "1,3,5-7"). Default: all pages */
  pages?: string;
  /** Include page headers and footers in output */
  includeHeaderFooter?: boolean;
  /** Detect strikethrough text and wrap with ~~ in Markdown output or <del></del> tag in HTML output (experimental) */
  detectStrikethrough?: boolean;
  /** Hybrid backend (requires a running server). Quick start: pip install "opendataloader-pdf[hybrid]" && opendataloader-pdf-hybrid --port 5002. For remote servers use --hybrid-url. Values: off (default), docling-fast, hancom-ai. Ignored when --use-struct-tree is set on a tagged PDF (structure tree takes precedence) */
  hybrid?: string;
  /** Hybrid triage mode. Values: auto (default, dynamic triage), full (skip triage, all pages to backend) */
  hybridMode?: string;
  /** Hybrid backend server URL (overrides default) */
  hybridUrl?: string;
  /** Hybrid backend request timeout in milliseconds (0 = no timeout). Default: 0 */
  hybridTimeout?: string | number;
  /** Opt in to Java fallback on hybrid backend error (default: disabled) */
  hybridFallback?: boolean;
  /** DLA label 7 (regionlist) handling. Requires --hybrid=hancom-ai. Values: table-first (default; check TSR overlap), list-only (skip TSR, always treat as list) */
  hybridHancomAiRegionlistStrategy?: string;
  /** OCR strategy. Requires --hybrid=hancom-ai. Values: off (stream-only), auto (default; stream first, OCR fallback), force (OCR-only) */
  hybridHancomAiOcrStrategy?: string;
  /** Page image cache backing. Requires --hybrid=hancom-ai. Values: memory (default), disk */
  hybridHancomAiImageCache?: string;
  /** Write output to stdout instead of file (single format only) */
  toStdout?: boolean;
  /** Number of worker threads for per-page processing. Default: 1 (sequential, stable). Values >1 (experimental) run pages in parallel for faster throughput; output may vary slightly on some PDFs. Capped at the number of available CPU cores. Applies to the native Java pipeline only; ignored in --hybrid mode */
  threads?: string | number;
  /** Set the rendering resolution for images in DPI. Higher values improve image quality but increase memory consumption; lower values reduce memory usage at the cost of detail. Accepts positive decimal DPI values (e.g., 144.0). Default: 144.0. */
  imageResolution?: string | number;
  /** Set the ratio used to calculate the automatic space-insertion threshold (threshold = space-ratio * font size). If the horizontal gap between two adjacent symbols exceeds this threshold, an extra space is inserted to text value. Accepts decimals (e.g., 0.17). Default: 0.17 */
  spaceRatio?: string | number;
}

/**
 * Options as parsed from CLI (all values are strings from commander).
 */
export interface CliOptions {
  outputDir?: string;
  password?: string;
  format?: string;
  quiet?: boolean;
  contentSafetyOff?: string;
  sanitize?: boolean;
  keepLineBreaks?: boolean;
  replaceInvalidChars?: string;
  useStructTree?: boolean;
  tableMethod?: string;
  readingOrder?: string;
  markdownPageSeparator?: string;
  markdownWithHtml?: boolean;
  textPageSeparator?: string;
  htmlPageSeparator?: string;
  imageOutput?: string;
  imageFormat?: string;
  imageDir?: string;
  pages?: string;
  includeHeaderFooter?: boolean;
  detectStrikethrough?: boolean;
  hybrid?: string;
  hybridMode?: string;
  hybridUrl?: string;
  hybridTimeout?: string;
  hybridFallback?: boolean;
  hybridHancomAiRegionlistStrategy?: string;
  hybridHancomAiOcrStrategy?: string;
  hybridHancomAiImageCache?: string;
  toStdout?: boolean;
  threads?: string;
  imageResolution?: string;
  spaceRatio?: string;
}

/**
 * Convert CLI options to ConvertOptions.
 */
export function buildConvertOptions(cliOptions: CliOptions): ConvertOptions {
  const convertOptions: ConvertOptions = {};

  if (cliOptions.outputDir !== undefined && cliOptions.outputDir !== null) {
    convertOptions.outputDir = cliOptions.outputDir;
  }
  if (cliOptions.password !== undefined && cliOptions.password !== null) {
    convertOptions.password = cliOptions.password;
  }
  if (cliOptions.format !== undefined && cliOptions.format !== null) {
    convertOptions.format = cliOptions.format;
  }
  if (cliOptions.quiet) {
    convertOptions.quiet = true;
  }
  if (cliOptions.contentSafetyOff !== undefined && cliOptions.contentSafetyOff !== null) {
    convertOptions.contentSafetyOff = cliOptions.contentSafetyOff;
  }
  if (cliOptions.sanitize) {
    convertOptions.sanitize = true;
  }
  if (cliOptions.keepLineBreaks) {
    convertOptions.keepLineBreaks = true;
  }
  if (cliOptions.replaceInvalidChars !== undefined && cliOptions.replaceInvalidChars !== null) {
    convertOptions.replaceInvalidChars = cliOptions.replaceInvalidChars;
  }
  if (cliOptions.useStructTree) {
    convertOptions.useStructTree = true;
  }
  if (cliOptions.tableMethod !== undefined && cliOptions.tableMethod !== null) {
    convertOptions.tableMethod = cliOptions.tableMethod;
  }
  if (cliOptions.readingOrder !== undefined && cliOptions.readingOrder !== null) {
    convertOptions.readingOrder = cliOptions.readingOrder;
  }
  if (cliOptions.markdownPageSeparator !== undefined && cliOptions.markdownPageSeparator !== null) {
    convertOptions.markdownPageSeparator = cliOptions.markdownPageSeparator;
  }
  if (cliOptions.markdownWithHtml) {
    convertOptions.markdownWithHtml = true;
  }
  if (cliOptions.textPageSeparator !== undefined && cliOptions.textPageSeparator !== null) {
    convertOptions.textPageSeparator = cliOptions.textPageSeparator;
  }
  if (cliOptions.htmlPageSeparator !== undefined && cliOptions.htmlPageSeparator !== null) {
    convertOptions.htmlPageSeparator = cliOptions.htmlPageSeparator;
  }
  if (cliOptions.imageOutput !== undefined && cliOptions.imageOutput !== null) {
    convertOptions.imageOutput = cliOptions.imageOutput;
  }
  if (cliOptions.imageFormat !== undefined && cliOptions.imageFormat !== null) {
    convertOptions.imageFormat = cliOptions.imageFormat;
  }
  if (cliOptions.imageDir !== undefined && cliOptions.imageDir !== null) {
    convertOptions.imageDir = cliOptions.imageDir;
  }
  if (cliOptions.pages !== undefined && cliOptions.pages !== null) {
    convertOptions.pages = cliOptions.pages;
  }
  if (cliOptions.includeHeaderFooter) {
    convertOptions.includeHeaderFooter = true;
  }
  if (cliOptions.detectStrikethrough) {
    convertOptions.detectStrikethrough = true;
  }
  if (cliOptions.hybrid !== undefined && cliOptions.hybrid !== null) {
    convertOptions.hybrid = cliOptions.hybrid;
  }
  if (cliOptions.hybridMode !== undefined && cliOptions.hybridMode !== null) {
    convertOptions.hybridMode = cliOptions.hybridMode;
  }
  if (cliOptions.hybridUrl !== undefined && cliOptions.hybridUrl !== null) {
    convertOptions.hybridUrl = cliOptions.hybridUrl;
  }
  if (cliOptions.hybridTimeout !== undefined && cliOptions.hybridTimeout !== null) {
    convertOptions.hybridTimeout = cliOptions.hybridTimeout;
  }
  if (cliOptions.hybridFallback) {
    convertOptions.hybridFallback = true;
  }
  if (cliOptions.hybridHancomAiRegionlistStrategy !== undefined && cliOptions.hybridHancomAiRegionlistStrategy !== null) {
    convertOptions.hybridHancomAiRegionlistStrategy = cliOptions.hybridHancomAiRegionlistStrategy;
  }
  if (cliOptions.hybridHancomAiOcrStrategy !== undefined && cliOptions.hybridHancomAiOcrStrategy !== null) {
    convertOptions.hybridHancomAiOcrStrategy = cliOptions.hybridHancomAiOcrStrategy;
  }
  if (cliOptions.hybridHancomAiImageCache !== undefined && cliOptions.hybridHancomAiImageCache !== null) {
    convertOptions.hybridHancomAiImageCache = cliOptions.hybridHancomAiImageCache;
  }
  if (cliOptions.toStdout) {
    convertOptions.toStdout = true;
  }
  if (cliOptions.threads !== undefined && cliOptions.threads !== null) {
    convertOptions.threads = cliOptions.threads;
  }
  if (cliOptions.imageResolution !== undefined && cliOptions.imageResolution !== null) {
    convertOptions.imageResolution = cliOptions.imageResolution;
  }
  if (cliOptions.spaceRatio !== undefined && cliOptions.spaceRatio !== null) {
    convertOptions.spaceRatio = cliOptions.spaceRatio;
  }

  return convertOptions;
}

/**
 * Build CLI arguments array from ConvertOptions.
 */
export function buildArgs(options: ConvertOptions): string[] {
  const args: string[] = [];

  if (options.outputDir !== undefined && options.outputDir !== null) {
    args.push('--output-dir', String(options.outputDir));
  }
  if (options.password !== undefined && options.password !== null) {
    args.push('--password', String(options.password));
  }
  if (options.format !== undefined && options.format !== null) {
    if (Array.isArray(options.format)) {
      if (options.format.length > 0) {
        args.push('--format', options.format.join(','));
      }
    } else {
      args.push('--format', String(options.format));
    }
  }
  if (options.quiet) {
    args.push('--quiet');
  }
  if (options.contentSafetyOff !== undefined && options.contentSafetyOff !== null) {
    if (Array.isArray(options.contentSafetyOff)) {
      if (options.contentSafetyOff.length > 0) {
        args.push('--content-safety-off', options.contentSafetyOff.join(','));
      }
    } else {
      args.push('--content-safety-off', String(options.contentSafetyOff));
    }
  }
  if (options.sanitize) {
    args.push('--sanitize');
  }
  if (options.keepLineBreaks) {
    args.push('--keep-line-breaks');
  }
  if (options.replaceInvalidChars !== undefined && options.replaceInvalidChars !== null) {
    args.push('--replace-invalid-chars', String(options.replaceInvalidChars));
  }
  if (options.useStructTree) {
    args.push('--use-struct-tree');
  }
  if (options.tableMethod !== undefined && options.tableMethod !== null) {
    args.push('--table-method', String(options.tableMethod));
  }
  if (options.readingOrder !== undefined && options.readingOrder !== null) {
    args.push('--reading-order', String(options.readingOrder));
  }
  if (options.markdownPageSeparator !== undefined && options.markdownPageSeparator !== null) {
    args.push('--markdown-page-separator', String(options.markdownPageSeparator));
  }
  if (options.markdownWithHtml) {
    args.push('--markdown-with-html');
  }
  if (options.textPageSeparator !== undefined && options.textPageSeparator !== null) {
    args.push('--text-page-separator', String(options.textPageSeparator));
  }
  if (options.htmlPageSeparator !== undefined && options.htmlPageSeparator !== null) {
    args.push('--html-page-separator', String(options.htmlPageSeparator));
  }
  if (options.imageOutput !== undefined && options.imageOutput !== null) {
    args.push('--image-output', String(options.imageOutput));
  }
  if (options.imageFormat !== undefined && options.imageFormat !== null) {
    args.push('--image-format', String(options.imageFormat));
  }
  if (options.imageDir !== undefined && options.imageDir !== null) {
    args.push('--image-dir', String(options.imageDir));
  }
  if (options.pages !== undefined && options.pages !== null) {
    args.push('--pages', String(options.pages));
  }
  if (options.includeHeaderFooter) {
    args.push('--include-header-footer');
  }
  if (options.detectStrikethrough) {
    args.push('--detect-strikethrough');
  }
  if (options.hybrid !== undefined && options.hybrid !== null) {
    args.push('--hybrid', String(options.hybrid));
  }
  if (options.hybridMode !== undefined && options.hybridMode !== null) {
    args.push('--hybrid-mode', String(options.hybridMode));
  }
  if (options.hybridUrl !== undefined && options.hybridUrl !== null) {
    args.push('--hybrid-url', String(options.hybridUrl));
  }
  if (options.hybridTimeout !== undefined && options.hybridTimeout !== null) {
    args.push('--hybrid-timeout', String(options.hybridTimeout));
  }
  if (options.hybridFallback) {
    args.push('--hybrid-fallback');
  }
  if (options.hybridHancomAiRegionlistStrategy !== undefined && options.hybridHancomAiRegionlistStrategy !== null) {
    args.push('--hybrid-hancom-ai-regionlist-strategy', String(options.hybridHancomAiRegionlistStrategy));
  }
  if (options.hybridHancomAiOcrStrategy !== undefined && options.hybridHancomAiOcrStrategy !== null) {
    args.push('--hybrid-hancom-ai-ocr-strategy', String(options.hybridHancomAiOcrStrategy));
  }
  if (options.hybridHancomAiImageCache !== undefined && options.hybridHancomAiImageCache !== null) {
    args.push('--hybrid-hancom-ai-image-cache', String(options.hybridHancomAiImageCache));
  }
  if (options.toStdout) {
    args.push('--to-stdout');
  }
  if (options.threads !== undefined && options.threads !== null) {
    args.push('--threads', String(options.threads));
  }
  if (options.imageResolution !== undefined && options.imageResolution !== null) {
    args.push('--image-resolution', String(options.imageResolution));
  }
  if (options.spaceRatio !== undefined && options.spaceRatio !== null) {
    args.push('--space-ratio', String(options.spaceRatio));
  }

  return args;
}
