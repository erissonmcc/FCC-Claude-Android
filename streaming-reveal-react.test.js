const {test} = require('node:test');
const assert = require('node:assert/strict');
const React = require('react');
const {act, create} = require('react-test-renderer');
global.IS_REACT_ACT_ENVIRONMENT = true;
const {useStreamingReveal} = require('./use-streaming-reveal');

function View({text, active}) {
  const result = useStreamingReveal(text, active);
  return React.createElement('output', result);
}

test('shows history immediately and does not retain replaced text', async () => {
  let root;
  await act(async () => { root = create(React.createElement(View, {text: 'histórico', active: false})); });
  assert.equal(root.root.findByType('output').props.text, 'histórico');
  await act(async () => root.update(React.createElement(View, {text: 'novo', active: true})));
  assert.equal(root.root.findByType('output').props.text, '');
  await act(async () => root.unmount());
});

test('reveals gradually and finishes with the exact snapshot', async () => {
  let root;
  await act(async () => { root = create(React.createElement(View, {text: 'abcdefghijklmnopqrstuvwxyz', active: true})); });
  assert.ok(root.root.findByType('output').props.text.length < 26);
  await act(async () => root.update(React.createElement(View, {text: 'abcdefghijklmnopqrstuvwxyz', active: false})));
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 1050)); });
  assert.deepEqual(root.root.findByType('output').props, {
    text: 'abcdefghijklmnopqrstuvwxyz', streaming: false,
  });
  await act(async () => root.unmount());
});
