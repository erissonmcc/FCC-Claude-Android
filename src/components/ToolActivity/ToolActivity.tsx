// Presentation and labels adapted from the original Compose ToolActivity.
import React, {useEffect, useState} from 'react';
import {Image, ScrollView, Text, View} from 'react-native';
import {Accordion as AnimatedAccordion} from '@animatereactnative/accordion';
import Animated, {FadeIn, FadeOut, LinearTransition} from 'react-native-reanimated';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {stylesheet} from './styles';
const accordionLayout = LinearTransition.duration(180);
const accordionEntering = FadeIn.duration(120);
const accordionExiting = FadeOut.duration(100);
export type ToolItem = {id: string; kind: string; title: string; text: string; runId: string; complete?: boolean};
export type Run = {status: string; createdAt: number; finishedAt: number | null; consumedTokens?: number | null};
const actions: Record<string, string> = {"Skill": "Carregar skill", "Bash": "Executar comando", "BashOutput": "Acompanhar comando", "Read": "Ler arquivo", "FileRead": "Ler arquivo", "Write": "Gravar arquivo", "FileWrite": "Gravar arquivo", "Edit": "Editar arquivo", "FileEdit": "Editar arquivo", "MultiEdit": "Editar arquivos", "Glob": "Buscar arquivos", "Grep": "Buscar no conteúdo", "LS": "Listar arquivos", "NotebookEdit": "Editar notebook", "WebSearch": "Pesquisar na web", "WebFetch": "Abrir página", "Agent": "Executar agente", "Task": "Executar agente", "TaskOutput": "Acompanhar tarefa", "TaskCreate": "Criar tarefa", "TaskUpdate": "Atualizar tarefa", "TaskGet": "Consultar tarefa", "TaskList": "Listar tarefas", "TaskStop": "Parar tarefa", "KillShell": "Parar comando", "TodoWrite": "Atualizar lista de tarefas", "EnterPlanMode": "Entrar no modo de planejamento", "ExitPlanMode": "Concluir planejamento", "EnterWorktree": "Abrir área de trabalho isolada", "ExitWorktree": "Sair da área de trabalho isolada", "ToolSearch": "Buscar ferramentas", "ListMcpResources": "Listar recursos MCP", "ListMcpResourcesTool": "Listar recursos MCP", "ReadMcpResource": "Ler recurso MCP", "ReadMcpResourceTool": "Ler recurso MCP", "ReadMcpResourceDir": "Listar pasta MCP", "RefreshMcpTools": "Atualizar ferramentas MCP", "SendMessage": "Enviar mensagem ao agente", "TeamCreate": "Criar equipe", "TeamDelete": "Encerrar equipe", "CronCreate": "Agendar tarefa", "CronDelete": "Remover agendamento", "CronList": "Listar agendamentos", "ScheduleWakeup": "Programar retomada", "RemoteTrigger": "Gerenciar acionamento remoto", "Workflow": "Executar fluxo de trabalho", "Monitor": "Monitorar execução", "Artifact": "Gerenciar artefato", "Projects": "Gerenciar arquivos do projeto", "ClaudeDesign": "Executar ação de design", "ReportFindings": "Registrar resultados da revisão", "ProposeSkills": "Propor skills", "ProposeGoal": "Definir objetivo", "PushNotification": "Enviar notificação", "ReadNotifications": "Ler notificações", "SendFeedback": "Enviar feedback", "OfferChromeSetup": "Configurar navegador", "ShowOnboardingRolePicker": "Escolher perfil", "AskUserQuestion": "Fazer uma pergunta"};
const phoneToolNames: Record<string, string> = {
  'mcp__phone__read_file': 'Read',
  'mcp__phone__write_file': 'Write',
  'mcp__phone__edit_file': 'Edit',
  'mcp__phone__list_directory': 'LS',
  'mcp__phone__find_files': 'Glob',
  'mcp__phone__search_files': 'Grep',
  'mcp__phone__run_command': 'Bash',
};
const labels: Record<string, string> = {"skill": "Skill", "args": "Argumentos", "arguments": "Argumentos", "command": "Comando", "file_path": "Arquivo", "notebook_path": "Notebook", "path": "Pasta", "paths": "Pastas", "pattern": "Padrão", "glob": "Arquivos", "query": "Busca", "url": "Endereço", "description": "Descrição", "prompt": "Instruções", "content": "Conteúdo", "old_string": "Antes", "new_string": "Depois", "new_source": "Novo conteúdo", "edits": "Alterações", "replace_all": "Substituir todas as ocorrências", "offset": "Início", "limit": "Limite", "pages": "Páginas", "cell_id": "Célula", "cell_type": "Tipo de célula", "edit_mode": "Operação", "output_mode": "Formato da busca", "head_limit": "Limite de resultados", "multiline": "Várias linhas", "timeout": "Tempo limite (ms)", "timeout_ms": "Tempo limite (ms)", "run_in_background": "Em segundo plano", "server": "Servidor", "uri": "Recurso", "tool": "Ferramenta", "name": "Nome", "task_id": "Tarefa", "taskId": "Tarefa", "shell_id": "Comando", "agent_id": "Agente", "subagent_type": "Tipo de agente", "team_name": "Equipe", "recipient": "Destinatário", "subject": "Tarefa", "status": "Estado", "todos": "Tarefas", "activeForm": "Em andamento", "owner": "Responsável", "model": "Modelo", "effort": "Esforço", "mode": "Modo", "action": "Ação", "operation": "Operação", "allowed_domains": "Domínios permitidos", "blocked_domains": "Domínios excluídos", "cron": "Agendamento", "recurring": "Recorrente", "durable": "Persistente", "delaySeconds": "Espera (segundos)", "reason": "Motivo", "plan": "Plano", "allowedPrompts": "Ações previstas", "metadata": "Informações adicionais", "addBlocks": "Bloqueia", "addBlockedBy": "Depende de", "findings": "Resultados"};
const primary = ['skill','args','command','file_path','notebook_path','path','pattern','glob','query','url','subject','name','task_id','taskId','status','server','uri','recipient','subagent_type','action','operation','cron','reason'];
const codeFields = new Set(['command','content','old_string','new_string','new_source','script','diff','skillMd']);
const icons = {
  terminal: require('../../assets/lucide/terminal.png'), file: require('../../assets/lucide/file.png'),
  search: require('../../assets/lucide/search.png'), plug: require('../../assets/lucide/plug.png'),
  zap: require('../../assets/lucide/zap.png'), chevron_down: require('../../assets/lucide/chevron_down.png'),
  chevron_right: require('../../assets/lucide/chevron_right.png'),
};
function Icon({name}: {name: keyof typeof icons}) {const {styles: s} = useStyles(stylesheet); return <Image source={icons[name]} style={s.icon} />;}
function displayToolName(name: string) {return phoneToolNames[name] || name;}
function category(tool: string) {
  tool = displayToolName(tool);
  if (['Bash','BashOutput','KillShell'].includes(tool)) return 'comandos';
  if (['Read','FileRead','Write','FileWrite','Edit','FileEdit','MultiEdit','NotebookEdit'].includes(tool)) return 'arquivos';
  if (['Glob','Grep','LS'].includes(tool)) return 'buscas';
  if (['WebSearch','WebFetch'].includes(tool)) return 'web';
  return 'ferramentas';
}
function groupIcon(items: ToolItem[]): keyof typeof icons {
  if (items.every(item => item.title.startsWith('mcp__') && displayToolName(item.title) === item.title)) return 'plug';
  const kinds = [...new Set(items.map(item => category(item.title)))];
  if (kinds.length !== 1) return 'zap';
  return kinds[0] === 'comandos' ? 'terminal' : kinds[0] === 'arquivos' ? 'file' : ['buscas','web'].includes(kinds[0]) ? 'search' : 'zap';
}
function parse(text: string): Record<string, unknown> {
  try {const result = JSON.parse(text); return result && typeof result === 'object' && !Array.isArray(result) ? result : {content: text};}
  catch {return {content: text};}
}
function titleCase(value: string) {return value.charAt(0).toUpperCase() + value.slice(1);}
function Field({name, value}: {name: string; value: unknown}) {
  const {styles: s} = useStyles(stylesheet);
  const [full, setFull] = useState(false);
  const label = labels[name] || titleCase(name.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' '));
  if (Array.isArray(value)) return <View style={s.field}><Text style={s.label}>{label} ({value.length})</Text><View style={s.nested}>{value.map((child, index) => name === 'todos' && child && typeof child === 'object'
    ? <Text key={index} style={s.value}>{index + 1}. {String(child.content ?? '')} · {status(String(child.status))}</Text>
    : <Field key={index} name={String(index + 1)} value={child} />)}</View></View>;
  if (value && typeof value === 'object') return <View style={s.field}><Text style={s.label}>{label}</Text><View style={s.nested}>{Object.entries(value).filter(([,v]) => v != null).map(([key, child]) => <Field key={key} name={key} value={child} />)}</View></View>;
  const raw = value == null ? '—' : value === true ? 'Sim' : value === false ? 'Não' : String(value);
  const text = name === 'status' ? status(raw) : raw;
  if (codeFields.has(name) || text.includes('\n') || text.length > 240) return <Animated.View layout={LinearTransition.duration(200)} style={s.field}><Text style={s.label}>{label}</Text><ScrollView horizontal style={s.codeBox}><Text selectable numberOfLines={full ? undefined : 12} style={[s.code, name === 'old_string' && {color: '#E0A4A4'}, name === 'new_string' && {color: '#A8C9AB'}]}>{text}</Text></ScrollView>{text.split('\n').length > 12 && <Pressable onPress={() => setFull(!full)} style={s.expand}><Text style={s.label}>{full ? 'Recolher conteúdo' : 'Mostrar conteúdo completo'}</Text></Pressable>}</Animated.View>;
  return <Text selectable style={s.value}>{label}: {text}</Text>;
}
function status(value: string) {return ({completed:'Concluída',in_progress:'Em andamento',pending:'Pendente',deleted:'Removida'} as Record<string,string>)[value] || value;}

