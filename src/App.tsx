import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  BackHandler,
  DrawerLayoutAndroid,
  FlatList,
  Image,
  KeyboardAvoidingView,
  Keyboard,
  Modal,
  PanResponder,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView, initialWindowMetrics} from 'react-native-safe-area-context';
import {PortalProvider} from '@gorhom/portal';
import {CopilotMarkdown, defaultMarkdownStyles} from '@copilotkit/react-native/components';
import {EffortMenu} from './EffortMenu';
import {Pressable} from './RipplePressable';
import {WorkActivity, type Run} from './ToolActivity';
import type {MarkdownStyle} from 'react-native-enriched-markdown';
import {NativeEventEmitter, NativeModules} from 'react-native';

const C = {
  backdrop: '#101113',
  surface: '#1B1C1F',
  raised: '#25272B',
  text: '#E9E9E7',
  muted: '#999B9F',
  accent: '#D5BFA3',
  danger: '#E9A28F',
  line: '#FFFFFF18',
};

const markdownStyle = {
  paragraph: {...defaultMarkdownStyles.paragraph, fontSize: 15, lineHeight: 23, color: C.text, marginTop: 3, marginBottom: 9},
  h1: {...defaultMarkdownStyles.h1, color: C.text, fontSize: 23, marginTop: 12, marginBottom: 8},
  h2: {...defaultMarkdownStyles.h2, color: C.text, fontSize: 19, marginTop: 10, marginBottom: 6},
  h3: {...defaultMarkdownStyles.h3, color: C.text, fontSize: 17, marginTop: 8, marginBottom: 5},
  strong: {...defaultMarkdownStyles.strong, color: C.text},
  em: {...defaultMarkdownStyles.em, color: C.text},
  link: {...defaultMarkdownStyles.link, color: C.accent},
  blockquote: {...defaultMarkdownStyles.blockquote, color: C.text, backgroundColor: C.surface, borderColor: C.accent},
  code: {...defaultMarkdownStyles.code, color: C.text, backgroundColor: C.surface, borderColor: C.line},
  codeBlock: {...defaultMarkdownStyles.codeBlock, color: C.text, backgroundColor: C.surface, borderColor: '#414348', borderRadius: 10},
  list: {...defaultMarkdownStyles.list, color: C.text, bulletColor: C.muted, markerColor: C.text, fontSize: 15, lineHeight: 23, marginTop: 3, marginBottom: 9},
  h4: {color: C.text, fontSize: 16, fontWeight: 'bold'},
  h5: {color: C.text, fontSize: 15, fontWeight: 'bold'},
  h6: {color: C.muted, fontSize: 15, fontWeight: 'bold'},
  strikethrough: {color: C.muted},
  underline: {color: C.text},
  thematicBreak: {color: '#414348'},
  table: {color: C.text, headerTextColor: C.text, headerBackgroundColor: C.raised, rowEvenBackgroundColor: C.backdrop, rowOddBackgroundColor: C.surface, borderColor: '#414348'},
  taskList: {checkedColor: C.accent, borderColor: C.muted, checkmarkColor: C.backdrop, checkedTextColor: C.muted},
  math: {color: C.text, backgroundColor: C.surface},
  inlineMath: {color: C.text},
} satisfies MarkdownStyle;

const icons = {
  arrow_up: require('./assets/lucide/arrow_up.png'),
  arrow_down: require('./assets/lucide/arrow_down.png'),
  plus: require('./assets/lucide/plus.png'),
  square_pen: require('./assets/lucide/square_pen.png'),
  menu: require('./assets/lucide/menu.png'),
  square: require('./assets/lucide/square.png'),
  chevron_down: require('./assets/lucide/chevron_down.png'),
  wifi: require('./assets/lucide/wifi.png'),
  wifi_off: require('./assets/lucide/wifi_off.png'),
};
function IconButton({icon, label, onPress, disabled = false, filled = false}: {icon: keyof typeof icons; label: string; onPress: () => void; disabled?: boolean; filled?: boolean}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} android_ripple={{color: filled ? '#00000024' : '#FFFFFF20', foreground: true}} style={[styles.action, filled && styles.sendButton, filled && disabled && styles.sendDisabled]}><Image source={icons[icon]} style={{width: 22, height: 22, tintColor: filled ? (disabled ? C.muted : C.backdrop) : C.muted}} /></Pressable>;
}

