export function errorPreview(error: string) {
  return error.slice(0, 400).replace(/\s+/g, ' ').trim() + (error.length > 400 ? '…' : '');
}

export function formatActionError(operation: string, error: unknown) {
  const details = (typeof error === 'object' && error !== null ? error : {}) as {message?: unknown; code?: unknown; stack?: unknown};
  const message = typeof details.message === 'string' ? details.message : String(error);
  const code = typeof details.code === 'string' ? `Código: ${details.code.slice(0, 128)}` : '';
  const stack = typeof details.stack === 'string' ? details.stack.slice(0, 8192) : '';
  return [`Operação: ${operation.slice(0, 128)}`, code, message.slice(0, 24576), stack].filter(Boolean).join('\n').slice(0, 32768);
}
