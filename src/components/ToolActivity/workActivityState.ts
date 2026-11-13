export function hasFinalMessageAfterTools(items: {kind: string; text: string; complete?: boolean}[], status: string) {
  if (status !== 'completed') return false;
  let lastTool = -1;
  items.forEach((item, index) => { if (item.kind === 'tool_call') lastTool = index; });
  return items.slice(lastTool + 1).some(item => item.kind === 'text' && item.complete === true && item.text.trim().length > 0);
}
