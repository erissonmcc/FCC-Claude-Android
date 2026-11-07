const {collectGraphemes} = require('unicode-segmenter/grapheme');

function createStreamingReveal() {
  let source = '';
  let units = [];
  let visible = 0;
  let lastTime = 0;
  let streaming = false;
  let initialized = false;
  let deadline = 0;
  let fadeUntil = 0;

  function tick(now) {
    const elapsed = Math.max(0, now - lastTime);
    const remaining = units.length - visible;
    const rate = Math.max(25, remaining * 1000 / Math.max(1, deadline - lastTime));
    const previousCount = Math.floor(visible);
    visible = Math.min(units.length, visible + elapsed * rate / 1000);
    const count = Math.floor(visible);
    if (count > previousCount) {
      const finishedAt = lastTime + Math.min(elapsed, remaining * 1000 / rate);
      fadeUntil = finishedAt + 300;
    }
    lastTime = now;
    return {text: units.slice(0, count).join(''), streaming: streaming || count < units.length || now < fadeUntil};
  }

  function update(text, active, now) {
    if (initialized) tick(now);
    const immediate = !active && !streaming && visible >= units.length;
    const empty = visible >= units.length;
    const replaced = !text.startsWith(source);
    if (replaced) {
      visible = 0;
      fadeUntil = 0;
    }
    source = text;
    units = collectGraphemes(text);
    if ((empty || replaced) && visible < units.length) deadline = now + 600;
    streaming = active;
    if (immediate) visible = units.length;
    initialized = true;
    lastTime = now;
  }

  return {update, tick};
}

module.exports = {createStreamingReveal};
