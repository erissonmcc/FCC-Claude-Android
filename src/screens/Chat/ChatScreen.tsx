import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  Animated,
  BackHandler,
  DrawerLayoutAndroid,
  FlatList,
  Image,
  Keyboard,
  PanResponder,
  ScrollView,
  type ScrollViewProps,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  type StyleProp,
  View,
  type ViewStyle,
  useWindowDimensions,
} from 'react-native';
import {SafeAreaView, useSafeAreaInsets} from 'react-native-safe-area-context';
import {KeyboardChatScrollView, KeyboardStickyView, type KeyboardChatScrollViewProps, type KeyboardChatScrollViewRef} from 'react-native-keyboard-controller';
import {useSharedValue} from 'react-native-reanimated';
import {CopilotMarkdown, defaultMarkdownStyles} from '@copilotkit/react-native/components';
import {useStyles} from 'react-native-unistyles';
import {EffortMenu} from '../../components/EffortMenu/EffortMenu';
import {GeneratingIndicator} from '../../components/GeneratingIndicator/GeneratingIndicator';
import {Pressable} from '../../components/Pressable/Pressable';
import {WorkActivity, type Run} from '../../components/ToolActivity/ToolActivity';
import type {MarkdownStyle} from 'react-native-enriched-markdown';
import {NativeEventEmitter, NativeModules} from 'react-native';
import {colors as C} from '../../theme';
import {stylesheet} from './styles';
import {SettingsScreen} from '../Settings/SettingsScreen';
import {Header} from '../../components/Header/Header';

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

const VirtualizedListScrollView = React.forwardRef<
  KeyboardChatScrollViewRef,
  ScrollViewProps & KeyboardChatScrollViewProps
>((props, ref) => <KeyboardChatScrollView
  ref={ref}
  {...props}
  automaticallyAdjustContentInsets={false}
  contentInsetAdjustmentBehavior="never"
  keyboardLiftBehavior="whenAtEnd"
/>);

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
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} android_ripple={{color: filled ? '#00000024' : '#FFFFFF20', foreground: true, radius: rippleRadius}} style={[styles.action, buttonStyle, filled && styles.sendButton, filled && disabled && styles.sendDisabled]}><Image source={icons[icon]} style={{width: 22, height: 22, tintColor: filled ? (disabled ? C.muted : C.backdrop) : C.muted}} /></Pressable>;
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