type Session = {
  id: string; cwd: string; title: string; revision: number; status: string;
  harness: string; model: string; reasoningEffort: string; mode: string;
};
type Item = {
  id: string; kind: string; text: string; title: string; complete: boolean;
  runId: string; ordinal: number; sequence: number;
};
type Prompt = {id: string; tool: string; input: Record<string, any>};
type Attachment = {key: string; sessionId: string; name: string; contentType: string; uploaded: boolean};
type ChatState = {
  items: Item[]; runs: Record<string, Run>; sessions: Session[]; session: Session | null; prompts: Prompt[];
  pendingMessages: {id: string; sessionId: string; text: string}[];
  runMessages: {id: string; sessionId: string; runId: string; text: string; status: string}[];
  attachments: Attachment[]; busy: boolean; sending: boolean; activeRunId: string;
  connected: boolean; feedConnected: boolean; draft: string; error: string | null;
  historyError: string | null; historyCursor: string | null; historyLoading: boolean;
  detailLoaded: boolean; bootstrap: Record<string, any>;
};

const EMPTY: ChatState = {
  items: [], runs: {}, sessions: [], session: null, prompts: [], pendingMessages: [], runMessages: [],
  attachments: [], busy: false, sending: false, activeRunId: '', connected: false,
  feedConnected: false, draft: '', error: null, historyError: null, historyCursor: null,
  historyLoading: false, detailLoaded: false, bootstrap: {},
};

const FccChat = NativeModules.FccChat as {
  initialize(): Promise<string>;
  refresh(): Promise<unknown>;
  setDraft(text: string): void;
  sendMessage(text: string): Promise<boolean>;
  stop(): Promise<unknown>;
  newSession(cwd: string, model: string, mode: string, effort: string): Promise<unknown>;
  selectSession(id: string): Promise<unknown>;
  showSessionMenu(id: string, viewTag: number): Promise<unknown>;
  applySettings(cwd: string, model: string, mode: string, effort: string): Promise<unknown>;
  chooseProjectFolder(path: string): Promise<string | null>;
  respondPrompt(id: string, approved: boolean, answers?: string | null): Promise<unknown>;
  loadOlder(): Promise<unknown>;
  removeAttachment(key: string): Promise<unknown>;
  pickAttachments(): Promise<unknown>;
};

function Action({label, onPress, tint = C.text, disabled = false}: {
  label: string; onPress: () => void; tint?: string; disabled?: boolean;
}) {
  return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={[styles.action, disabled && styles.disabled]}>
    <Text style={[styles.actionText, {color: tint}]}>{label}</Text>
  </Pressable>;
}


