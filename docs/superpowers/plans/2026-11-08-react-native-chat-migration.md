# React Native Chat Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the app's Compose interface with a React Native interface and preserve the FCC chat behavior.

**Architecture:** React Native becomes the single user-facing UI. An Android native module exposes the existing Kotlin `ChatViewModel` and `ChatRepository` state/actions to JavaScript, so sessions, SSE, turns, attachments, and tools keep their existing backend implementation.

**Tech Stack:** React Native CLI 0.81.5, React 19.1.0, TypeScript, `@copilotkit/react-native@1.85.0` `CopilotMarkdown`, `react-native-streamdown`, existing Kotlin/JVM Android app.

**Spec:** `docs/superpowers/specs/2026-11-08-react-native-chat-migration-design.md`

## Global Constraints

- No WebView.
- Do not mix Compose and React Native UI in the normal app flow.
- Keep the current FCC backend at `http://127.0.0.1:8082` and preserve the Kotlin session/streaming/tool logic.
- Keep the existing visual identity and app behavior.
- Charts are a follow-up phase and are outside this migration.
- Preserve all five pre-existing untracked screenshots.
- Do not add/run tests in this change; verify with Android compilation and installation requested earlier.

## Review Focus

- A JS reload or Android Activity recreation must not duplicate or lose the active run/session; keep ownership in the Kotlin ViewModel and test by restarting the React surface during an active session.
- An SSE reconnect or epoch resync must deliver a fresh state snapshot; exercise by disconnecting/reconnecting the FCC feed during a conversation.
- Messages with pending attachments must upload from the RN picker and retain retry behavior; verify a document attachment send.
- Permission prompts and queued messages must not be lost during screen changes; exercise approval/denial and send-while-running flows.
- Tool messages and structured arguments must remain visible and expandable in React Native; verify a session containing a tool call.

---

### Task 1: Add the React Native Android shell

**Files:**
- Create: `package.json`, `index.js`, `babel.config.js`, `metro.config.js`, `src/App.tsx`
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/java/com/fcc/claude/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, Gradle wrapper files as required by the selected RN baseline
- Keep: project checkout at `/data/data/com.termux/files/home/Claude-Code`

- [ ] Configure RN 0.76.x with React 18.3 and the app-compatible native toolchain; install the selected chat package v5.0.1 and required non-optional peers. The package metadata supports React >=18 and has no RN version floor.
- [ ] Make `MainActivity` host the RN root view and pass existing FCC launch arguments/cwd into the JS entry point.
- [ ] Keep crash-report process behavior available without rendering the normal chat screen through Compose.
- [ ] Verify Metro starts and `:app:assembleDebug` compiles the native shell.

### Task 2: Bridge the existing Kotlin chat state and actions

**Files:**
- Create: `app/src/main/java/com/fcc/claude/FccChatPackage.kt`, `app/src/main/java/com/fcc/claude/FccChatModule.kt`
- Modify: `app/src/main/java/com/fcc/claude/ChatViewModel.kt`, `app/src/main/java/com/fcc/claude/MainActivity.kt`, `app/src/main/java/com/fcc/claude/CrashApplication.kt` only if RN application initialization requires it
- Create: `src/native/FccChat.ts`

- [ ] Expose an initial serializable snapshot and change events from the existing ViewModel to React Native.
- [ ] Expose the current session, history, send/stop, settings, permission response, attachment, and tool related actions without duplicating repository endpoints in JavaScript.
- [ ] Preserve lifecycle/reconnect behavior and avoid creating a second ViewModel for the same Activity.
- [ ] Compile Android after adding the native module.

### Task 3: Move app screens to React Native

**Files:**
- Create/modify: `src/App.tsx`, `src/screens/ChatScreen.tsx`, `src/screens/SessionDrawer.tsx`, `src/screens/SettingsScreen.tsx`, `src/components/ToolCard.tsx`
- Modify: Android navigation/activity entry as needed

- [ ] Remove `@kesha-antonov/react-native-chat`; use an app-owned open transcript/composer backed by the FCC bridge and CopilotKit's `CopilotMarkdown` for assistant Markdown.
- [ ] Upgrade the RN/React packages to RN 0.81.5 and React 19.1.0 for the official markdown renderer's `react-native-worklets` native peer.
- [ ] Recreate session navigation, settings, permission prompts, pending messages, and expandable tool cards in React Native using the existing colors and behavior.
- [ ] Keep charts out of this phase.
- [ ] Remove the Stream SDK, Compose Markdown renderer, and Compose chat UI dependencies/code once no active UI path uses them; retain only platform/business-logic code still required by the bridge or crash handling.
- [ ] Build and install the debug APK, then verify session load, Markdown, streaming/stop, attachment, permission, settings, and tools on device.

### Task 4: Final compatibility and cleanup

**Files:**
- Modify only files made obsolete by this migration.

- [ ] Remove unused Compose UI dependencies and repository declarations only after confirming the build no longer references them.
- [ ] Run `./gradlew assembleDebug` from the Android project directory and confirm successful APK output.
- [ ] Install through the existing Termux/ADB workflow and confirm Android reports `Success`.
- [ ] Review `git status` to ensure only intended changes are present and all pre-existing screenshots remain untouched.
