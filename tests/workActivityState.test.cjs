const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const exportsObject = {};
vm.runInNewContext(ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname, '../src/components/ToolActivity/workActivityState.ts'), 'utf8'), {
  compilerOptions: {module: ts.ModuleKind.CommonJS},
}).outputText, {exports: exportsObject});
const {hasFinalMessageAfterTools} = exportsObject;
const tool = {kind: 'tool_call', text: '{}', complete: true};
test('stopped work without an answer cannot be treated as a final message', () => {
  assert.equal(hasFinalMessageAfterTools([{kind: 'text', text: 'Vou verificar.', complete: true}, tool], 'interrupted'), false);
  assert.equal(hasFinalMessageAfterTools([tool, {kind: 'reasoning', text: 'pensando', complete: true}], 'completed'), false);
});
test('empty or partial response does not enable collapsing work', () => {
  assert.equal(hasFinalMessageAfterTools([tool, {kind: 'text', text: '   ', complete: true}], 'completed'), false);
  assert.equal(hasFinalMessageAfterTools([tool, {kind: 'text', text: 'Começando', complete: false}], 'completed'), false);
});
test('a complete answer after the last tool enables collapsing work', () => {
  assert.equal(hasFinalMessageAfterTools([tool, {kind: 'text', text: 'Concluído.', complete: true}], 'completed'), true);
  assert.equal(hasFinalMessageAfterTools([tool, {kind: 'text', text: 'Vou tentar novamente.', complete: true}, tool], 'completed'), false);
});

test('interruption does not turn an intermediate text into a final answer', () => {
  assert.equal(hasFinalMessageAfterTools([tool, {kind: 'text', text: 'Vou continuar.', complete: true}], 'interrupted'), false);
});