function PermissionPrompt({prompt, onRespond}: {prompt: Prompt; onRespond: (approved: boolean, answers?: Record<string, any>) => void}) {
  const questions = Array.isArray(prompt.input?.questions) ? prompt.input.questions : [];
  const [selected, setSelected] = useState<Record<number, number[]>>({});
  const [other, setOther] = useState<Record<number, string>>({});
  if (questions.length > 0) {
    const ready = questions.every((_: unknown, index: number) => (selected[index]?.length ?? 0) > 0 &&
      (!(selected[index] ?? []).includes((questions[index].options ?? []).length) || Boolean(other[index]?.trim())));
    const answers = Object.fromEntries(questions.map((question: any, index: number) => {
      const options = question.options ?? [];
      const choices = selected[index] ?? [];
      return [question.question, choices.map(choice => choice === options.length ? other[index]?.trim() : options[choice]?.label).filter(Boolean).join(', ')];
    }));
    return <View style={styles.promptCard}>
      <Text style={styles.promptTitle}>Claude quer saber</Text>
      {questions.map((question: any, questionIndex: number) => <View key={`${prompt.id}-${questionIndex}`} style={styles.questionBlock}>
        {!!question.header && <Text style={styles.muted}>{question.header}</Text>}
        <Text style={styles.questionText}>{question.question}</Text>
        {(question.options ?? []).map((option: any, optionIndex: number) => {
          const active = (selected[questionIndex] ?? []).includes(optionIndex);
          return <Pressable key={`${questionIndex}-${optionIndex}`} onPress={() => setSelected(prev => {
            const old = prev[questionIndex] ?? [];
            return {...prev, [questionIndex]: question.multiSelect ? (active ? old.filter(i => i !== optionIndex) : [...old, optionIndex]) : [optionIndex]};
          })} style={[styles.option, active && styles.optionSelected]}>
            <Text style={styles.optionLabel}>{option.label}</Text>
            {!!option.description && <Text style={styles.muted}>{option.description}</Text>}
          </Pressable>;
        })}
        <Pressable onPress={() => setSelected(prev => ({...prev, [questionIndex]: [((question.options ?? []).length)]}))} style={[styles.option, (selected[questionIndex] ?? []).includes((question.options ?? []).length) && styles.optionSelected]}>
          <Text style={styles.optionLabel}>Outra resposta</Text>
        </Pressable>
        {(selected[questionIndex] ?? []).includes((question.options ?? []).length) && <TextInput value={other[questionIndex] ?? ''} onChangeText={value => setOther(prev => ({...prev, [questionIndex]: value}))} placeholder="Digite sua resposta" placeholderTextColor={C.muted} style={styles.textField} />}
      </View>)}
      <Action label="Enviar respostas" tint={C.accent} disabled={!ready} onPress={() => onRespond(true, answers)} />
      <Action label="Cancelar" tint={C.muted} onPress={() => onRespond(false)} />
    </View>;
  }
  return <View style={styles.promptCard}>
    <Text style={styles.promptTitle}>Permissão necessária</Text>
    <Text style={styles.questionText}>{prompt.tool}</Text>
    <Text selectable style={styles.toolBody}>{JSON.stringify(prompt.input, null, 2)}</Text>
    <View style={styles.promptActions}>
      <Action label="Permitir" tint={C.accent} onPress={() => onRespond(true)} />
      <Action label="Negar" tint={C.danger} onPress={() => onRespond(false)} />
    </View>
  </View>;
}

export default function App() {
  return <SafeAreaProvider initialMetrics={initialWindowMetrics}><PortalProvider><ChatScreen /></PortalProvider></SafeAreaProvider>;
}

