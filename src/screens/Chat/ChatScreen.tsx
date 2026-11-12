import React, {useCallback, useEffect, useMemo, useState} from 'react';
import { StyleSheet, 
  ActivityIndicator,
  Alert,
  Animated,
  BackHandler,
  DrawerLayoutAndroid,
  Image,
  Keyboard,
  type LayoutChangeEvent,
  PanResponder,
  ScrollView,
  StatusBar,
  Text,
  TextInput,
  type StyleProp,
  View,
  type ViewStyle,
  useWindowDimensions,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets} from 'react-native-safe-area-context';
import { KeyboardChatScrollView, KeyboardStickyView} from 'react-native-keyboard-controller';
import { useKeyboardScrollToEnd} from '@legendapp/list/keyboard';
import { AnchorItem, StreamingItem, StreamingMessageList, type StreamingMessageListRef } from 'react-native-streaming-message-list';
import { useSharedValue } from 'react-native-reanimated';
import Markdown from 'react-native-markdown-display';
import type {RenderRules} from 'react-native-markdown-display';
import { useStyles} from 'react-native-unistyles';
import { EffortMenu} from '../../components/EffortMenu/EffortMenu';
import { ApprovalMenu} from '../../components/ApprovalMenu/ApprovalMenu';
import { ContextMenu, ContextMenuDots } from '../../components/ContextMenu/ContextMenu';
import { GeneratingIndicator} from '../../components/GeneratingIndicator/GeneratingIndicator';
import { Pressable} from '../../components/Pressable/Pressable';
import { WorkActivity, type Run} from '../../components/ToolActivity/ToolActivity';
import { NativeEventEmitter, NativeModules} from 'react-native';
import { colors as C} from '../../theme';
import { stylesheet} from './styles';
import { Header} from '../../components/Header/Header';

const markdownStyles = {
  body: {backgroundColor: 'transparent'},
  text: {color: C.text, fontSize: 16, lineHeight: 24},
  paragraph: {marginTop: 0, marginBottom: 12, flexWrap: 'wrap' as const},
  heading1: {color: C.text, fontSize: 24, lineHeight: 30, fontWeight: '700' as const, marginTop: 12, marginBottom: 8},
  heading2: {color: C.text, fontSize: 21, lineHeight: 27, fontWeight: '700' as const, marginTop: 10, marginBottom: 7},
  heading3: {color: C.text, fontSize: 18, lineHeight: 24, fontWeight: '700' as const, marginTop: 8, marginBottom: 6},
  strong: {fontWeight: '700' as const},
  em: {fontStyle: 'italic' as const},
  s: {textDecorationLine: 'line-through' as const},
  link: {color: C.accent, textDecorationLine: 'underline' as const},
  blockquote: {borderLeftWidth: 3, borderLeftColor: C.accent, paddingHorizontal: 12, marginLeft: 0, backgroundColor: C.surface},
  code_inline: {color: C.text, backgroundColor: C.surface, fontFamily: 'monospace'},
  code_block: {color: C.text, backgroundColor: C.surface, borderColor: C.line, borderWidth: 1, borderRadius: 10, padding: 12, fontFamily: 'monospace'},
  fence: {color: C.text, backgroundColor: C.surface, borderColor: C.line, borderWidth: 1, borderRadius: 10, padding: 12, fontFamily: 'monospace'},
  bullet_list: {marginTop: 0, marginBottom: 10},
  ordered_list: {marginTop: 0, marginBottom: 10},
  bullet_list_icon: {color: C.text},
  ordered_list_icon: {color: C.text},
  table: {borderColor: C.line, borderWidth: 1},
  th: {borderColor: C.line, borderWidth: 1, padding: 6},
  td: {borderColor: C.line, borderWidth: 1, padding: 6},
};

function FadingTail({text}: {text: string}) {
  const [opacity, setOpacity] = useState(0.15);
  useEffect(() => {
    const started = Date.now();
    const timer = setInterval(() => {
      const progress = Math.min(1, (Date.now() - started) / 180);
      setOpacity(0.15 + progress * 0.85);
      if (progress === 1) clearInterval(timer);
    }, 34);
    return () => clearInterval(timer);
  }, []);
  return <Text style={{opacity}}>{text}</Text>;
}

