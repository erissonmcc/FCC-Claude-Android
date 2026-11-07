const {test} = require('node:test');
const assert = require('node:assert/strict');
const {markdownProps} = require('./markdown-props');

test('delivers complete Markdown snapshots and tracks streaming animation', () => {
  assert.deepEqual(markdownProps('Olá **mundo**', true, '#eeeeee', '#d5bfa3', '#25272b'), {
    markdown: 'Olá **mundo**', streamingAnimation: true, textColor: '#eeeeee', accent: '#d5bfa3', codeBackground: '#25272b',
  });
  assert.deepEqual(markdownProps('Oi', false, '#fff', '#abc', '#222'), {
    markdown: 'Oi', streamingAnimation: false, textColor: '#fff', accent: '#abc', codeBackground: '#222',
  });
});

test('provisionally closes open bold markers only while streaming', () => {
  assert.equal(markdownProps('Antes **texto', true).markdown, 'Antes **texto**');
  assert.equal(markdownProps('Antes **texto ', true).markdown, 'Antes **texto** ');
  assert.equal(markdownProps('Antes **', true).markdown, 'Antes ');
  assert.equal(markdownProps('Antes **texto', false).markdown, 'Antes **texto');
});

test('does not alter asterisks in links, escaped text, or code', () => {
  for (const markdown of [
    '[site](https://example.com/a**b)',
    '\\**literal',
    '`**literal`',
    '```\n**literal\n```',
    '    **literal',
  ]) {
    assert.equal(markdownProps(markdown, true).markdown, markdown);
  }
});