function ChatScreen() {
  const [state, setState] = useState<ChatState>(EMPTY);
  const [ready, setReady] = useState(false);
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', () => setKeyboardVisible(true));
    const hide = Keyboard.addListener('keyboardDidHide', () => setKeyboardVisible(false));
    return () => { show.remove(); hide.remove(); };
  }, []);
  const [drawer, setDrawer] = useState(false);
  const [settings, setSettings] = useState(false);
  const [model, setModel] = useState('');
  const [mode, setMode] = useState('config');
  const [effort, setEffort] = useState('');
  const [cwd, setCwd] = useState('/storage/emulated/0');
  const [showLatest, setShowLatest] = useState(false);
  const [composerHeight, setComposerHeight] = useState(112);
  const listRef = React.useRef<FlatList<Item>>(null);
  const drawerRef = React.useRef<DrawerLayoutAndroid>(null);
  const drawerSwipe = React.useRef(PanResponder.create({
    onMoveShouldSetPanResponderCapture: (_, gesture) => gesture.x0 < 80 && gesture.dx > 28 && gesture.dx > Math.abs(gesture.dy) * 1.8,
    onPanResponderGrant: () => drawerRef.current?.openDrawer(),
  })).current;
  const {width} = useWindowDimensions();
  const isNearBottom = React.useRef(true);
  const pendingDrafts = React.useRef<string[]>([]);
  const sessionId = React.useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!drawer) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      drawerRef.current?.closeDrawer();
      return true;
    });
    return () => subscription.remove();
  }, [drawer]);

  useEffect(() => {
    isNearBottom.current = true;
    setShowLatest(false);
    listRef.current?.scrollToEnd({animated: false});
  }, [state.session?.id]);

  const applySnapshot = useCallback((value: string) => {
    try {
      const next = JSON.parse(value) as ChatState;
      const changedSession = sessionId.current !== next.session?.id;
      sessionId.current = next.session?.id;
      if (changedSession) pendingDrafts.current = [];
      const acknowledged = pendingDrafts.current.lastIndexOf(next.draft);
      if (acknowledged >= 0) pendingDrafts.current.splice(0, acknowledged + 1);
      setState(previous => ({...next, draft: pendingDrafts.current.length ? previous.draft : next.draft}));
      if (next.session?.cwd) setCwd(next.session.cwd);
      if (next.session) {
        setModel(next.session.model ?? '');
        setMode(next.session.mode || 'config');
        setEffort(next.session.reasoningEffort ?? '');
      }
    } catch (error) {
      console.warn('Resposta inválida do serviço de chat FCC', error);
    }
  }, []);

  useEffect(() => {
    const emitter = new NativeEventEmitter(NativeModules.FccChat);
    const subscription = emitter.addListener('fccStateChanged', applySnapshot);
    FccChat.initialize().then(applySnapshot).catch(error => setState(prev => ({...prev, error: String(error)}))).finally(() => setReady(true));
    return () => subscription.remove();
  }, [applySnapshot]);

  const send = useCallback(async () => {
    const value = state.draft.trim();
    if (!value) return;
    try {
      const openSettings = await FccChat.sendMessage(value);
      if (openSettings === true) setSettings(true);
    } catch (error) {
      console.warn('Não foi possível enviar a mensagem', error);
    }
  }, [state.draft]);

  const currentAttachments = state.attachments.filter(item => item.sessionId === state.session?.id);

  const footer = <View style={styles.listFooter}>
    {state.busy && <View style={styles.generating}><ActivityIndicator size="small" color={C.accent} /><Text style={styles.generatingText}>Gerando resposta…</Text></View>}
    {state.prompts.map(prompt => <PermissionPrompt key={prompt.id} prompt={prompt} onRespond={(approved, answers) => FccChat.respondPrompt(prompt.id, approved, answers ? JSON.stringify(answers) : null)} />)}
    {state.pendingMessages.filter(message => message.sessionId === state.session?.id).map(message => <View key={message.id} style={styles.queued}><Text style={styles.muted}>Na fila · {message.text}</Text></View>)}
    {state.runMessages.filter(message => message.sessionId === state.session?.id && message.status !== 'forwarded').map(message => <View key={message.id} style={styles.queued}><Text style={styles.muted}>{message.status === 'uncertain' ? 'Envio pendente de confirmação' : 'Enviando…'} · {message.text}</Text></View>)}
    {!!state.historyError && <Text style={styles.errorText}>{state.historyError}</Text>}
  </View>;

  const rows = useMemo(() => {
    const result: (Item & {work?: Item[]})[] = [];
    for (const item of state.items) {
      if (item.kind === 'tool_call' || item.kind === 'reasoning') {
        const previous = result[result.length - 1];
        if (previous?.work && previous.runId === item.runId) previous.work.push(item);
        else result.push({...item, work: [item]});
      } else result.push(item);
    }
    return result;
  }, [state.items]);
  const renderItem = useCallback(({item}: {item: Item & {work?: Item[]}}) => {
    if (item.work) return <View style={styles.assistantRow}><WorkActivity items={item.work} run={state.runs[item.runId]} /></View>;
    if (item.kind === 'user') {
      return <View style={styles.userRow}><View style={styles.userBubble}><Text selectable style={styles.userText}>{item.text}</Text></View></View>;
    }
    if (!item.text) return null;
    return <View style={styles.assistantRow}>
      <CopilotMarkdown content={item.text} style={markdownStyle} streamingAnimation={!item.complete} />
      {!item.complete && <View style={styles.streamMarker}><View style={styles.streamDot} /></View>}
    </View>;
  }, [state.runs]);

  const onListScroll = useCallback((event: any) => {
    const {contentOffset, contentSize, layoutMeasurement} = event.nativeEvent;
    isNearBottom.current = contentOffset.y + layoutMeasurement.height >= contentSize.height - 120;
    setShowLatest(!isNearBottom.current);
  }, []);
  const onListContentSizeChange = useCallback(() => {
    if (isNearBottom.current) listRef.current?.scrollToEnd({animated: false});
  }, []);

  return <DrawerLayoutAndroid
    ref={drawerRef}
    style={styles.screen}
    drawerWidth={Math.min(width * 0.84, 350)}
    drawerPosition="left"
    drawerLockMode="unlocked"
    drawerBackgroundColor={C.surface}
    keyboardDismissMode="none"
    onDrawerOpen={() => { Keyboard.dismiss(); setDrawer(true); }}
    onDrawerClose={() => setDrawer(false)}
    renderNavigationView={() => <SessionDrawer
      state={state}
      onSelect={id => { drawerRef.current?.closeDrawer(); FccChat.selectSession(id); }}
      onMenu={(id, tag) => { FccChat.showSessionMenu(id, tag).catch(console.warn); }}
      onNew={() => { drawerRef.current?.closeDrawer(); FccChat.newSession(cwd, '', 'config', ''); }}
      onSettings={() => { drawerRef.current?.closeDrawer(); setSettings(true); }}
    />}
  >
  <KeyboardAvoidingView behavior="padding" enabled={keyboardVisible} style={styles.screen}>
  <SafeAreaView edges={keyboardVisible ? ['top', 'left', 'right'] : ['top', 'bottom', 'left', 'right']} style={styles.screen}>
    <StatusBar backgroundColor={C.backdrop} barStyle="light-content" />
    <View style={styles.topBar}>
      <IconButton icon="menu" label="Abrir conversas" onPress={() => drawerRef.current?.openDrawer()} />
      <View style={styles.headerCenter}>
        <Text numberOfLines={1} style={styles.headerTitle}>{state.session?.title || 'FCC Claude'}</Text>
        <Text numberOfLines={1} style={styles.headerPath}>{state.session?.cwd || cwd}</Text>
      </View>
      <IconButton icon={state.connected ? "wifi" : "wifi_off"} label="Atualizar conexão" onPress={() => FccChat.refresh()} />
      {state.busy && <IconButton icon="square" label="Parar resposta" onPress={() => FccChat.stop()} />}
      <IconButton icon="square_pen" label="Nova conversa" onPress={() => FccChat.newSession(cwd, '', 'config', '')} />
    </View>
    {!!state.error && <Pressable onPress={() => FccChat.refresh()} style={styles.errorBanner}><Text style={styles.errorText}>{state.error}</Text></Pressable>}
    {!ready ? <View style={styles.loading}><ActivityIndicator color={C.accent} /></View> : <>
      <View style={styles.transcript} {...drawerSwipe.panHandlers}>
      <FlatList
        ref={listRef}
        style={styles.messages}
        data={rows}
        keyExtractor={item => item.id}
        renderItem={renderItem}
        ListHeaderComponent={state.historyCursor ? <Pressable disabled={state.historyLoading} onPress={() => FccChat.loadOlder()} style={styles.loadEarlier}>
          <Text style={styles.loadEarlierText}>{state.historyLoading ? 'Carregando mensagens…' : 'Carregar mensagens anteriores'}</Text>
        </Pressable> : null}
        ListFooterComponent={footer}
        ListEmptyComponent={<View style={styles.emptyState}><Text style={styles.emptyTitle}>Como posso ajudar?</Text><Text style={styles.muted}>Converse com Claude sobre seu projeto.</Text></View>}
        contentContainerStyle={[styles.messagesContent, {paddingBottom: composerHeight + 24}]}
        onScroll={onListScroll}
        onContentSizeChange={onListContentSizeChange}
        scrollEventThrottle={16}
        removeClippedSubviews={false}
        keyboardShouldPersistTaps="handled"
      />
      {showLatest && <Pressable accessibilityRole="button" accessibilityLabel="Ir para a mensagem mais recente" style={[styles.latestButton, {bottom: composerHeight + 10}]} onPress={() => {
        isNearBottom.current = true;
        setShowLatest(false);
        listRef.current?.scrollToEnd({animated: true});
      }}><Image source={icons.arrow_down} style={{width: 20, height: 20, tintColor: C.text}} /></Pressable>}
      <View pointerEvents="box-none" style={styles.composerOverlay} onLayout={event => setComposerHeight(Math.ceil(event.nativeEvent.layout.height))}>
      {currentAttachments.length > 0 && <ScrollView horizontal style={styles.attachments} contentContainerStyle={styles.attachmentList}>
        {currentAttachments.map(file => <Pressable key={file.key} onPress={() => FccChat.removeAttachment(file.key)} style={styles.attachmentChip}>
          <Text numberOfLines={1} style={styles.attachmentText}>{file.name}  ×</Text>
        </Pressable>)}
      </ScrollView>}
      <View style={styles.composer}>

        <TextInput
          value={state.draft}
          onChangeText={value => { pendingDrafts.current.push(value); setState(previous => ({...previous, draft: value})); FccChat.setDraft(value); }}
          placeholder="Pergunte qualquer coisa"
          placeholderTextColor={C.muted}
          multiline
          maxLength={20000}
          style={styles.composerInput}
          textAlignVertical="center"
          selectionColor={C.accent}
          underlineColorAndroid="transparent"
          autoCapitalize="sentences"
          submitBehavior="newline"
        />
        <View style={styles.composerControls}>
        <IconButton icon="plus" label="Anexar arquivos" onPress={() => FccChat.pickAttachments()} />
        <EffortMenu
          modelId={model || state.bootstrap?.default_model || ""}
          models={state.bootstrap?.models || []}
          effort={effort}
          onSelectEffort={value => { setEffort(value); void FccChat.applySettings(cwd, model, mode, value); }}
          onSelectModel={newModel => {
            setModel(newModel);
            void FccChat.applySettings(cwd, newModel, mode, effort);
          }}
        />
        {state.busy && !state.draft.trim()
          ? <IconButton icon="square" label="Parar resposta" onPress={() => FccChat.stop()} />
          : <IconButton icon="arrow_up" label="Enviar mensagem" filled disabled={!state.draft.trim()} onPress={() => { void send(); }} />}
        </View>
      </View>
      </View>
      </View>
    </>}
    <SettingsModal visible={settings} cwd={cwd} model={model} mode={mode} effort={effort} state={state} onClose={() => setSettings(false)} onChangeCwd={setCwd} onChangeModel={setModel} onChangeMode={setMode} onChangeEffort={setEffort} onSave={async () => { await FccChat.applySettings(cwd, model, mode, effort); setSettings(false); }} />
  </SafeAreaView>
  </KeyboardAvoidingView>
  </DrawerLayoutAndroid>;
}

