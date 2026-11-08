# React Native chat migration design

## Goal

Replace the app's Compose interface with one React Native interface while keeping the current FCC session, streaming, attachment, and tool behavior.

## Selected UI package

Use the official `@copilotkit/react-native` `CopilotMarkdown` component with `react-native-streamdown` for Markdown rendering during incremental FCC responses. Keep the message transcript and composer app-owned so they can consume the existing FCC snapshot and preserve the open, assistant-first layout. Do not use the generic `@kesha-antonov/react-native-chat` message UI.

`CopilotChat` is not the selected screen: its default assistant renderer uses message bubbles and its runtime expects AG-UI. The existing FCC bridge remains the source of truth for sessions, messages, tools, permissions, attachments, and streaming; this migration does not add a second agent runtime or server. CopilotKit's renderer is used directly for assistant Markdown.

The verified CopilotKit package supports React 18/19 and RN >=0.70, but its current `react-native-streamdown` peer requires `react-native-worklets >=0.8.3`, whose published peer range starts at RN 0.81. Align the project on React 19.1 / React Native 0.81.5 to meet that native renderer requirement. This upgrade is part of the integration and must be build-verified.

Enable the new architecture and use Reanimated 4.3 / Worklets 0.8.3 with the renderer's required Babel/Metro Bundle Mode. Use the official Hermes Android runtime. Termux builds disable only bytecode compilation through `FCC_COMPILE_HERMES_BYTECODE=false`; Hermes executes the Metro JavaScript bundle directly, avoiding the distributed x86_64 host compiler. Build variables live in the ignored local `.env`, loaded by `sh scripts/build-debug.sh`.

The package does not document built-in chart rendering. Charts are explicitly deferred to a later phase, as requested. Do not claim charts are covered by the package or add a chart renderer/dependency in the chat migration phase. Keep the message integration open to adding charts later. Do not use a WebView.

## Architecture

- Make React Native the root UI for the app, including chat, session navigation, settings, permission prompts, and the existing Tools screen. Keep the existing Android entry points needed for crash reporting and platform intents.
- Preserve `ChatViewModel` and `ChatRepository` as the owners of sessions, history, SSE events, streaming runs, attachments, and tool actions. Expose those operations and observable state to JavaScript through a small Android native module/event bridge; do not duplicate endpoint/state logic in JavaScript.
- Use `CopilotMarkdown` only for assistant content. Keep the FCC transcript, composer, streaming indicator, and stop action connected to the existing native bridge. Keep assistant text open across the available width; keep user messages visually distinct without using messenger-style chat bubbles.
- Use React Native screens for app-level navigation and project/session controls. The Tools feature keeps its current behavior and payloads.
- Charts are a follow-up phase after the React Native chat migration; inspect the existing FCC/tool payloads before selecting a renderer. Normal code blocks remain code in this phase.

## Constraints

- Android only for this migration; no WebView.
- Preserve the current visual identity and backend URL/protocol.
- Do not remove existing functionality or pre-existing screenshots.
- Android compatibility of React Native 0.81.5, `react-native-streamdown`, and the native markdown renderer must be verified by the app build. Do not use a WebView.

## Success criteria

- The app has one React Native UI surface rather than a Compose/React Native hybrid.
- Session selection, history, live events, streaming, stop, attachments, permissions, settings, and tools still use the current FCC behavior.
- Assistant Markdown streams through CopilotKit's renderer without the prior heading/list/code formatting regressions.
- The chat message integration leaves a clear extension point for a later chart renderer; chart rendering itself is not a criterion for this phase.
- Android debug build succeeds and the app installs/opens on the current device.