export function ToolActivity({item}: {item: ToolItem}) {
  const {styles: s} = useStyles(stylesheet);
  const [expanded, setExpanded] = useState(false);
  const input = parse(item.text);
  const keys = Object.keys(input).filter(key => input[key] != null);
  const summaryKey = primary.find(key => keys.includes(key) && typeof input[key] !== 'object') || keys.find(key => key !== 'description' && typeof input[key] !== 'object');
  const summary = input.description || (summaryKey ? input[summaryKey] : '');
  const toolName = displayToolName(item.title);
  const title = actions[toolName] || titleCase(toolName.split('__').pop()!.replace(/_/g, ' '));
  return <AnimatedAccordion.Accordion isOpen={expanded} onChange={setExpanded} layout={accordionLayout}>
    <AnimatedAccordion.Header>
      <View accessible accessibilityRole="button" accessibilityState={{expanded}} style={s.toolHeader}>
        <Icon name={groupIcon([item])}/>
        <Text numberOfLines={1} style={s.summary}>{title}{!expanded && summary ? ': ' + String(summary).replace(/\s+/g,' ') : ''}</Text>
        <Icon name={expanded ? 'chevron_down' : 'chevron_right'} />
      </View>
    </AnimatedAccordion.Header>
    <AnimatedAccordion.Expanded layout={accordionLayout} entering={accordionEntering} exiting={accordionExiting}>
      <View style={s.details}>
        <Text style={s.label}>{toolName}</Text>
        {input.description != null && <Text style={s.summary}>{String(input.description)}</Text>}
        {keys.filter(key => key !== 'description').map(key => <Field key={key} name={key} value={input[key]}/>)}
      </View>
    </AnimatedAccordion.Expanded>
  </AnimatedAccordion.Accordion>;
}
function ToolGroup({items, active}: {items: ToolItem[]; active: boolean}) {
  const {styles: s} = useStyles(stylesheet);
  const [expanded, setExpanded] = useState(false);
  const latest = items[items.length - 1];
  const categories = [...new Set(items.map(item => category(item.title)))];
  const summaries: Record<string,string> = {comandos:'executou comandos',arquivos:'acessou arquivos',buscas:'fez buscas',web:'acessou a web',ferramentas:'usou ferramentas'};
  const input = parse(latest.text);
  const path = String(input.file_path || input.path || '').split('/').pop();
  const latestTool = displayToolName(latest.title);
  const verb = ['Read','FileRead'].includes(latestTool) ? 'Lendo' : ['Write','FileWrite'].includes(latestTool) ? 'Gravando' : ['Edit','FileEdit','MultiEdit','NotebookEdit'].includes(latestTool) ? 'Editando' : '';
  const activeLabel = verb ? `${verb} ${path || 'arquivo'}` : latestTool === 'Bash' ? String(input.description || 'Executando comando') : latestTool === 'WebSearch' ? 'Pesquisando na web' : latestTool === 'WebFetch' ? 'Abrindo página' : actions[latestTool] || 'Usando ferramenta';
  return <AnimatedAccordion.Accordion isOpen={expanded} onChange={setExpanded} layout={accordionLayout}>
    <AnimatedAccordion.Header>
      <View accessible accessibilityRole="button" accessibilityState={{expanded}} style={s.groupHeader}>
        <Icon name={groupIcon(active ? [latest] : items)}/>
        <Text numberOfLines={1} style={s.summary}>{active ? activeLabel : titleCase(categories.map(kind => summaries[kind]).join(' e '))}</Text>
        <Icon name={expanded ? 'chevron_down' : 'chevron_right'} />
      </View>
    </AnimatedAccordion.Header>
    <AnimatedAccordion.Expanded layout={accordionLayout} entering={accordionEntering} exiting={accordionExiting}>
      {items.map(item => <AnimatedAccordion.Sibling key={item.id} layout={accordionLayout}><ToolActivity item={item}/></AnimatedAccordion.Sibling>)}
    </AnimatedAccordion.Expanded>
  </AnimatedAccordion.Accordion>;
}
export function WorkActivity({items, run, renderMessage}: {items: ToolItem[]; run?: Run; renderMessage: (item: ToolItem) => React.ReactNode}) {
  const {styles: s} = useStyles(stylesheet);
  const active = !!run && ['preparing','running','stopping'].includes(run.status);
  const [expanded, setExpanded] = useState(active);
  useEffect(() => {setExpanded(active);}, [active]);
  const seconds = run?.finishedAt && run.createdAt > 0 ? Math.max(0, Math.floor((run.finishedAt - run.createdAt)/1000)) : null;
  const duration = seconds == null ? '' : seconds >= 3600 ? `${Math.floor(seconds/3600)} h ${Math.floor(seconds%3600/60)} min ${seconds%60} s` : seconds >= 60 ? `${Math.floor(seconds/60)} min ${seconds%60} s` : `${seconds} s`;
  const parts: ToolItem[][] = [];
  for (const item of items) {const last=parts[parts.length-1]; if (item.kind === 'tool_call' && last?.[0].kind === 'tool_call') last.push(item); else parts.push([item]);}
  return <AnimatedAccordion.Accordion key={!run ? 'static' : active ? 'active' : 'finished'} isOpen={!run || active} onChange={setExpanded} layout={accordionLayout} style={s.work}>
    {run && !active && <AnimatedAccordion.Header>
      <View accessible accessibilityRole="button" accessibilityState={{expanded}} style={s.workHeader}>
        <Text style={s.summary}>{duration ? `Trabalhou por ${duration}` : 'Trabalhou'}</Text>
        <Icon name={expanded ? 'chevron_down' : 'chevron_right'}/>
      </View>
    </AnimatedAccordion.Header>}
    <AnimatedAccordion.Expanded layout={accordionLayout} entering={accordionEntering} exiting={accordionExiting}>
      {parts.map((part,index) => {
        if (part[0].kind === 'tool_call') return <AnimatedAccordion.Sibling key={part[0].id} layout={accordionLayout}><ToolGroup items={part} active={active && index === parts.length-1}/></AnimatedAccordion.Sibling>;
        if (part[0].kind === 'reasoning') return <AnimatedAccordion.Sibling key={part[0].id} layout={accordionLayout}><Reasoning item={part[0]}/></AnimatedAccordion.Sibling>;
        return <AnimatedAccordion.Sibling key={part[0].id} layout={accordionLayout}>{part.map(renderMessage)}</AnimatedAccordion.Sibling>;
      })}
    </AnimatedAccordion.Expanded>
  </AnimatedAccordion.Accordion>;
}
function Reasoning({item}: {item: ToolItem}) {
  const {styles: s} = useStyles(stylesheet);
  const [expanded, setExpanded] = useState(false);
  return <AnimatedAccordion.Accordion isOpen={expanded} onChange={setExpanded} layout={accordionLayout}>
    <AnimatedAccordion.Header>
      <View accessible accessibilityRole="button" accessibilityState={{expanded}} style={s.groupHeader}>
        <Text style={s.summary}>Raciocínio</Text>
        <Icon name={expanded ? 'chevron_down' : 'chevron_right'}/>
      </View>
    </AnimatedAccordion.Header>
    <AnimatedAccordion.Expanded layout={accordionLayout} entering={accordionEntering} exiting={accordionExiting}>
      <Text selectable style={s.value}>{item.text}</Text>
    </AnimatedAccordion.Expanded>
  </AnimatedAccordion.Accordion>;
}