function SessionDrawer({state, onSelect, onMenu, onNew, onSettings}: {
  state: ChatState; onSelect: (id: string) => void;
  onMenu: (id: string, tag: number) => void; onNew: () => void; onSettings: () => void;
}) {
  return <SafeAreaView style={styles.drawer}>
        <Text style={styles.drawerTitle}>FCC Claude</Text>
        <Text style={styles.muted}>Seu espaço de trabalho</Text>
        <View style={styles.drawerNew}><Pressable accessibilityRole="button" onPress={onNew} style={[styles.action, {flexDirection: 'row', gap: 8}]}><Image source={icons.square_pen} style={{width: 20, height: 20, tintColor: C.accent}}/><Text style={[styles.actionText, {color: C.accent}]}>Nova conversa</Text></Pressable></View>
        <Text style={styles.sectionLabel}>CONVERSAS</Text>
        <ScrollView style={styles.sessionList}>
          {state.sessions.filter(session => session.harness === 'claude').map(session => <View key={session.id} style={[styles.sessionRow, state.session?.id === session.id && styles.sessionActive]}>
            <Pressable android_ripple={{color: '#FFFFFF18'}} style={styles.sessionSelect} onPress={() => onSelect(session.id)}><Text numberOfLines={2} style={styles.sessionTitle}>{session.title || 'Nova conversa'}</Text></Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={`Opções da conversa ${session.title || 'Nova conversa'}`} style={styles.action} onPress={event => onMenu(session.id, Number(event.nativeEvent.target))}><Text style={[styles.actionText, {color: C.muted}]}>⋯</Text></Pressable>
          </View>)}
        </ScrollView>
        <Action label="Configurações" tint={C.text} onPress={onSettings} />
  </SafeAreaView>;
}

