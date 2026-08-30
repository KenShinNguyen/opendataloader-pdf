/**
 * Integration test for the CLI's exit path.
 *
 * The CLI used to end a failed run with `process.exit(code)`. Node writes to a
 * piped stdout/stderr asynchronously, and process.exit() discards whatever is
 * still buffered, so any streamed Java output beyond the pipe buffer (~64KB on
 * Linux) was silently truncated — exactly the case that matters for
 * `--to-stdout | jq` when the JAR then exits non-zero.
 *
 * Driving this through the real CLI needs a non-zero exit *after* a large
 * stdout payload, with the payload still queued when the CLI exits. Rather than depend on the Maven-built JAR, the test copies
 * dist/ into a temp package layout and puts a fake `java` first on PATH. The
 * CLI resolves its JAR as `<dist>/../lib/`, so the copy makes it pick up a
 * placeholder there — the repo's own lib/ is never touched, which keeps this
 * from interfering with the JAR-dependent suites running in parallel.
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { spawn, spawnSync } from 'child_process';
import { Writable } from 'stream';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const packageDir = path.resolve(__dirname, '..');
const builtCliPath = path.join(packageDir, 'dist', 'cli.js');

// Comfortably larger than a 64KB pipe buffer so truncation is unmissable.
const PAYLOAD_BYTES = 200_000;

let shimDir: string;
let sandboxDir: string;
let cliPath: string;
let inputPdf: string;

/**
 * The test needs the CLI's writes to still be queued when it exits — the state
 * process.exit() used to discard — which means nothing may drain its stdout for
 * a moment. Holding that off by leaving the stream paused and attaching the
 * reader on a timer loses the race: the stream is sometimes read to completion
 * and ended before the timer fires, and the payload is discarded with it, so the
 * test reads zero bytes and fails on a CLI that is perfectly correct.
 *
 * Attaching the sink at spawn removes the race — every chunk is captured the
 * moment it arrives. Backpressure comes from the sink not acknowledging its
 * writes for the first 500ms instead: the pipe still fills and the CLI still
 * queues, but nothing is read and thrown away. A CLI that discards its buffer on
 * exit still arrives truncated, which is what the assertion is looking for.
 */
const READ_HOLD_MS = 500;

function runCli(args: string[]): Promise<{ stdout: string; exitCode: number | null }> {
  return new Promise((resolve, reject) => {
    const proc = spawn('node', [cliPath, ...args], {
      // Prepend the shim dir so our fake `java` wins over any real one.
      env: { ...process.env, PATH: `${shimDir}${path.delimiter}${process.env.PATH ?? ''}` },
    });

    const chunks: Buffer[] = [];
    const heldWrites: Array<() => void> = [];
    let acknowledgeWrites = false;
    const sink = new Writable({
      // Ack one chunk at a time so backpressure reaches the pipe immediately.
      highWaterMark: 1,
      write(chunk: Buffer, _encoding, done) {
        chunks.push(chunk);
        if (acknowledgeWrites) {
          done();
        } else {
          heldWrites.push(done);
        }
      },
    });
    const holdTimer = setTimeout(() => {
      acknowledgeWrites = true;
      heldWrites.splice(0).forEach((done) => done());
    }, READ_HOLD_MS);

    // Drain stderr so the child never blocks on a full pipe.
    proc.stderr.on('data', () => {});
    proc.stdout.pipe(sink);

    let exitCode: number | null = null;
    let exited = false;
    let sinkFinished = false;
    const settle = () => {
      if (exited && sinkFinished) {
        clearTimeout(holdTimer);
        resolve({ stdout: Buffer.concat(chunks).toString(), exitCode });
      }
    };
    sink.on('finish', () => {
      sinkFinished = true;
      settle();
    });
    proc.on('exit', (code) => {
      exitCode = code;
      exited = true;
      settle();
    });
    proc.on('error', reject);
  });
}

describe('CLI exit path', () => {
  beforeAll(() => {
    if (!fs.existsSync(builtCliPath)) {
      const result = spawnSync(
        'pnpm',
        ['exec', 'tsup', '--no-dts', 'src/index.ts', 'src/cli.ts',
         '--format', 'esm,cjs', '--shims', '--out-dir', 'dist'],
        { cwd: packageDir, stdio: 'inherit' },
      );
      if (result.status !== 0) {
        throw new Error('Failed to build dist for CLI exit integration test');
      }
    }

    shimDir = fs.mkdtempSync(path.join(os.tmpdir(), 'odl-cli-exit-'));

    // Self-contained package layout: <sandbox>/dist/cli.js + <sandbox>/lib/<jar>.
    // The CLI looks for its JAR at `<dist>/../lib/`, so the placeholder here
    // satisfies its existence check without going near the repo's lib/.
    sandboxDir = fs.mkdtempSync(path.join(os.tmpdir(), 'odl-cli-sandbox-'));
    fs.cpSync(path.join(packageDir, 'dist'), path.join(sandboxDir, 'dist'), { recursive: true });
    fs.mkdirSync(path.join(sandboxDir, 'lib'), { recursive: true });
    // Contents are irrelevant — the fake `java` never reads it.
    fs.writeFileSync(path.join(sandboxDir, 'lib', 'opendataloader-pdf-cli.jar'), 'placeholder');
    // The bundle still imports `commander` as a bare specifier, which Node
    // resolves by walking up from the file — so the sandbox needs its own link
    // back to the real node_modules.
    fs.symlinkSync(
      path.join(packageDir, 'node_modules'),
      path.join(sandboxDir, 'node_modules'),
      'dir',
    );
    cliPath = path.join(sandboxDir, 'dist', 'cli.js');

    // Fake `java`: emit a large stdout payload, then fail. Mirrors a JAR that
    // streams output and exits non-zero (e.g. one input of several failed).
    const shim = path.join(shimDir, 'java');
    fs.writeFileSync(
      shim,
      `#!/bin/sh\nawk 'BEGIN { for (i = 0; i < ${PAYLOAD_BYTES}; i++) printf "x" }'\nexit 3\n`,
      { mode: 0o755 },
    );

    inputPdf = path.join(shimDir, 'input.pdf');
    fs.writeFileSync(inputPdf, '%PDF-1.4\n');
  }, 60000);

  afterAll(() => {
    if (shimDir) fs.rmSync(shimDir, { recursive: true, force: true });
    if (sandboxDir) fs.rmSync(sandboxDir, { recursive: true, force: true });
  });

  it('flushes all streamed stdout even when the JAR exits non-zero', async () => {
    const { stdout, exitCode } = await runCli([inputPdf]);

    // The whole payload must survive; process.exit() used to cut it at ~64KB.
    expect(stdout.length).toBe(PAYLOAD_BYTES);
    expect(exitCode).toBe(1);
  }, 30000);
});
