# Chat attachments design

## Goal

Let a user attach images and files to an FCC Claude chat from Android. The agent must be able to read the attachment during that turn, and temporary server-side copies must be removed with the session.

## User flow

1. A `+` button at the far left of the composer opens Android's document picker and allows multiple files, including images.
2. Chosen items appear in the composer as removable attachment chips (with an image preview where practical). Removing a chip removes it from the pending turn.
3. Sending uploads the selected content to the FCC server for that conversation, then sends the user's text with safe, server-generated local attachment paths for Claude to inspect.
4. If upload or turn submission fails, keep the draft and selected attachments available for retry; do not submit a prompt that references missing files.

## Storage and lifecycle

- The Android app streams each selected URI to a new FCC upload endpoint; it does not need broad storage permission.
- FCC stores copies in a private temporary directory keyed by session and attachment ID. Never trust a client filename as a path; sanitize display names and generate storage names server-side.
- Session deletion removes its temporary attachments. Add bounded file size/count checks and reject unsupported or empty uploads with a visible error.
- Claude receives paths only from the FCC server's validated session directory. Existing tool-result UI remains hidden; attachment chips represent the user-provided files.

## Alternatives considered

- Keep files in shared Android storage and pass shared paths: simpler, but exposes copies and depends on Termux access to Android storage.
- Send raw image/file content through the agent protocol: avoids persistent paths but requires a broader SDK/protocol change and complicates non-image files.

## Scope

Implement the composer picker/chips, multipart upload API, temporary per-session storage and cleanup, and inclusion of validated paths in the turn prompt. Preserve the existing chat and websocket streaming behavior. Do not add a persistent attachment library or general file browser.

## Composer visual adjustment

- Add the attachment action to the existing composer, keeping the FCC Claude app's current dark palette, Lucide icon family, rounded shape, and model/reasoning/send controls.
- Reduce the visual weight of the current accent-colored outline so the input feels integrated with the existing interface; do not copy another app's exact styling.
- Keep attachment chips readable and removable without making the multiline composer or its existing controls cramped.

## Histórico ao abrir/trocar de conversa

- Ao abrir ou trocar de conversa, manter o histórico atual fora da lista até o snapshot completo da nova conversa estar disponível; então substituir a lista de uma vez.
- Exibir skeleton loading na área de mensagens durante esse carregamento, usando as formas, cores e espaçamentos do FCC Claude.
- Não alterar o streaming incremental de mensagens novas do Claude.

## Acceptance criteria

- User can select one or several supported files and images; canceling selection changes nothing.
- Attachments can be removed before sending and are uploaded with the intended conversation.
- Claude receives usable references and can inspect the selected image/file.
- Upload failure does not send a broken turn and leaves the draft retryable.
- Session deletion removes that session's temporary attachment files.
- Oversized/unsupported inputs produce a clear error and do not leave partial files behind.
