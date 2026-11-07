const {test} = require('node:test');
const assert = require('node:assert/strict');
const {createStreamingReveal} = require('./streaming-reveal');

function create() { return createStreamingReveal(); }

test('reveals gradually and delivers the complete final snapshot', () => {
  const reveal = create();
  reveal.update('abcdefghijklmnopqrst', true, 0);
  assert.equal(reveal.tick(0).text, '');
  const middle = reveal.tick(100).text;
  assert.ok(middle.length > 0 && middle.length < 20);
  reveal.update('abcdefghijklmnopqrst', false, 100);
  assert.deepEqual(reveal.tick(1000), {text: 'abcdefghijklmnopqrst', streaming: false});
});

test('keeps streaming active until the tail fade finishes', () => {
  const reveal = create();
  reveal.update('abcdef', true, 0);
  reveal.update('abcdef', false, 0);
  assert.equal(reveal.tick(240).streaming, true);
  assert.equal(reveal.tick(550).streaming, false);
});

test('accelerates after a burst and bounds its drain time', () => {
  const reveal = create();
  reveal.update('a'.repeat(10), true, 0);
  reveal.tick(100);
  reveal.update('a'.repeat(200), true, 100);
  const early = reveal.tick(200).text.length;
  assert.ok(early > 10 && early < 200);
  assert.equal(reveal.tick(700).text.length, 200);
});

test('drains a large burst within 800 ms', () => {
  const reveal = create();
  reveal.update('a'.repeat(2000), true, 0);
  for (let time = 16; time <= 800; time += 16) reveal.tick(time);
  assert.equal(reveal.tick(800).text.length, 2000);
});

test('does not leave queued text stalled after repeated bursts', () => {
  const reveal = create();
  let source = '';
  for (let time = 0; time <= 490; time += 35) {
    source += 'palavra ';
    reveal.update(source, true, time);
    reveal.tick(time);
  }
  reveal.update(source, false, 525);
  for (let time = 541; time <= 1149; time += 16) reveal.tick(time);
  assert.deepEqual(reveal.tick(1150), {text: source, streaming: false});
});

test('discards the old queue when the snapshot is replaced', () => {
  const reveal = create();
  reveal.update('antigo comprido', true, 0);
  reveal.tick(100);
  reveal.update('novo', true, 100);
  assert.equal(reveal.tick(1000).text, 'novo');
});

test('shows history and non-streaming updates immediately', () => {
  const reveal = create();
  reveal.update('histórico', false, 0);
  assert.deepEqual(reveal.tick(0), {text: 'histórico', streaming: false});
  reveal.update('histórico completo', false, 20);
  assert.deepEqual(reveal.tick(20), {text: 'histórico completo', streaming: false});
});

test('keeps combining graphemes together across snapshots', () => {
  const reveal = create();
  reveal.update('e', true, 0);
  reveal.tick(600);
  reveal.update('e\u0301abc', true, 600);
  assert.equal(reveal.tick(600).text, 'e\u0301');
  assert.equal(reveal.tick(1200).text, 'e\u0301abc');
});

test('never reveals part of a joined emoji sequence', () => {
  const reveal = create();
  const family = '👩‍👩‍👧‍👦';
  reveal.update(family.repeat(10), true, 0);
  const text = reveal.tick(100).text;
  assert.equal(text, family.repeat(text.length / family.length));
});

test('segments graphemes without Intl.Segmenter', () => {
  const original = Intl.Segmenter;
  try {
    Intl.Segmenter = undefined;
    const reveal = create();
    const text = '👩‍👩‍👧‍👦'.repeat(10);
    reveal.update(text, true, 0);
    const partial = reveal.tick(100).text;
    assert.ok(partial.length > 0 && partial.length < text.length);
    assert.equal(partial, '👩‍👩‍👧‍👦'.repeat(partial.length / '👩‍👩‍👧‍👦'.length));
  } finally {
    Intl.Segmenter = original;
  }
});

test('never splits Unicode surrogate pairs', () => {
  const reveal = create();
  reveal.update('😀'.repeat(10), true, 0);
  const text = reveal.tick(100).text;
  assert.ok(!/[\uD800-\uDBFF]$/.test(text));
  assert.equal(reveal.tick(1000).text, '😀'.repeat(10));
});