function SettingsModal({visible, cwd, model, mode, effort, state, onClose, onChangeCwd, onChangeModel, onChangeMode, onChangeEffort, onSave}: {
  visible: boolean; cwd: string; model: string; mode: string; effort: string; state: ChatState;
  onClose: () => void; onChangeCwd: (value: string) => void; onChangeModel: (value: string) => void;
  onChangeMode: (value: string) => void; onChangeEffort: (value: string) => void; onSave: () => void;
}) {
  const chooseFolder = async () => {
    const selected = await FccChat.chooseProjectFolder(cwd);
    if (selected) onChangeCwd(selected);
  };
  return <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
    <View style={styles.modalRoot}>
      <Pressable android_ripple={null} style={styles.scrim} onPress={onClose} />
      <ScrollView style={styles.settingsPanel} contentContainerStyle={styles.settingsContent}>
        <Text style={styles.drawerTitle}>Configurações</Text>
        <Text style={styles.fieldLabel}>Pasta do projeto</Text>
        <TextInput value={cwd} onChangeText={onChangeCwd} style={styles.textField} placeholderTextColor={C.muted} />
        <Action label="Escolher pasta" tint={C.accent} onPress={chooseFolder} />
        <Text style={styles.fieldLabel}>Modelo</Text>
        <TextInput value={model} onChangeText={onChangeModel} style={styles.textField} placeholder="Modelo padrão" placeholderTextColor={C.muted} />
        <Text style={styles.fieldLabel}>Permissões</Text>
        <View style={styles.choiceRow}>{[['config', 'Padrão'], ['ask', 'Perguntar'], ['full_access', 'Acesso total']].map(([value, label]) => <Action key={value} label={label} tint={mode === value ? C.accent : C.muted} onPress={() => onChangeMode(value)} />)}</View>
        <Text style={styles.fieldLabel}>Esforço de raciocínio</Text>
        <View style={styles.choiceRow}>{['', 'low', 'medium', 'high', 'max'].map(value => <Action key={value || 'default'} label={value || 'Padrão'} tint={effort === value ? C.accent : C.muted} onPress={() => onChangeEffort(value)} />)}</View>
        <View style={styles.promptActions}><Action label="Cancelar" tint={C.muted} onPress={onClose} /><Action label="Salvar" tint={C.accent} onPress={onSave} /></View>
        {!!state.bootstrap?.message && <Text style={styles.muted}>{state.bootstrap.message}</Text>}
      </ScrollView>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  screen: {flex: 1, backgroundColor: C.backdrop},
  topBar: {minHeight: 60, paddingHorizontal: 10, paddingTop: 4, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: C.line},
  headerCenter: {flex: 1, paddingHorizontal: 8}, headerTitle: {color: C.text, fontSize: 16, fontWeight: '600'}, headerPath: {color: C.muted, fontSize: 11, marginTop: 2},
  action: {minHeight: 42, minWidth: 40, paddingHorizontal: 11, alignItems: 'center', justifyContent: 'center', borderRadius: 16}, actionText: {fontSize: 15, fontWeight: '600'}, pressed: {backgroundColor: C.raised}, disabled: {opacity: 0.45},
  messages: {flex: 1, backgroundColor: C.backdrop}, messagesContent: {flexGrow: 1, paddingTop: 12, paddingBottom: 16},
  transcript: {flex: 1}, latestButton: {position: 'absolute', right: 16, bottom: 10, width: 38, height: 38, borderRadius: 19, backgroundColor: C.raised, borderWidth: 1, borderColor: C.line, alignItems: 'center', justifyContent: 'center'}, latestText: {color: C.text, fontSize: 22},
  loading: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  errorBanner: {paddingHorizontal: 14, paddingVertical: 8, backgroundColor: '#482F2B'}, errorText: {color: C.danger, fontSize: 13},
  emptyState: {flex: 1, minHeight: 240, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 24}, emptyTitle: {color: C.text, fontSize: 23, fontWeight: '600', marginBottom: 8},
  assistantRow: {width: '100%', paddingHorizontal: 18, paddingVertical: 2}, userRow: {maxWidth: '88%', alignSelf: 'flex-end', marginHorizontal: 18, marginTop: 14, marginBottom: 24}, userBubble: {backgroundColor: C.raised, borderRadius: 22, paddingHorizontal: 15, paddingVertical: 11}, userText: {color: C.text, fontSize: 15, lineHeight: 22},
  streamMarker: {height: 14, justifyContent: 'center', paddingLeft: 1}, streamDot: {width: 6, height: 6, borderRadius: 3, backgroundColor: C.accent},
  loadEarlier: {alignSelf: 'center', paddingHorizontal: 12, paddingVertical: 8, marginBottom: 8}, loadEarlierText: {color: C.muted, fontSize: 12},
  listFooter: {paddingHorizontal: 12, paddingBottom: 8}, generating: {flexDirection: 'row', alignItems: 'center', gap: 8, marginLeft: 6, marginVertical: 10}, generatingText: {color: C.muted, fontSize: 12}, queued: {backgroundColor: C.surface, borderRadius: 12, padding: 10, marginTop: 6},
  toolCard: {minWidth: 220, maxWidth: '100%', borderWidth: 1, borderColor: C.line, backgroundColor: C.surface, borderRadius: 12, padding: 10, marginTop: 4},
  toolHeader: {flexDirection: 'row', alignItems: 'center'}, toolHeading: {flex: 1, color: C.accent, fontWeight: '600', fontSize: 13}, toolToggle: {color: C.muted, fontSize: 18}, toolBody: {color: C.text, fontSize: 12, lineHeight: 18, marginTop: 8},
  promptCard: {backgroundColor: C.surface, borderRadius: 16, padding: 14, marginTop: 10, borderWidth: 1, borderColor: C.line}, promptTitle: {color: C.accent, fontSize: 14, fontWeight: '700', marginBottom: 8}, questionBlock: {marginTop: 10, gap: 7}, questionText: {color: C.text, fontSize: 14, lineHeight: 20, fontWeight: '500'}, promptActions: {flexDirection: 'row', justifyContent: 'flex-end', marginTop: 8},
  option: {padding: 10, borderColor: C.line, borderWidth: 1, borderRadius: 10}, optionSelected: {borderColor: C.accent, backgroundColor: '#D5BFA318'}, optionLabel: {color: C.text, fontSize: 13}, muted: {color: C.muted, fontSize: 12, lineHeight: 17},
  attachments: {maxHeight: 42, backgroundColor: C.backdrop}, attachmentList: {paddingHorizontal: 10, alignItems: 'center'}, attachmentChip: {maxWidth: 220, backgroundColor: C.surface, borderRadius: 14, paddingHorizontal: 10, paddingVertical: 7, marginRight: 6}, attachmentText: {color: C.text, fontSize: 12},
  composer: {marginHorizontal: 12, marginTop: 8, marginBottom: 10, paddingHorizontal: 10, paddingTop: 10, paddingBottom: 9, borderWidth: 1, borderColor: C.line, borderRadius: 28, backgroundColor: C.raised},
  composerOverlay: {position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: 'transparent'},
  composerInput: {width: '100%', maxHeight: 145, minHeight: 40, paddingHorizontal: 8, paddingTop: 7, paddingBottom: 5, color: C.text, backgroundColor: 'transparent', fontSize: 15, lineHeight: 22},
  composerControls: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4},
  modelButton: {flex: 1, flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center', gap: 6, paddingHorizontal: 10, minHeight: 42},
  modelLabel: {color: C.text, fontSize: 13, flexShrink: 1},
  sendButton: {backgroundColor: C.accent, borderRadius: 19, minWidth: 38, minHeight: 38, width: 38, height: 38, paddingHorizontal: 0},
  sendDisabled: {backgroundColor: '#4A4B4E'},
  modalRoot: {flex: 1, flexDirection: 'row'}, scrim: {flex: 1, backgroundColor: '#0009'}, drawer: {flex: 1, backgroundColor: C.surface, padding: 16}, drawerNew: {backgroundColor: C.raised, borderRadius: 16, marginTop: 18, marginBottom: 6}, drawerTitle: {color: C.text, fontSize: 20, fontWeight: '700', marginBottom: 4}, sectionLabel: {color: C.muted, fontSize: 11, letterSpacing: 1, marginTop: 14, marginBottom: 8}, sessionList: {flex: 1}, sessionRow: {flexDirection: 'row', alignItems: 'center', borderRadius: 12, marginVertical: 2, overflow: 'hidden'}, sessionActive: {backgroundColor: C.raised}, sessionSelect: {flex: 1, minHeight: 52, justifyContent: 'center', paddingHorizontal: 12, paddingVertical: 10}, sessionTitle: {color: C.text, fontSize: 14},
  settingsPanel: {width: '92%', maxWidth: 420, maxHeight: '90%', alignSelf: 'center', backgroundColor: C.surface, borderRadius: 18}, settingsContent: {padding: 18, gap: 8}, fieldLabel: {color: C.muted, fontSize: 12, marginTop: 7}, textField: {minHeight: 44, borderColor: C.line, borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, color: C.text, backgroundColor: C.raised}, choiceRow: {flexDirection: 'row', flexWrap: 'wrap', backgroundColor: C.backdrop, borderRadius: 12},
});