export default function ChatScreen() {
  const {styles} = useStyles(stylesheet);
  const [state, setState] = useState<ChatState>(EMPTY);
  const [ready, setReady] = useState(false);
  const insets = useSafeAreaInsets();
  const [drawer, setDrawer] = useState(false);
  const [settings, setSettings] = useState(false);
  const [model, setModel] = useState('');
  const [mode, setMode] = useState('config');
  const [effort, setEffort] = useState('');
  const [cwd, setCwd] = useState('/storage/emulated/0');
  const [showLatest, setShowLatest] = useState(false);
  const [listScrolling, setListScrolling] = useState(false);
  const latestOpacity = React.useRef(new Animated.Value(0)).current;
  const scrollIdleTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const listDragging = React.useRef(false);
  const [composerHeight, setComposerHeight] = useState(112);
  const [transcriptHeight, setTranscriptHeight] = useState(0);
  const blankSpace = useSharedValue(0);
  const rowHeights = React.useRef(new Map<string, number>());
  const footerHeight = React.useRef(0);
  const contentHeight = React.useRef(0);
  const listHeight = React.useRef(0);
  const bottomInset = React.useRef(0);
  const anchorId = React.useRef<string | null>(null);
  const rowsRef = React.useRef<(Item & {work?: Item[]})[]>([]);
  const composerHeightRef = React.useRef(composerHeight);
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
  }, []);

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
    anchorId.current = null;
    rowHeights.current.clear();
    footerHeight.current = 0;
    blankSpace.value = 0;
    setShowLatest(false);
    listDragging.current = false;
    setListScrolling(false);
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    const frame = requestAnimationFrame(() => {
      if (state.items.length > 0) listRef.current?.scrollToOffset({offset: Math.max(0, contentHeight.current - listHeight.current + bottomInset.current), animated: false});
    });
    return () => cancelAnimationFrame(frame);
  }, [blankSpace, state.session?.id]);

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
    const value = state.draft;
    if (!value.trim()) return;
    isNearBottom.current = true;
    setShowLatest(false);
    pendingDrafts.current = [];
    try {
      const openSettings = await FccChat.sendMessage(value);
      if (openSettings === true) setSettings(true);
    } catch (error) {
      console.warn('Não foi possível enviar a mensagem', error);
    }
  }, [state.draft]);

  const currentAttachments = state.attachments.filter(item => item.sessionId === state.session?.id);

  const footer = <View style={styles.listFooter}>
    {state.busy && <GeneratingIndicator run={state.runs[state.activeRunId]} />}
    {state.prompts.map(prompt => <PermissionPrompt key={prompt.id} prompt={prompt} onRespond={(approved, answers) => FccChat.respondPrompt(prompt.id, approved, answers ? JSON.stringify(answers) : null)} />)}
    {state.pendingMessages.filter(message => message.sessionId === state.session?.id).map(message => <View key={message.id} style={styles.queued}><Text style={styles.muted}>Na fila · {message.text}</Text></View>)}
    {state.runMessages.filter(message => message.sessionId === state.session?.id && message.status !== 'forwarded').map(message => <View key={message.id} style={styles.queued}><Text style={styles.muted}>{message.status === 'uncertain' ? 'Envio pendente de confirmação' : 'Enviando…'} · {message.text}</Text></View>)}
    {!!state.historyError && <Text style={styles.errorText}>{state.historyError}</Text>}
  </View>;

  const rows = useMemo(() => {
    const result: (Item & {work?: Item[]})[] = [];
    let index = 0;
    while (index < state.items.length) {
      const item = state.items[index];
      const run = state.runs[item.runId];
      let end = index;
      if (run && item.kind !== 'user') {
        while (end < state.items.length && state.items[end].runId === item.runId && state.items[end].kind !== 'user') end++;
      }
      let lastTool = -1;
      for (let cursor = index; cursor < end; cursor++) {
        if (state.items[cursor].kind === 'tool_call') lastTool = cursor;
      }
      if (run && lastTool >= index) {
        result.push({...item, id: `work:${item.runId}:${item.id}`, work: state.items.slice(index, lastTool + 1)});
        index = lastTool + 1;
      } else if (item.kind === 'tool_call') {
        const start = index;
        while (index < state.items.length && state.items[index].kind === 'tool_call') index++;
        result.push({...item, id: `tools:${item.id}`, work: state.items.slice(start, index)});
      } else {
        result.push(item);
        index++;
      }
    }
    return result;
  }, [state.items, state.runs]);
  rowsRef.current = rows;
  composerHeightRef.current = composerHeight;
  const recomputeBlankSpace = useCallback(() => {
    const anchorIndex = rowsRef.current.findIndex(item => item.id === anchorId.current);
    if (!state.busy || anchorIndex < 0) {
      blankSpace.value = 0;
      return;
    }
    const contentBelowAnchor = rowsRef.current.slice(anchorIndex + 1).reduce(
      (height, item) => height + (rowHeights.current.get(item.id) ?? 0),
      footerHeight.current + composerHeightRef.current + 24,
    );
    const scrollableHeight = Math.max(0, listHeight.current - composerHeightRef.current - 24);
    blankSpace.value = Math.max(0, scrollableHeight - contentBelowAnchor);
  }, [blankSpace, state.busy]);
  const scrollToLatest = useCallback((animated: boolean) => {
    listRef.current?.scrollToOffset({
      offset: Math.max(0, contentHeight.current - listHeight.current + bottomInset.current),
      animated,
    });
  }, []);
  const onContentInsetChange = useCallback(({bottom}: {bottom: number}) => {
    bottomInset.current = bottom;
  }, []);
  const onEndVisible = useCallback((visible: boolean) => {
    isNearBottom.current = visible;
    setShowLatest(!visible);
  }, []);
  const renderScrollComponent = useCallback((props: ScrollViewProps) => (
    <VirtualizedListScrollView {...props} blankSpace={blankSpace} offset={insets.bottom} onContentInsetChange={onContentInsetChange} onEndVisible={onEndVisible} />
  ), [blankSpace, insets.bottom, onContentInsetChange, onEndVisible]);
  useEffect(() => {
    if (!state.busy || (!isNearBottom.current && anchorId.current === null)) {
      anchorId.current = null;
      blankSpace.value = 0;
      return;
    }
    const latestUser = [...rows].reverse().find(item => item.kind === 'user');
    if (latestUser && anchorId.current !== latestUser.id) anchorId.current = latestUser.id;
    recomputeBlankSpace();
    requestAnimationFrame(() => scrollToLatest(false));
  }, [blankSpace, recomputeBlankSpace, rows, scrollToLatest, state.busy]);
  const renderAssistantMessage = useCallback((item: Item) => {
    if (!item.text) return null;
    return <View style={styles.assistantRow}>
      <CopilotMarkdown content={item.text} style={markdownStyle} streamingAnimation={!item.complete} />
      {!item.complete && <View style={styles.streamMarker}><View style={styles.streamDot} /></View>}
    </View>;
  }, []);
  const renderItem = useCallback(({item}: {item: Item & {work?: Item[]}}) => {
    let content;
    if (item.work) content = <View style={styles.assistantRow}><WorkActivity items={item.work} run={state.runs[item.runId]} renderMessage={workItem => renderAssistantMessage(workItem as Item)} /></View>;
    else if (item.kind === 'user') content = <View style={styles.userRow}><View style={styles.userBubble}><Text selectable style={styles.userText}>{item.text}</Text></View></View>;
    else content = renderAssistantMessage(item);
    return <View onLayout={event => { rowHeights.current.set(item.id, event.nativeEvent.layout.height); recomputeBlankSpace(); }}>{content}</View>;
  }, [recomputeBlankSpace, renderAssistantMessage, state.runs]);

  const scheduleScrollIdle = useCallback(() => {
    if (scrollIdleTimer.current !== null) clearTimeout(scrollIdleTimer.current);
    scrollIdleTimer.current = setTimeout(() => {
      if (!listDragging.current) setListScrolling(false);
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
    isNearBottom.current = contentOffset.y + layoutMeasurement.height >= contentSize.height - 120;
    setShowLatest(!isNearBottom.current);
    setListScrolling(true);
    scheduleScrollIdle();
  }, [scheduleScrollIdle]);
  const onListContentSizeChange = useCallback(() => {
    recomputeBlankSpace();
    if ((isNearBottom.current || anchorId.current !== null) && !listDragging.current) scrollToLatest(false);
  }, [recomputeBlankSpace, scrollToLatest]);

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
  <SafeAreaView edges={['bottom', 'left', 'right']} style={styles.screen}>
    <StatusBar backgroundColor="transparent" barStyle="light-content" translucent />
    {!!state.error && <Pressable onPress={() => FccChat.refresh()} style={styles.errorBanner}><Text style={styles.errorText}>{state.error}</Text></Pressable>}
    {!ready ? <View style={styles.loading}><ActivityIndicator color={C.accent} /></View> : <>
      <View style={styles.transcript} {...drawerSwipe.panHandlers} onLayout={event => setTranscriptHeight(event.nativeEvent.layout.height)}>
      <FlatList
        ref={listRef}
        style={styles.messages}
        data={rows}
        keyExtractor={item => item.id}
        renderItem={renderItem}
        ListHeaderComponent={state.historyCursor ? <Pressable disabled={state.historyLoading} onPress={() => FccChat.loadOlder()} style={styles.loadEarlier}>
          <Text style={styles.loadEarlierText}>{state.historyLoading ? 'Carregando mensagens…' : 'Carregar mensagens anteriores'}</Text>
        </Pressable> : null}
        ListFooterComponent={<View onLayout={event => { footerHeight.current = event.nativeEvent.layout.height; recomputeBlankSpace(); }}>{footer}</View>}
        ListEmptyComponent={<View style={styles.emptyState}><Text style={styles.emptyTitle}>Como posso ajudar?</Text><Text style={styles.muted}>Converse com Claude sobre seu projeto.</Text></View>}
        contentContainerStyle={[styles.messagesContent, {paddingTop: insets.top + 80, paddingBottom: composerHeight + 24, minHeight: transcriptHeight}]}
        renderScrollComponent={renderScrollComponent}
        onLayout={event => { listHeight.current = event.nativeEvent.layout.height; recomputeBlankSpace(); }}
        onScroll={onListScroll}
        onScrollBeginDrag={() => { listDragging.current = true; anchorId.current = null; blankSpace.value = 0; onListScrollBegin(); }}
        onScrollEndDrag={() => { listDragging.current = false; scheduleScrollIdle(); }}
        onMomentumScrollBegin={onListScrollBegin}
        onMomentumScrollEnd={onListScrollEnd}
        onContentSizeChange={(_, height) => { contentHeight.current = height; onListContentSizeChange(); }}
        scrollEventThrottle={16}
        removeClippedSubviews={false}
        keyboardShouldPersistTaps="handled"
      />
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
      </KeyboardStickyView>
      </View>
    </>}
    <Header
      title={state.session?.title || 'FCC Claude'}
      path={state.session?.cwd || cwd}
      busy={state.busy}
      onOpenDrawer={() => drawerRef.current?.openDrawer()}
      onNewConversation={() => FccChat.newSession(cwd, '', 'config', '')}
      onRefresh={() => { void FccChat.refresh(); }}
      onStop={() => FccChat.stop()}
      onSettings={() => setSettings(true)}
    />
    <SettingsScreen visible={settings} cwd={cwd} model={model} mode={mode} effort={effort} message={state.bootstrap?.message} onClose={() => setSettings(false)} onChooseFolder={async () => { const selected = await FccChat.chooseProjectFolder(cwd); if (selected) setCwd(selected); }} onChangeCwd={setCwd} onChangeModel={setModel} onChangeMode={setMode} onChangeEffort={setEffort} onSave={async () => { await FccChat.applySettings(cwd, model, mode, effort); setSettings(false); }} />
  </SafeAreaView>
  </DrawerLayoutAndroid>;
}

function SessionDrawer({state, onSelect, onMenu, onNew, onSettings}: {
  state: ChatState; onSelect: (id: string) => void;
  onMenu: (id: string, tag: number) => void; onNew: () => void; onSettings: () => void;
}) {
  const {styles} = useStyles(stylesheet);
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
