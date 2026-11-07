# Anexos no chat — Plano de implementação

> **Para agentes de implementação:** SUB-SKILL OBRIGATÓRIA: usar `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans`; executar as tarefas em sequência e marcar cada etapa concluída.

**Objetivo:** Permitir selecionar, visualizar, remover e enviar arquivos/imagens ao Claude em uma conversa, mantendo cópias privadas temporárias por sessão no FCC.

**Arquitetura:** Android mantém URIs selecionadas no estado do composer e envia cada arquivo por multipart ao FCC. O servidor gera IDs e nomes de armazenamento, limita e valida os arquivos, resolve os IDs enviados com o turno somente dentro da sessão correspondente e apaga os arquivos quando a sessão é efetivamente removida. A interface preserva o visual atual do FCC Claude e reduz o destaque da borda do campo.

**Tecnologias:** Kotlin, Jetpack Compose, Android `OpenMultipleDocuments`/`ContentResolver`, `HttpURLConnection`, FastAPI/Starlette `UploadFile` e testes pytest existentes.

**Especificação:** `docs/superpowers/specs/2026-11-04-chat-attachments-design.md`

## Restrições globais

- Armazenar cópias temporárias privadas no Termux, vinculadas ao ID de sessão.
- Nunca usar o nome enviado pelo cliente como caminho ou nome de armazenamento.
- Limite por arquivo: 10 MiB; máximo de 5 arquivos por turno; limite total de 50 MiB por sessão.
- Rejeitar arquivos vazios e remover arquivos parciais após falha.
- Falha de upload ou envio não pode descartar o rascunho nem anexos selecionados; nunca enviar turno com referência inválida.
- Excluir anexos apenas após a exclusão confirmada da sessão, sem afetar outros diretórios/sessões.
- Manter paleta, arredondamento, tipografia, ícones Lucide e controles existentes do composer; não copiar literalmente outra marca.

## Foco de revisão

- URI de provedor ilegível/desaparecida: mostrar erro e manter seleção e rascunho (teste Android manual/automatizado conforme infraestrutura existente).
- Multipart com conteúdo vazio, maior que 10 MiB, 6 arquivos no turno ou total acima de 50 MiB: rejeitar com erro e sem arquivos parciais (testes de rota).
- Nome com separadores de caminho/Unicode incomum: resposta pode exibir nome seguro, mas armazenamento segue nome aleatório fora do controle do cliente (teste de rota).
- ID de anexo inexistente ou pertencente a outra sessão: não aceitar o turno (teste de rota/serviço).
- Exclusão concorrente com upload/envio: nenhum caminho fora do diretório da sessão é lido ou removido (teste de serviço).
- Abrir/trocar de conversa enquanto o snapshot está pendente: exibir skeleton, não misturar nem revelar histórico parcialmente, instalar o snapshot inteiro uma vez; não alterar o streaming incremental (teste manual/automatizado Android).

---

### Task 1: Serviço de anexos e ciclo de vida no FCC

**Arquivos:**
- Criar: `src/free_claude_code/application/code_sessions/attachments.py` (armazenamento temporário e resolução segura por sessão).
- Modificar: `src/free_claude_code/api/code_sessions_routes.py` (endpoint multipart e IDs no payload do turno).
- Modificar: `src/free_claude_code/application/code_sessions/service.py` (validar anexos e anexar referências ao texto submetido; limpar na exclusão concluída).
- Modificar: `src/free_claude_code/application/code_sessions/ports.py` e `src/free_claude_code/api/ports.py` somente se a injeção do serviço exigir a extensão das interfaces.
- Testar: `tests/api/test_code_sessions_routes.py` e `tests/application/test_code_sessions.py`.

**Interfaces:**
- `POST /admin/api/code/sessions/{session_id}/attachments` recebe multipart `file` e retorna `{attachment_id, name, size, content_type}`; não retorna caminho local.
- `SendPayload` aceita `attachment_ids: list[str]` (ausente equivale a lista vazia); IDs são validados no FCC para a sessão da rota.
- O serviço constrói referências locais a partir dos arquivos validados no diretório privado daquela sessão; caminhos arbitrários enviados pelo cliente nunca são aceitos.

