# Validação do renderer Markdown — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to carry out this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Confirmar que a integração própria `MarkdownMessage` usa Mike Penz para Markdown e syntax highlighting e apresenta corretamente snapshots sucessivos de streaming.

**Architecture:** O código atual já mantém `MarkdownMessage` como adapter do tema e usa `multiplatform-markdown-renderer` como motor. O trabalho começa verificando essa implementação existente; só haverá mudança de produção se uma regressão reproduzível for encontrada, sempre com teste primeiro.

**Tech Stack:** Kotlin, Jetpack Compose, Mike Penz `multiplatform-markdown-renderer` 0.35.0, JetBrains Markdown parser, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-07-markdown-message-renderer-design.md`

## Global Constraints

- Manter Mike Penz como motor Markdown e syntax highlighting.
- Manter `MarkdownMessage` como camada de tema e integração do app.
- Não criar parser, renderer, tokenizer, ou modelo de mensagem paralelo.
- Não alterar backend, formato dos eventos, persistência, auto-scroll ou estilos gerais do chat.
- Não instalar APK durante a validação.
- Reprocessamento/cache só será considerado se uma resposta longa demonstrar custo perceptível.

## Review Focus

- Snapshots substituídos deixam texto antigo visível — testar snapshots parciais, correção e substituição integral.
- Snapshot vazio deixa conteúdo anterior visível — incluir o caso vazio na sequência de snapshots.
- Code fence aberto durante streaming falha ou perde o código — manter o teste de fence sem fechamento.
- A composição não atualiza ao receber texto novo — verificar o comportamento visual durante streaming; os testes de parser isolados não provam recomposição.
- Syntax highlighting ou dimensões dos blocos mudam — inspecionar um code fence realçado na UI; `assembleDebug` sozinho não prova aparência.

---

### Task 1: Confirmar cobertura de snapshots do parser

**Files:**
- Inspect: `app/src/test/java/com/fcc/claude/MarkdownRendererTest.kt`
- Modify only if a specific missing assertion is identified: `app/src/test/java/com/fcc/claude/MarkdownRendererTest.kt`

**Interfaces:**
- Consumes: `MarkdownParser(GFMFlavourDescriptor())` e `MarkdownElementTypes` já usados pelo teste.
- Produces: evidência automatizada de que snapshots independentes, incluindo sintaxe parcial, texto corrigido, snapshot vazio e fence aberto, são aceitos pelo parser.

- [ ] **Step 1: Conferir os casos cobertos**

Confirmar que `markdownParserKeepsLatestStreamingSnapshotIncludingPartialSyntax` percorre resposta parcial, sintaxe incompleta, texto corrigido e snapshot vazio, e que `markdownParserRecognizesAnUnclosedCodeFenceDuringStreaming` reconhece um fence ainda aberto.

- [ ] **Step 2: Rodar o teste existente antes de qualquer alteração**

Run: `./gradlew :app:testDebugUnitTest --tests com.fcc.claude.MarkdownRendererTest`
Expected: PASS. Se falhar, registrar a falha real antes de alterar código.

- [ ] **Step 3: Alterar teste somente se faltar um caso da lista acima**

Escrever uma única asserção para o caso efetivamente ausente, usando parser real e sem mocks. Não duplicar os cenários já presentes no arquivo.

- [ ] **Step 4: Se um teste novo foi escrito, observar RED e depois GREEN**

Run antes da mudança de produção: `./gradlew :app:testDebugUnitTest --tests com.fcc.claude.MarkdownRendererTest`
Expected: o novo caso falha pela razão esperada. Se for necessário código de produção, implementar o menor ajuste possível e repetir o comando até PASS.

### Task 2: Confirmar dependências e build

**Files:**
- Inspect: `app/build.gradle.kts`
- No dependency changes expected.

**Interfaces:**
- Consumes: dependências Mike Penz existentes `multiplatform-markdown-renderer-android`, `multiplatform-markdown-renderer-m3` e `multiplatform-markdown-renderer-code`.
- Produces: confirmação de que a compilação mantém o renderer e o módulo de syntax highlighting configurados.

- [ ] **Step 1: Executar a suíte unitária Android**

Run: `./gradlew :app:testDebugUnitTest`
Expected: todos os testes passam. Reportar cada falha pelo nome; não ocultar falhas preexistentes.

- [ ] **Step 2: Compilar a variante debug**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL sem adicionar ou remover dependências.

### Task 3: Verificar integração de streaming e apresentação

**Files:**
- Inspect: `app/src/main/java/com/fcc/claude/MarkdownMessage.kt`
- Inspect: `app/src/main/java/com/fcc/claude/ChatMessageComponents.kt`
- No production changes expected unless a reproducible defect is found.

**Interfaces:**
- Consumes: `MessageCard` encaminha `item.text` para `MarkdownMessage`; `MarkdownMessage` entrega o snapshot ao `Markdown` de Mike Penz.
- Produces: confirmação visual de atualização de snapshot, syntax highlighting e coexistência com a rolagem atual.

- [ ] **Step 1: Conferir o caminho de atualização**

Verificar que `MessageCard` passa o texto atual de `ChatItem` diretamente para `MarkdownMessage` e que o componente encaminha esse valor como `content` ao renderer, sem acumular snapshots localmente.

- [ ] **Step 2: Exercitar uma resposta em streaming na UI existente**

Usar uma resposta que mostre texto parcial, depois um code fence aberto e finalmente seu conteúdo atualizado/fechado. Confirmar que o texto visível corresponde ao snapshot mais recente, sem conteúdo obsoleto.

- [ ] **Step 3: Inspecionar código destacado e rolagem**

Confirmar visualmente que o code fence usa syntax highlighting, mantém dimensões legíveis e não prejudica a rolagem de uma resposta longa. Não instalar APK como parte desta validação.

- [ ] **Step 4: Corrigir somente defeito reproduzido, com TDD**

Se houver defeito, primeiro criar teste que o reproduza e observar a falha; depois implementar o ajuste mínimo, executar a suíte unitária completa e `:app:assembleDebug`. Não alterar política de auto-scroll ou outras áreas fora da integração Markdown.

## Critério de conclusão

Concluir quando os testes unitários e `assembleDebug` passarem e a inspeção visual confirmar snapshots atuais, syntax highlighting e scroll. Se a UI não estiver disponível para inspeção, registrar esse bloqueio explicitamente; build verde não substitui validação visual.
