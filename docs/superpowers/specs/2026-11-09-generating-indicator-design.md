# Indicador de geração no React Native

## Objetivo

Adaptar o indicador de trabalho do Compose antigo para o rodapé de conversa React Native, substituindo o spinner e o texto pequeno “Gerando resposta…”.

## Abordagem

Usar componentes React Native e `Animated`, sem novo pacote nem componente Android nativo. A linha terá três pontos animados, o texto “Claude está trabalhando…” com brilho suave, e métricas da execução ativa: tempo decorrido e tokens consumidos. A animação dos pontos e do texto seguirá o período de 1600 ms do Compose; o tempo decorrido atualiza uma vez por segundo.

## Escopo e limites

Manter a condição atual de exibição (`state.busy`) e ler as métricas de `state.runs[state.activeRunId]`. Não mudar o ciclo de vida das execuções nem o restante do rodapé. A implementação React Native reproduz a composição e o movimento do original sem exigir gradiente de texto nativo.

## Verificação

Conferir TypeScript e diff. A aparência e a atualização das métricas ainda dependem de validação visual no aparelho.