function StreamingMarkdown({content, complete}: {content: string; complete: boolean}) {
  const [visibleContent, setVisibleContent] = useState(() => complete ? content : '');
  const [fadeTailLength, setFadeTailLength] = useState(0);
  const visibleRef = React.useRef(visibleContent);
  const targetRef = React.useRef(content);
  const timerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const revealNextBatch = useCallback(() => {
    timerRef.current = null;
    const target = targetRef.current;
    const current = visibleRef.current;
    if (current.length < target.length) {
      const pending = Array.from(target.slice(current.length));
      const batchSize = Math.min(256, Math.max(8, Math.ceil(pending.length / 4)));
      const addition = pending.slice(0, batchSize).join('');
      visibleRef.current = current + addition;
      setFadeTailLength(addition.length);
      setVisibleContent(visibleRef.current);
    }
    if (visibleRef.current.length < targetRef.current.length) {
      timerRef.current = setTimeout(revealNextBatch, 34);
    }
  }, []);

  useEffect(() => {
    targetRef.current = content;
    const visible = visibleRef.current;
    if (complete && !content.startsWith(visible) && content !== visible) {
      visibleRef.current = content;
      setFadeTailLength(0);
      setVisibleContent(content);
    } else if (!content.startsWith(visible)) {
      let commonLength = 0;
      while (commonLength < visible.length && commonLength < content.length && visible[commonLength] === content[commonLength]) commonLength += 1;
      const commonPrefix = content.slice(0, commonLength);
      visibleRef.current = commonPrefix;
      setFadeTailLength(0);
      setVisibleContent(commonPrefix);
    }
    if (visibleRef.current.length < content.length && timerRef.current === null) {
      timerRef.current = setTimeout(revealNextBatch, 34);
    }
  }, [complete, content, revealNextBatch]);

  useEffect(() => () => {if (timerRef.current !== null) clearTimeout(timerRef.current);}, []);

  const rules = useMemo<RenderRules>(() => ({
    text: (node, _children, _parents, styles, inheritedStyles = {}) => {
      const value = node.content;
      const tailLength = value && visibleContent.endsWith(value) ? Math.min(fadeTailLength, value.length) : 0;
      return <Text key={node.key} style={[inheritedStyles, styles.text]}>
        {tailLength ? value.slice(0, -tailLength) : value}
        {tailLength ? <FadingTail text={value.slice(-tailLength)} /> : null}
      </Text>;
    },
  }), [fadeTailLength, visibleContent]);

  return <Markdown style={markdownStyles} rules={rules}>{visibleContent}</Markdown>;
}

const icons = {
  arrow_up: require('../../assets/lucide/arrow_up.png'),
  arrow_down: require('../../assets/lucide/arrow_down.png'),
  plus: require('../../assets/lucide/plus.png'),
  square_pen: require('../../assets/lucide/square_pen.png'),
  menu: require('../../assets/lucide/menu.png'),
  square: require('../../assets/lucide/square.png'),
  chevron_down: require('../../assets/lucide/chevron_down.png'),
  wifi: require('../../assets/lucide/wifi.png'),
  wifi_off: require('../../assets/lucide/wifi_off.png'),
};
function IconButton({icon, label, onPress, disabled = false, filled = false, buttonStyle, rippleRadius}: {icon: keyof typeof icons; label: string; onPress: () => void; disabled?: boolean; filled?: boolean; buttonStyle?: StyleProp<ViewStyle>; rippleRadius?: number}) {
  const {styles} = useStyles(stylesheet);
  const iconColor = filled ? (disabled ? C.muted : C.backdrop) : C.muted;
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} android_ripple={{color: filled ? '#00000024' : '#FFFFFF20', foreground: true, radius: rippleRadius}} style={[styles.action, buttonStyle, filled && styles.sendButton, filled && disabled && styles.sendDisabled]}>
    {icon === 'square'
      ? <View style={{width: 14, height: 14, borderRadius: 2, backgroundColor: iconColor}} />
      : <Image source={icons[icon]} style={{width: 22, height: 22, tintColor: iconColor}} />}
  </Pressable>;
}

