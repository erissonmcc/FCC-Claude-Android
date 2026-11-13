const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const exportsObject = {};
vm.runInNewContext(ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname, '../src/components/ErrorBanner/errorPresentation.ts'), 'utf8'), {
  compilerOptions: {module: ts.ModuleKind.CommonJS},
}).outputText, {exports: exportsObject});
const {errorPreview, formatActionError} = exportsObject;
test('huge errors have a short plain-text preview while the source remains intact', () => {
  const source = 'Falhou\n' + 'x'.repeat(2_000_000);
  const preview = errorPreview(source);
  assert.ok(preview.length <= 401);
  assert.ok(preview.startsWith('Falhou '));
  assert.ok(preview.endsWith('…'));
  assert.equal(source.length, 2_000_007);
});
test('action errors retain context, native error code and stack', () => {
  const report = formatActionError('Salvar esforço', {message: 'FCC (409): revision conflict', code: 'FCC_ACTION', stack: 'Error at ChatScreen.tsx:42'});
  assert.ok(report.includes('Salvar esforço'));
  assert.ok(report.includes('FCC_ACTION'));
  assert.ok(report.includes('409'));
  assert.ok(report.includes('ChatScreen.tsx:42'));
});
test('diagnostic payload stays bounded even for very large native errors', () => {
  assert.ok(formatActionError('Salvar esforço', {message: 'x'.repeat(2_000_000), stack: 'y'.repeat(2_000_000)}).length <= 32768);
});
