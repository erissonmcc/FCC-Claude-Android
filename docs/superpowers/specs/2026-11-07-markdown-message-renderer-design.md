# Renderer Markdown de mensagens — especificação de design

## Objetivo

Manter uma integração própria de mensagens Markdown no app Compose sem reimplementar parser, layout Markdown ou syntax highlighting. A biblioteca `multiplatform-markdown-renderer` de Mike Penz é permitida e será mantida como motor de renderização.

## Contexto confirmado

- `MarkdownMessage.kt` já expõe o componente interno `MarkdownMessage(text, textColor, accent, codeBackground, modifier)`.
- O componente configura cores e tipografia e delega o conteúdo ao `Markdown` de Mike Penz.
- `highlightedCodeFence` e `highlightedCodeBlock` já são configurados para blocos de código com syntax highlighting.
- `MessageCard` usa `MarkdownMessage` para itens do tipo `text`.
- `MarkdownRendererTest.kt` cobre snapshots de texto parcial e fence de código ainda aberto usando o parser JetBrains Markdown.
- `app/build.gradle.kts` contém os módulos Mike Penz `multiplatform-markdown-renderer-android`, `multiplatform-markdown-renderer-m3` e `multiplatform-markdown-renderer-code`, versão `0.35.0`.

## Arquitetura

### Camada do app: `MarkdownMessage`

Permanece como fronteira própria do app entre `ChatItem.text` e a biblioteca. Responsabilidades:

- receber o snapshot de texto atual e as cores/estilos do tema;
- mapear tema e tipografia para as APIs do renderer;
- selecionar os componentes de código com syntax highlighting;
- encaminhar `Modifier` e ocupar a largura disponível conforme o consumidor atual.

Não deve implementar parsing Markdown, tokenizer de linguagens ou modelos de mensagem paralelos.

### Motor: Mike Penz

A dependência continua responsável por interpretar Markdown e produzir UI Compose. O parser subjacente é JetBrains Markdown. O módulo de código continua habilitado para renderizar code fences e blocos destacados. Não introduzir outro parser ou outra biblioteca de syntax highlighting nesta mudança.

### Fluxo de dados

`ChatItem.text` atualizado → `MessageCard` → `MarkdownMessage` → renderer Mike Penz.

Cada atualização do texto substitui o snapshot exibido; não concatenar snapshots no componente. O renderer deve mostrar o conteúdo mais recente mesmo quando ele contém sintaxe parcial, correção/revisão ou fence ainda sem fechamento. A resposta final deve manter o mesmo caminho de renderização.

## Requisitos de comportamento

1. Preservar estilo atual para texto, parágrafos, links, código inline, blocos de código e tabelas configuradas.
2. Preservar syntax highlighting para code fences e code blocks.
3. Renderizar o snapshot mais recente do streaming sem deixar conteúdo exclusivo de snapshots anteriores.
4. Não exigir que a sintaxe esteja completa para mostrar texto parcial.
5. Manter a integração com `MessageCard` e seu layout/rolagem existentes; não alterar a lógica da lista de conversa nesta mudança.

## Validação

### Unit tests

- Preservar os testes de parser para snapshots parciais e fence aberto.
- Acrescentar regressão de parser que processa snapshots sucessivos, incluindo correção/substituição e fence aberto, confirmando que cada string é tratada como snapshot independente. Isso não substitui a verificação do estado visual do composable.
- Não testar implementação interna de terceiros nem duplicar os testes da biblioteca.

### Build e comportamento visual

- Executar a suíte unitária Android e `assembleDebug` após a implementação.
- Inspecionar visualmente uma resposta Markdown com code fence realçado e uma resposta longa em streaming, confirmando atualização do último snapshot, dimensões do bloco e scroll estável.
- Não instalar APK como parte desta validação.

## Fora de escopo

- Criar parser ou renderer Markdown independente.
- Trocar Mike Penz, trocar o parser JetBrains Markdown ou adicionar Prism4j/outro highlighter.
- Alterar backend, formato dos eventos de streaming, persistência de mensagens, política de auto-scroll ou estilos gerais do chat.
- Adicionar otimização de debounce/cache antes de observar custo real em streaming.

## Riscos e pontos de verificação

- O conteúdo completo é reprocessado pelo renderer a cada snapshot. O custo deve ser observado em uma resposta longa; otimização só entra se a validação demonstrar problema.
- Os testes atuais exercitam o parser, não comprovam o layout Compose nem o syntax highlighting visual. A inspeção visual faz parte da validação e não deve ser substituída por alegação baseada apenas em build verde.