type Session = {
  id: string; cwd: string; title: string; revision: number; status: string;
  harness: string; model: string; reasoningEffort: string; mode: string;
};
type Item = {
  id: string; kind: string; text: string; title: string; complete: boolean;
  runId: string; ordinal: number; sequence: number;
};
type OptimisticMessage = {item: Item; sessionId: string | null};
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
  getBackendConfig(): Promise<string>;
  configureBackend(url: string, token: string): Promise<unknown>;
  refresh(): Promise<unknown>;
  setDraft(text: string): void;
  sendMessage(text: string, clientMessageId: string): Promise<boolean>;
  stop(): Promise<unknown>;
  newSession(cwd: string, model: string, mode: string, effort: string): Promise<unknown>;
  selectSession(id: string): Promise<unknown>;
  deleteSession(id: string): Promise<unknown>;
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
  const {styles} = useStyles(stylesheet);
  return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={[styles.action, disabled && styles.disabled]}>
    <Text style={[styles.actionText, {color: tint}]}>{label}</Text>
  </Pressable>;
}

function PermissionPrompt({prompt, onRespond}: {prompt: Prompt; onRespond: (approved: boolean, answers?: Record<string, any>) => void}) {
  const {styles} = useStyles(stylesheet);
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

type ChatScreenProps = {
  cwd: string;
  model: string;
  mode: string;
  effort: string;
  backendUrl: string;
  backendToken: string;
  onSettings: () => void;
  onConfigChange: (next: {
    cwd: string; model: string; mode: string; effort: string;
    backendUrl: string; backendToken: string;
  }) => void;
};

export default function ChatScreen({cwd: initialCwd, model: initialModel, mode: initialMode, effort: initialEffort, backendUrl: initialBackendUrl, backendToken: initialBackendToken, onSettings, onConfigChange}: ChatScreenProps) {
  const {styles} = useStyles(stylesheet);
  const [ready, setReady] = useState(false);
  const [state, setState] = useState<ChatState>(EMPTY);
  const [optimisticMessages, setOptimisticMessages] = useState<OptimisticMessage[]>([]);
  const [localCwd, setLocalCwd] = useState(initialCwd);
  const [localModel, setLocalModel] = useState(initialModel);
  const [localMode, setLocalMode] = useState(initialMode);
  const [localEffort, setLocalEffort] = useState(initialEffort);
  const [localBackendUrl, setLocalBackendUrl] = useState(initialBackendUrl);
  const [localBackendToken, setLocalBackendToken] = useState(initialBackendToken);
  const insets = useSafeAreaInsets();
  const [drawer, setDrawer] = useState(false);
  const [showLatest, setShowLatest] = useState(false);
  const [listScrolling, setListScrolling] = useState(false);
  const latestOpacity = React.useRef(new Animated.Value(0)).current;
  const topBarScrollY = React.useRef(new Animated.Value(0)).current;
  const scrollIdleTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const scrollDiagnosticTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const listDragging = React.useRef(false);
  const scrollDiagnosticActive = React.useRef(false);
  const previousScrollOffset = React.useRef<number | null>(null);
  const [composerHeight, setComposerHeight] = useState(112);
  const listRef = React.useRef<StreamingMessageListRef>(null);
  const contentInsetEndAdjustment = useSharedValue(composerHeight + 24);
  const lastComposerInset = React.useRef<number | null>(null);
  const onComposerLayout = useCallback((event: LayoutChangeEvent) => {
    const height = event.nativeEvent.layout.height;
    if (height !== lastComposerInset.current) {
      lastComposerInset.current = height;
      contentInsetEndAdjustment.value = height;
    }
  }, [contentInsetEndAdjustment]);
  const keyboardListRef = useMemo<Parameters<typeof useKeyboardScrollToEnd>[0]['listRef']>(() => ({
    get current() {
      const list = listRef.current;
      if (!list) return null;
      return {scrollToEnd: async (options?: Parameters<StreamingMessageListRef['scrollToEnd']>[0]) => { list.scrollToEnd(options); }};
    },
  }), []);
  const {freeze, scrollMessageToEnd} = useKeyboardScrollToEnd({listRef: keyboardListRef});
  const drawerRef = React.useRef<DrawerLayoutAndroid>(null);
  const drawerSwipe = React.useRef(PanResponder.create({
    onMoveShouldSetPanResponderCapture: (_, gesture) => gesture.x0 < 80 && gesture.dx > 28 && gesture.dx > Math.abs(gesture.dy) * 1.8,
    onPanResponderGrant: () => drawerRef.current?.openDrawer(),
  })).current;
  const {width} = useWindowDimensions();
  const isNearBottom = React.useRef(true);
  const pendingDrafts = React.useRef<string[]>([]);
  const sessionId = React.useRef<string | undefined>(undefined);
  const latestVisible = showLatest && !listScrolling;

  useEffect(() => {
    const animation = Animated.timing(latestOpacity, {
      toValue: latestVisible ? 1 : 0,
      duration: 180,
      useNativeDriver: true,
    });
    animation.start();
    return () => animation.stop();
  }, [latestOpacity, latestVisible]);

  useEffect(() => () => {
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    if (scrollDiagnosticTimer.current !== null) clearTimeout(scrollDiagnosticTimer.current);
  }, []);

  useEffect(() => {
    if (!drawer) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      drawerRef.current?.closeDrawer();
      return true;
    });
    return () => subscription.remove();
  }, [drawer]);

  const syncConfig = useCallback((next: {cwd: string; model: string; mode: string; effort: string; backendUrl: string; backendToken: string}) => {
    setLocalCwd(next.cwd);
    setLocalModel(next.model);
    setLocalMode(next.mode);
    setLocalEffort(next.effort);
    setLocalBackendUrl(next.backendUrl);
    setLocalBackendToken(next.backendToken);
    onConfigChange(next);
  }, [onConfigChange]);

  useEffect(() => {
    isNearBottom.current = true;
    setShowLatest(false);
    listDragging.current = false;
    setListScrolling(false);
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    const frame = requestAnimationFrame(() => {
      if (state.items.length > 0) void listRef.current?.scrollToEnd({animated: false});
    });
    return () => cancelAnimationFrame(frame);
  }, [state.session?.id]);

  const applySnapshot = useCallback((value: string) => {
    try {
      const next = JSON.parse(value) as ChatState;
      const changedSession = sessionId.current !== next.session?.id;
      sessionId.current = next.session?.id;
      if (changedSession) pendingDrafts.current = [];
      const acknowledged = pendingDrafts.current.lastIndexOf(next.draft);
      if (acknowledged >= 0) pendingDrafts.current.splice(0, acknowledged + 1);
      if (next.error && !next.sending) pendingDrafts.current = [];
      const serverIds = new Set(next.items.map(item => item.id));
      const queuedTexts = [...next.pendingMessages, ...next.runMessages]
        .filter(message => message.sessionId === next.session?.id)
        .map(message => message.text);
      setOptimisticMessages(previous => {
        const remaining = previous.filter(message => {
          if (serverIds.has(message.item.id)) return false;
          if (message.sessionId && message.sessionId !== next.session?.id) return false;
          if (queuedTexts.includes(message.item.text)) return false;
          if (next.error && !next.sending) return false;
          return true;
        });
        return remaining.length === previous.length ? previous : remaining;
      });
      setState(previous => ({...next, draft: pendingDrafts.current.length ? previous.draft : next.draft}));
      if (next.session?.cwd) {
        const cfg = {cwd: next.session.cwd, model: next.session.model ?? '', mode: next.session.mode || 'config', effort: next.session.reasoningEffort ?? '', backendUrl: localBackendUrl, backendToken: localBackendToken};
        setLocalCwd(cfg.cwd);
        setLocalModel(cfg.model);
        setLocalMode(cfg.mode);
        setLocalEffort(cfg.effort);
        syncConfig(cfg);
      }
    } catch (error) {
      console.warn('Resposta inválida do serviço de chat FCC', error);
    }
  }, [localBackendUrl, localBackendToken, syncConfig]);

  useEffect(() => {
    const emitter = new NativeEventEmitter(NativeModules.FccChat);
    const subscription = emitter.addListener('fccStateChanged', applySnapshot);
    FccChat.getBackendConfig().then(raw => { const config = JSON.parse(raw); setLocalBackendUrl(config.url); setLocalBackendToken(config.token); syncConfig({cwd: localCwd, model: localModel, mode: localMode, effort: localEffort, backendUrl: config.url, backendToken: config.token}); }).finally(() => FccChat.initialize().then(applySnapshot).catch(error => setState(prev => ({...prev, error: String(error)}))).finally(() => setReady(true)));
    return () => subscription.remove();
  }, [applySnapshot, localCwd, localModel, localMode, localEffort, syncConfig]);

  const send = useCallback(async () => {
    const value = state.draft;
    if (!value.trim()) return;
    isNearBottom.current = true;
    setShowLatest(false);
    pendingDrafts.current = [];
    const clientMessageId = `rn-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const isModelCommand = value.trim().toLowerCase() === '/model';
    if (!isModelCommand) {
      const text = value.trim();
      setOptimisticMessages(previous => [...previous, {
        sessionId: state.session?.id ?? null,
        item: {id: clientMessageId, kind: 'user', text, title: '', complete: true, runId: '', ordinal: Number.MAX_SAFE_INTEGER, sequence: Number.MAX_SAFE_INTEGER},
      }]);
      pendingDrafts.current.push('');
      setState(previous => ({...previous, draft: ''}));
    }
    try {
      const sending = FccChat.sendMessage(value, clientMessageId);
      requestAnimationFrame(() => {
        void scrollMessageToEnd({animated: true, closeKeyboard: true});
      });
      const openSettings = await sending;
      if (openSettings === true) onSettings();
    } catch (error) {
      setOptimisticMessages(previous => previous.filter(message => message.item.id !== clientMessageId));
      console.warn('Não foi possível enviar a mensagem', error);
    }
  }, [onSettings, scrollMessageToEnd, state.draft, state.session?.id]);

  const currentAttachments = state.attachments.filter(item => item.sessionId === state.session?.id);

  const footer = <View style={styles.listFooter}>
    {state.busy && <GeneratingIndicator run={state.runs[state.activeRunId]} />}
    {state.prompts.map(prompt => <PermissionPrompt key={prompt.id} prompt={prompt} onRespond={(approved, answers) => FccChat.respondPrompt(prompt.id, approved, answers ? JSON.stringify(answers) : null)} />)}
    {state.pendingMessages.filter(message => message.sessionId === state.session?.id).map(message => <View key={message.id} style={styles.queued}><Text style={styles.muted}>Na fila · {message.text}</Text></View>)}
    {state.runMessages.filter(message => message.sessionId === state.session?.id && message.status !== 'forwarded').map(message => <View key={message.id} style={styles.queued}><Text style={styles.muted}>{message.status === 'uncertain' ? 'Envio pendente de confirmação' : 'Enviando…'} · {message.text}</Text></View>)}
    {!!state.historyError && <Text style={styles.errorText}>{state.historyError}</Text>}
  </View>;

  const visibleItems = useMemo(() => {
    const serverIds = new Set(state.items.map(item => item.id));
    const queuedTexts = [...state.pendingMessages, ...state.runMessages]
      .filter(message => message.sessionId === state.session?.id)
      .map(message => message.text);
    return [...state.items, ...optimisticMessages
      .filter(message => !serverIds.has(message.item.id) &&
        (!message.sessionId || message.sessionId === state.session?.id) &&
        !queuedTexts.includes(message.item.text))
      .map(message => message.item)];
  }, [optimisticMessages, state.items, state.pendingMessages, state.runMessages, state.session?.id]);

  const rows = useMemo(() => {
    const result: (Item & {work?: Item[]})[] = [];
    let index = 0;
    while (index < visibleItems.length) {
      const item = visibleItems[index];
      const run = state.runs[item.runId];
      let end = index;
      if (run && item.kind !== 'user') {
        while (end < visibleItems.length && visibleItems[end].runId === item.runId && visibleItems[end].kind !== 'user') end++;
      }
      let lastTool = -1;
      for (let cursor = index; cursor < end; cursor++) {
        if (visibleItems[cursor].kind === 'tool_call') lastTool = cursor;
      }
      if (run && lastTool >= index) {
        result.push({...item, id: `work:${item.runId}:${item.id}`, work: visibleItems.slice(index, lastTool + 1)});
        index = lastTool + 1;
      } else if (item.kind === 'tool_call') {
        const start = index;
        while (index < visibleItems.length && visibleItems[index].kind === 'tool_call') index++;
        result.push({...item, id: `tools:${item.id}`, work: visibleItems.slice(start, index)});
      } else {
        result.push(item);
        index++;
      }
    }
    return result;
  }, [visibleItems, state.runs]);
  const scrollToLatest = useCallback((animated: boolean) => {
    void listRef.current?.scrollToEnd({animated});
  }, []);
  let anchorRowIndex = -1;
  let streamingRowIndex = -1;
  rows.forEach((row, index) => {
    if (row.kind === 'user') {
      anchorRowIndex = index;
      streamingRowIndex = -1;
    } else if (anchorRowIndex >= 0) {
      streamingRowIndex = index;
    }
  });
  const renderAssistantMessage = useCallback((item: Item, inWork = false) => {
    if (!item.text) return null;
    return <View style={inWork ? styles.workAssistantRow : styles.assistantRow}>
      <StreamingMarkdown content={item.text} complete={item.complete} />
      {!item.complete && <View style={styles.streamMarker}><View style={styles.streamDot} /></View>}
    </View>;
  }, []);
  const renderItem = useCallback(({item, index}: {item: Item & {work?: Item[]}; index: number}) => {
    let content;
    if (item.work) content = <View style={styles.assistantRow}><WorkActivity items={item.work} run={state.runs[item.runId]} renderMessage={workItem => renderAssistantMessage(workItem as Item, true)} /></View>;
    else if (item.kind === 'user') content = <View style={styles.userRow}><View style={styles.userBubble}><Text selectable style={styles.userText}>{item.text}</Text></View></View>;
    else content = renderAssistantMessage(item);
    const row = <View>{content}</View>;
    if (index === anchorRowIndex) return <AnchorItem>{row}</AnchorItem>;
    if (index === streamingRowIndex) return <StreamingItem>{row}</StreamingItem>;
    return row;
  }, [anchorRowIndex, renderAssistantMessage, state.runs, streamingRowIndex]);

  const scheduleScrollIdle = useCallback(() => {
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    scrollIdleTimer.current = setTimeout(() => {
      if (listDragging.current) return;
      setListScrolling(false);
    }, 160);
  }, []);
  const onListScrollBegin = useCallback(() => {
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    setListScrolling(true);
  }, []);
  const onListScrollEnd = useCallback(() => {
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    setListScrolling(false);
  }, []);
  const onListScroll = useCallback((event: any) => {
    const {contentOffset, contentSize, layoutMeasurement} = event.nativeEvent;
    const previousOffset = previousScrollOffset.current;
    if (scrollDiagnosticActive.current) {
      console.warn('[scroll-diag] offset sample', {
        previousOffset,
        offset: contentOffset.y,
        dragging: listDragging.current,
        busy: state.busy,
        contentHeight: contentSize.height,
        viewportHeight: layoutMeasurement.height,
      });
    }
    previousScrollOffset.current = contentOffset.y;
    topBarScrollY.setValue(contentOffset.y);
    isNearBottom.current = contentOffset.y + layoutMeasurement.height >= contentSize.height - 120;
    setShowLatest(!isNearBottom.current);
    setListScrolling(true);
    scheduleScrollIdle();
  }, [scheduleScrollIdle, state.busy, topBarScrollY]);

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
      onDelete={session => Alert.alert(
        'Excluir conversa?',
        `A conversa “${session.title || 'Nova conversa'}” será excluída. Esta ação não pode ser desfeita.`,
        [
          {text: 'Cancelar', style: 'cancel'},
          {text: 'Excluir', style: 'destructive', onPress: () => { FccChat.deleteSession(session.id).catch(console.warn); }},
        ],
      )}
      onNew={() => { drawerRef.current?.closeDrawer(); FccChat.newSession(localCwd, '', localMode, ''); }}
      onSettings={onSettings}
    />}
  >
  <SafeAreaView edges={['bottom', 'left', 'right']} style={styles.screen}>
    <StatusBar backgroundColor="transparent" barStyle="light-content" translucent />
    {!ready || !state.connected ? <View style={styles.loading}><ActivityIndicator color={C.accent} /></View> : <>
      <View style={styles.transcript} {...drawerSwipe.panHandlers}>
      <StreamingMessageList
        ref={listRef}
        style={styles.messages}
        data={rows}
        keyExtractor={item => item.id}
        renderItem={renderItem}
        alignItemsAtEnd
        isStreaming={state.busy}
        ListHeaderComponent={state.historyCursor ? <Pressable disabled={state.historyLoading} onPress={() => FccChat.loadOlder()} style={styles.loadEarlier}>
          <Text style={styles.loadEarlierText}>{state.historyLoading ? 'Carregando mensagens…' : 'Carregar mensagens anteriores'}</Text>
        </Pressable> : null}
        ListFooterComponent={footer}
        contentContainerStyle={[styles.messagesContent, {paddingTop: insets.top + 80, paddingBottom: 24}]}
        renderScrollComponent={scrollProps => <KeyboardChatScrollView
          {...scrollProps}
          freeze={freeze}
          extraContentPadding={contentInsetEndAdjustment}
          keyboardLiftBehavior="whenAtEnd"
          offset={Math.max(0, insets.bottom - 10)}
        />}
        onScroll={onListScroll}
        onScrollBeginDrag={event => {
          if (scrollDiagnosticTimer.current !== null) clearTimeout(scrollDiagnosticTimer.current);
          scrollDiagnosticActive.current = true;
          listDragging.current = true;
          console.warn('[scroll-diag] begin drag', {offset: event.nativeEvent.contentOffset.y});
          onListScrollBegin();
        }}
        onScrollEndDrag={event => {
          listDragging.current = false;
          console.warn('[scroll-diag] end drag', {offset: event.nativeEvent.contentOffset.y});
          if (scrollDiagnosticTimer.current !== null) clearTimeout(scrollDiagnosticTimer.current);
          scrollDiagnosticTimer.current = setTimeout(() => { scrollDiagnosticActive.current = false; }, 2000);
          scheduleScrollIdle();
        }}
        onMomentumScrollBegin={event => {
          if (scrollDiagnosticTimer.current !== null) clearTimeout(scrollDiagnosticTimer.current);
          scrollDiagnosticActive.current = true;
          console.warn('[scroll-diag] momentum begin', {offset: event.nativeEvent.contentOffset.y});
          onListScrollBegin();
        }}
        onMomentumScrollEnd={event => {
          console.warn('[scroll-diag] momentum end', {offset: event.nativeEvent.contentOffset.y});
          onListScrollEnd();
          if (scrollDiagnosticTimer.current !== null) clearTimeout(scrollDiagnosticTimer.current);
          scrollDiagnosticTimer.current = setTimeout(() => { scrollDiagnosticActive.current = false; }, 2000);
        }}
        scrollEventThrottle={16}
        keyboardShouldPersistTaps="handled"
      />
      {rows.length === 0 && <View pointerEvents="none" style={[styles.emptyOverlay, {top: insets.top + 80, bottom: composerHeight + 24}]}>
        <View style={styles.emptyState}><Text style={styles.emptyTitle}>Como posso ajudar?</Text><Text style={styles.muted}>Converse com Claude sobre seu projeto.</Text></View>
      </View>}
      <View pointerEvents="none" style={[styles.transcriptBottomFade, {height: composerHeight + 36}]}>
        <Image source={{uri: 'transcript_bottom_fade'}} style={StyleSheet.absoluteFillObject} />
      </View>
      <Animated.View pointerEvents={latestVisible ? 'auto' : 'none'} accessibilityElementsHidden={!latestVisible} importantForAccessibility={latestVisible ? 'auto' : 'no-hide-descendants'} style={{position: 'absolute', right: 16, bottom: composerHeight + 10, opacity: latestOpacity}}>
      <Pressable accessibilityRole="button" accessibilityLabel="Ir para a mensagem mais recente" style={styles.latestButton} onPress={() => {
        isNearBottom.current = true;
        setShowLatest(false);
        scrollToLatest(true);
      }}><Image source={icons.arrow_down} style={{width: 20, height: 20, tintColor: C.text}} /></Pressable>
      </Animated.View>
      <KeyboardStickyView
        pointerEvents="box-none"
        style={styles.composerOverlay}
        offset={{opened: Math.max(0, insets.bottom - 10)}}
        onLayout={event => setComposerHeight(Math.ceil(event.nativeEvent.layout.height))}
      >
      <View onLayout={onComposerLayout}>
      {currentAttachments.length > 0 && <ScrollView horizontal style={styles.attachments} contentContainerStyle={styles.attachmentList}>
        {currentAttachments.map(file => <Pressable key={file.key} onPress={() => FccChat.removeAttachment(file.key)} style={styles.attachmentChip}>
          <Text numberOfLines={1} style={styles.attachmentText}>{file.name}  ×</Text>
        </Pressable>)}
      </ScrollView>}
      <View style={styles.composer}>

        <View style={styles.composerInputWrap}>
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
        </View>
        <View style={styles.composerControls}>
        <IconButton icon="plus" label="Anexar arquivos" onPress={() => FccChat.pickAttachments()} buttonStyle={styles.composerRoundButton} rippleRadius={20} />
        <ApprovalMenu
          mode={localMode}
          onSelectMode={value => {
            setLocalMode(value);
            syncConfig({cwd: localCwd, model: localModel, mode: value, effort: localEffort, backendUrl: localBackendUrl, backendToken: localBackendToken});
            void FccChat.applySettings(localCwd, localModel, value, localEffort);
          }}
        />
        <EffortMenu
          modelId={localModel || state.bootstrap?.default_model || ""}
          models={state.bootstrap?.models || []}
          effort={localEffort}
          onSelectEffort={value => { setLocalEffort(value); syncConfig({cwd: localCwd, model: localModel, mode: localMode, effort: value, backendUrl: localBackendUrl, backendToken: localBackendToken}); void FccChat.applySettings(localCwd, localModel, localMode, value); }}
          onSelectModel={newModel => {
            setLocalModel(newModel);
            syncConfig({cwd: localCwd, model: newModel, mode: localMode, effort: localEffort, backendUrl: localBackendUrl, backendToken: localBackendToken});
            void FccChat.applySettings(localCwd, newModel, localMode, localEffort);
          }}
        />
        {state.busy && !state.draft.trim()
          ? <IconButton icon="square" label="Parar resposta" filled rippleRadius={19} onPress={() => FccChat.stop()} />
          : <IconButton icon="arrow_up" label="Enviar mensagem" filled disabled={!state.draft.trim()} onPress={() => { void send(); }} />}
        </View>
      </View>
      </View>
      </KeyboardStickyView>
      </View>
    </>}
    <Header
      title={state.session?.title || 'FCC Claude'}
      path={state.session?.cwd || localCwd}
      busy={state.busy}
      scrollY={topBarScrollY}
      onOpenDrawer={() => drawerRef.current?.openDrawer()}
      onNewConversation={() => FccChat.newSession(localCwd, '', localMode, '')}
      onRefresh={() => { void FccChat.refresh(); }}
      onStop={() => FccChat.stop()}
      onSettings={onSettings}
    />
    {!!state.error && <Pressable accessibilityRole="button" onPress={() => FccChat.refresh()} style={[styles.errorBanner, {top: insets.top + 80}]}><Text style={styles.errorText}>{state.error}</Text></Pressable>}

  </SafeAreaView>
  </DrawerLayoutAndroid>;
}

function SessionDrawer({state, onSelect, onDelete, onNew, onSettings}: {
  state: ChatState; onSelect: (id: string) => void;
  onDelete: (session: Session) => void; onNew: () => void; onSettings: () => void;
}) {
  const {styles} = useStyles(stylesheet);
  return <SafeAreaView style={styles.drawer}>
    <View style={styles.drawerHeader}>
      <Text style={styles.drawerTitle}>FCC Claude</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Configurações" onPress={onSettings} android_ripple={{color: '#FFFFFF20'}} style={styles.drawerSettings}>
        <Text style={styles.drawerSettingsIcon}>⚙</Text>
      </Pressable>
    </View>
    <View style={styles.drawerDivider} />
    <Text style={styles.sectionLabel}>Conversas</Text>
    <ScrollView style={styles.sessionList} showsVerticalScrollIndicator={false}>
      {state.sessions.filter(session => session.harness === 'claude').map(session => <View key={session.id} style={[styles.sessionRow, state.session?.id === session.id && styles.sessionActive]}>
        <Pressable accessibilityRole="button" android_ripple={{color: '#FFFFFF18'}} style={styles.sessionSelect} onPress={() => onSelect(session.id)}><Text numberOfLines={1} style={styles.sessionTitle}>{session.title || 'Nova conversa'}</Text></Pressable>
        <ContextMenu
          accessibilityLabel={`Opções da conversa ${session.title || 'Nova conversa'}`}
          actions={[{label: 'Excluir conversa', destructive: true, onPress: () => onDelete(session)}]}
          renderTrigger={({onPress, expanded}) => <Pressable accessibilityRole="button" accessibilityLabel={`Opções da conversa ${session.title || 'Nova conversa'}`} accessibilityState={{expanded}} style={styles.action} onPress={onPress} android_ripple={{color: '#FFFFFF20', foreground: true}}><ContextMenuDots /></Pressable>}
        />
      </View>)}
    </ScrollView>
    <View style={styles.drawerFooter}>
      <Pressable accessibilityRole="button" onPress={onNew} android_ripple={{color: '#FFFFFF20'}} style={styles.drawerNew}>
        <Image source={icons.square_pen} style={styles.drawerNewIcon}/><Text style={styles.drawerNewText}>Nova conversa</Text>
      </Pressable>
    </View>
  </SafeAreaView>;
}