- [ ] Escrever testes para upload válido, rejeições/limites, nome hostil, ID de outra sessão, texto do turno com referências seguras e limpeza na exclusão concluída.
- [ ] Executar os testes novos e confirmar que falham antes da implementação.
- [ ] Implementar o armazenamento limitado a 10 MiB por arquivo, 5 por turno e 50 MiB por sessão, com geração de nomes/IDs e limpeza de parciais.
- [ ] Ligar o endpoint ao serviço e passar `attachment_ids` à admissão do turno, validando sessão, existência e caminhos confinados.
- [ ] Acoplar a remoção do diretório ao sucesso do ciclo de exclusão da sessão, sem apagar arquivos em exclusão não concluída.
- [ ] Executar testes direcionados e `python -m compileall` nos módulos FCC alterados.

### Task 2: Seletor Android, chips e envio recuperável

**Arquivos:**
- Modificar: `app/src/main/java/com/fcc/claude/MainActivity.kt`.
- Testar: testes Android existentes; se não houver infraestrutura de UI adequada, validar compilação e fluxo real no aparelho.

**Interfaces:**
- Produzir itens pendentes contendo URI, nome de exibição, MIME e metadados necessários para upload; estado é local ao composer e à sessão ativa.
- `FccApi.uploadAttachment(sessionId, uri, ...)` envia o conteúdo por multipart em `Dispatchers.IO` e retorna ID/nome/tamanho.
- O envio do turno inclui texto e IDs retornados pelo FCC; não inclui caminhos de arquivo do dispositivo nem paths do servidor.

- [ ] Adicionar ação “+” de pelo menos 48dp com descrição acessível e picker multi-documento sem permissão ampla de armazenamento.
- [ ] Exibir anexos selecionados como chips/linhas removíveis; mostrar miniatura para imagem quando disponível e manter o composer multilinha utilizável.
- [ ] Integrar a ação ao composer atual sem remover seletor de modelo/esforço nem botão de envio; manter estilo FCC e substituir a borda de destaque atual por contorno neutro discreto.
- [ ] Durante abertura/troca de conversa, manter a lista anterior oculta e mostrar skeleton até `loadDetail` receber o snapshot completo; substituir `items` uma única vez e descartar resposta atrasada de sessão que deixou de ser ativa.
- [ ] Manter intacta a atualização incremental usada para mensagens novas durante streaming do Claude.
- [ ] Implementar upload multipart de stream URI sem carregar arquivos completos em memória.
- [ ] No envio, aguardar uploads; se todos passarem, submeter texto e IDs e limpar anexos/rascunho somente após aceitação do turno.
- [ ] Em falha de leitura, upload ou submissão, não despachar prompt incompleto e preservar texto/anexos para retry; apresentar erro visível.
- [ ] Compilar com `./gradlew :app:assembleDebug` e verificar no aparelho a seleção, remoção, estado de erro e composer com teclado aberto.

### Task 3: Validação ponta a ponta

**Arquivos:**
- Ajustar apenas os arquivos das tarefas anteriores se os testes revelarem necessidade.

**Interfaces:**
- Confirmar que a ferramenta `Read` do agente consegue abrir a imagem e o arquivo textual a partir das referências emitidas pelo FCC.

- [ ] Executar uma conversa de teste com uma imagem e um arquivo de texto; verificar que Claude consegue inspecionar ambos e que o histórico do usuário continua legível.
- [ ] Apagar somente a conversa descartável de teste e confirmar que seus anexos desapareceram enquanto outros diretórios de sessão permanecem intactos.
- [ ] Instalar o APK validado por `/data/local/tmp/fcc-claude.apk` e `pm install -r --user 0`; confirmar abertura e ausência de erros relevantes no logcat.
