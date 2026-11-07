function completeStreamingBold(markdown) {
  let opener = -1;
  let fence = null;
  let inlineCode = 0;
  let linkDepth = 0;
  let bracketDepth = 0;
  let indentedCode = false;
  for (let i = 0; i < markdown.length;) {
    if (i === 0 || markdown[i - 1] === '\n') {
      const lineEnd = markdown.indexOf('\n', i);
      const line = markdown.slice(i, lineEnd < 0 ? markdown.length : lineEnd);
      const canStartCode = i === 0 || /\n[ \t]*\n$/.test(markdown.slice(0, i));
      if (!fence && !inlineCode && /^( {4}|\t)/.test(line) && (indentedCode || canStartCode)) {
        indentedCode = true;
        i = lineEnd < 0 ? markdown.length : lineEnd + 1;
        continue;
      }
      indentedCode = false;
      const match = markdown.slice(i).match(/^ {0,3}(`{3,}|~{3,})/);
      if (match && (!fence || (match[1][0] === fence[0] && match[1].length >= fence.length))) {
        fence = fence ? null : match[1];
        i += match[0].length;
        continue;
      }
    }
    if (fence) { i++; continue; }
    if (markdown[i] === '\\') { i += 2; continue; }
    if (linkDepth) {
      if (markdown[i] === '(') linkDepth++;
      if (markdown[i] === ')') linkDepth--;
      i++;
      continue;
    }
    if (!inlineCode && markdown[i] === '[') bracketDepth++;
    if (!inlineCode && markdown[i] === ']' && bracketDepth > 0) {
      bracketDepth--;
      if (markdown[i + 1] === '(') {
        linkDepth = 1;
        i += 2;
        continue;
      }
    }
    if (markdown[i] === '`') {
      let end = i + 1;
      while (markdown[end] === '`') end++;
      const length = end - i;
      if (!inlineCode) inlineCode = length;
      else if (inlineCode === length) inlineCode = 0;
      i = end;
      continue;
    }
    if (!inlineCode && markdown.startsWith('**', i)) {
      if (opener >= 0 && i > opener + 2 && !/\s/.test(markdown[i - 1])) opener = -1;
      else if (opener < 0 && (i + 2 === markdown.length || !/\s/.test(markdown[i + 2]))) opener = i;
      i += 2;
      continue;
    }
    i++;
  }
  if (opener < 0 || fence || inlineCode) return markdown;
  if (opener + 2 === markdown.length) return markdown.slice(0, opener);
  const trailing = markdown.match(/\s*$/)[0];
  const content = markdown.slice(0, markdown.length - trailing.length);
  return content + (content.endsWith('*') && !content.endsWith('**') ? '*' : '**') + trailing;
}

function markdownProps(markdown, streaming, textColor, accent, codeBackground) {
  return {
    markdown: streaming ? completeStreamingBold(markdown) : markdown,
    streamingAnimation: streaming,
    textColor,
    accent,
    codeBackground,
  };
}

module.exports = {markdownProps};
