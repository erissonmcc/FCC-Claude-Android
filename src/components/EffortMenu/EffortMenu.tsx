import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Animated, AppState, BackHandler, Image, Keyboard, PanResponder, ScrollView, StyleSheet, Text, View} from 'react-native';
import {Portal} from '@gorhom/portal';
import Reanimated, {runOnJS, useAnimatedStyle, useSharedValue, withTiming} from 'react-native-reanimated';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {stylesheet} from './styles';

const labels: Record<string,string> = {minimal:'Mínimo',low:'Leve',medium:'Médio',high:'Alto',xhigh:'Muito alto',max:'Máximo'};
export type Model = {id:string; display_name?:string; reasoning_efforts?:string[]; default_reasoning_effort?:string};

export function displayName(model: Model | undefined, id: string) {
  if (model?.display_name && model.display_name !== id) return model.display_name;
  const parts = id.split('/').pop()!.split(/[-_]+/);
  if (parts[0]?.toLowerCase() === 'gpt' && parts.length > 1) return `GPT-${parts[1]}${parts.slice(2).map(part => ' ' + part.charAt(0).toUpperCase() + part.slice(1)).join('')}`;
  if (parts[0]?.toLowerCase() === 'gemini' && parts.length > 1) return `Gemini ${parts.slice(1).map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(' ')}`;
  return parts.map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(' ') || 'Modelo padrão';
}

export function EffortMenu({
  modelId,
  models,
  effort,
  onSelectEffort,
  onSelectModel,
}: {
  modelId: string;
  models: Model[];
  effort: string;
  onSelectEffort: (effort: string) => void;
  onSelectModel: (modelId: string) => void;
}) {
  const {styles: s} = useStyles(stylesheet);
  const [visible, setVisible] = useState(false);
  const [mounted, setMounted] = useState(false);
  const opacity = useRef(new Animated.Value(0)).current;
  const [keyboardSettled, setKeyboardSettled] = useState(true);
  const [view, setView] = useState<'effort' | 'models'>('effort');
  const triggerRef = useRef<View>(null);
  const overlayRef = useRef<View>(null);
  const [position, setPosition] = useState<{left: number; bottom: number; width: number} | null>(null);

  // Filtra apenas modelos da OpenAI e Google Vertex AI
  const filteredModels = models.filter(item => {
    const id = item.id.toLowerCase();
    return id.startsWith('openai/') || id.startsWith('vertex/');
  });

  const model = models.find(entry => entry.id === modelId);
  const values = model?.reasoning_efforts ?? [];
  const selectedEffort = values.includes(effort) ? effort : model?.default_reasoning_effort || values[0] || '';
  const [previewEffort, setPreviewEffort] = useState(selectedEffort);
  const name = displayName(model, modelId);
  const compactHeight = values.length > 0 ? 130 : 86;
  const panelHeight = useSharedValue(compactHeight);
  const animatedPanelStyle = useAnimatedStyle(() => ({height: panelHeight.value}));

  useEffect(() => setPreviewEffort(selectedEffort), [selectedEffort]);

  const selectEffort = useCallback((value: string) => {
    setPreviewEffort(value);
    onSelectEffort(value);
  }, [onSelectEffort]);

  const showModels = () => {
    setView('models');
    panelHeight.value = withTiming(300, {duration: 260});
  };

  const showEffort = () => {
    setView('effort');
    panelHeight.value = withTiming(compactHeight, {duration: 260});
  };

  useEffect(() => {
    if (visible) return;
    const animation = Animated.timing(opacity, {
      toValue: 0, duration: 160, useNativeDriver: true,
    });
    animation.start(({finished}) => {
      if (finished) {
        setMounted(false);
        setView('effort');
      }
    });
    return () => animation.stop();
  }, [visible, opacity]);

  useEffect(() => {
    if (!visible || !position || !keyboardSettled) return;
    const animation = Animated.timing(opacity, {
      toValue: 1, duration: 180, useNativeDriver: true,
    });
    animation.start();
    return () => animation.stop();
  }, [visible, position, opacity, keyboardSettled]);

  const positionMenu = (onPositioned?: () => void) => {
    overlayRef.current?.measureInWindow((overlayX, overlayY, overlayWidth, overlayHeight) => {
      triggerRef.current?.measureInWindow((x, y, width) => {
        const panelWidth = Math.min(320, overlayWidth - 32);
        setPosition({
          width: panelWidth,
          left: Math.max(16, Math.min(x + width - overlayX - panelWidth, overlayWidth - panelWidth - 16)),
          bottom: overlayHeight - (y - overlayY) + 8,
        });
        onPositioned?.();
      });
    });
  };

  useEffect(() => {
    if (!visible) {
      return;
    }
    const repositionAfterKeyboardLayout = () => {
      setPosition(null);
      setKeyboardSettled(false);
      requestAnimationFrame(() => {
        requestAnimationFrame(() => requestAnimationFrame(() => positionMenu(() => setKeyboardSettled(true))));
      });
    };
    const keyboardShown = Keyboard.addListener('keyboardDidShow', repositionAfterKeyboardLayout);
    const keyboardHidden = Keyboard.addListener('keyboardDidHide', repositionAfterKeyboardLayout);
    const appState = AppState.addEventListener('change', state => {
      if (state === 'active') requestAnimationFrame(() => positionMenu());
    });
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      if (view === 'models') {
        showEffort();
        return true;
      }
      setVisible(false);
      return true;
    });
    return () => {
      keyboardShown.remove();
      keyboardHidden.remove();
      appState.remove();
      back.remove();
    };
  }, [visible, view]);

  return (
    <>
      <View ref={triggerRef} collapsable={false} style={s.anchor} onLayout={() => { if (visible) positionMenu(); }}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Modelo e esforço de raciocínio"
          accessibilityState={{expanded: visible}}
          onPress={() => {
            const keyboardVisible = Keyboard.isVisible();
            setKeyboardSettled(!keyboardVisible);
            opacity.setValue(0);
            setPosition(null);
            setView('effort');
            panelHeight.value = compactHeight;
            setMounted(true);
            setVisible(true);
            if (keyboardVisible) {
              requestAnimationFrame(() => requestAnimationFrame(() => positionMenu(() => setKeyboardSettled(true))));
            }
          }}
          style={s.trigger}
        >
          {mounted ? (
            <Text numberOfLines={1} style={s.muted}>Selecionar esforço</Text>
          ) : (
            <>
              <Text numberOfLines={1} style={s.name}>{name}</Text>
              {!!selectedEffort && <Text style={s.muted}>{labels[selectedEffort] || selectedEffort}</Text>}
            </>
          )}
          <Image source={require('../../assets/lucide/chevron_down.png')} style={s.smallIcon} />
        </Pressable>
      </View>

      {mounted && (
        <Portal>
          <View ref={overlayRef} collapsable={false} style={s.overlay} onLayout={() => positionMenu()}>
            <Pressable
              android_ripple={null}
              accessibilityLabel="Fechar seletor"
              style={StyleSheet.absoluteFill}
              onPress={() => setVisible(false)}
            />
            <Animated.View pointerEvents={visible ? 'auto' : 'none'} style={[s.panelFade, position, {opacity}]}>
            <Reanimated.View style={[s.panelAnchor, animatedPanelStyle]}>
            <Reanimated.View style={s.panel}>
              <View key={view} collapsable={false} style={s.panelContent}>
              {view === 'effort' ? (
                <>
                  <View style={s.heading}>
                    <Pressable style={s.resetButton} accessibilityRole="button" accessibilityLabel="Raio" onPress={() => {}}>
                      <Image source={require('../../assets/lucide/zap.png')} style={s.resetIcon} />
                    </Pressable>
                    <Text style={s.previewTitle}>
                      <Text style={s.previewValue}>{labels[previewEffort] || previewEffort}</Text>
                    </Text>
                    <Pressable
                      style={s.resetButton}
                      accessibilityRole="button"
                      accessibilityLabel="Restaurar esforço padrão"
                      disabled={!model?.default_reasoning_effort}
                      onPress={() => model?.default_reasoning_effort && onSelectEffort(model.default_reasoning_effort)}
                      hitSlop={10}
                    >
                      <Image source={require('../../assets/lucide/refresh_ccw.png')} style={s.resetIcon} />
                    </Pressable>
                  </View>

                  <Pressable style={s.modelRow} onPress={showModels}>
                    <Text numberOfLines={1} style={s.name}>{name}</Text>
                    <Image source={require('../../assets/lucide/chevron_right.png')} style={s.smallIcon} />
                  </Pressable>

                  {values.length > 0 && (
                    <EffortSlider
                      key={`${modelId}-${selectedEffort}`}
                      values={values}
                      selected={selectedEffort}
                      onSelect={selectEffort}
                      onPreview={setPreviewEffort}
                    />
                  )}
                </>
              ) : (
                <View style={s.modelsContainer}>
                  <View style={s.modelsHeader}>
                    <Pressable
                      style={s.backButton}
                      accessibilityRole="button"
                      accessibilityLabel="Voltar ao esforço"
                      onPress={showEffort}
                      hitSlop={8}
                    >
                      <Image source={require('../../assets/lucide/chevron_right.png')} style={[s.smallIcon, {transform: [{rotate: '180deg'}]}]} />
                    </Pressable>
                    <Text style={s.modelsTitle}>Selecionar modelo</Text>
                    <View style={s.backButton} />
                  </View>

                  <ScrollView style={s.modelsList} contentContainerStyle={s.modelsListContent} bounces={false}>
                    <Pressable
                      style={[s.modelItem, !modelId && s.modelItemActive]}
                      onPress={() => {
                        onSelectModel('');
                        showEffort();
                      }}
                    >
                      <View style={s.modelInfo}>
                        <Text style={[s.modelItemTitle, !modelId && s.modelItemTitleActive]}>Padrão</Text>
                        <Text style={s.modelItemSubtitle}>Conjunto recomendado de modelos</Text>
                      </View>
                      {!modelId && <Image source={require('../../assets/lucide/check.png')} style={s.checkIcon} />}
                    </Pressable>

                    {filteredModels.map(item => {
                      const isSelected = item.id === modelId;
                      const itemTitle = displayName(item, item.id);
                      return (
                        <Pressable
                          key={item.id}
                          style={[s.modelItem, isSelected && s.modelItemActive]}
                          onPress={() => {
                            onSelectModel(item.id);
                            showEffort();
                          }}
                        >
                          <View style={s.modelInfo}>
                            <Text numberOfLines={1} style={[s.modelItemTitle, isSelected && s.modelItemTitleActive]}>
                              {itemTitle}
                            </Text>
                          </View>
                          {isSelected && <Image source={require('../../assets/lucide/check.png')} style={s.checkIcon} />}
                        </Pressable>
                      );
                    })}
                  </ScrollView>
                </View>
              )}
              </View>
            </Reanimated.View>
            </Reanimated.View>
            </Animated.View>
          </View>
        </Portal>
      )}
    </>
  );
}

const EffortSlider = React.memo(function EffortSlider({values, selected, onSelect, onPreview}: {
  values: string[];
  selected: string;
  onSelect: (value: string) => void;
  onPreview: (value: string) => void;
}) {
  const {styles: s} = useStyles(stylesheet);
  const initialIndex = Math.max(0, values.indexOf(selected));
  const previewIndex = useRef(initialIndex);
  const [layoutWidth, setLayoutWidth] = useState(0);
  const sliderRef = useRef<View>(null);
  const width = useRef(0);
  const pageXOffset = useRef(0);
  const didMove = useRef(false);
  const currentProgress = useRef(initialIndex / Math.max(1, values.length - 1));
  const progress = useSharedValue(currentProgress.current);
  const latest = useRef({values, onSelect, onPreview});
  latest.current = {values, onSelect, onPreview};
  const endpointInset = 32;
  const travel = Math.max(0, layoutWidth - endpointInset * 2);

  const fillStyle = useAnimatedStyle(() => ({transform: [{translateX: -travel * (1 - progress.value)}]}));
  const thumbStyle = useAnimatedStyle(() => ({left: endpointInset + travel * progress.value - 14}));

  const progressFromPageX = (pageX: number) => {
    const x = pageX - pageXOffset.current;
    const trackWidth = Math.max(1, width.current - endpointInset * 2);
    return Math.max(0, Math.min(1, (x - endpointInset) / trackWidth));
  };

  const updateFromPageX = (pageX: number) => {
    const nextProgress = progressFromPageX(pageX);
    currentProgress.current = nextProgress;
    progress.value = nextProgress;
    const nextIndex = Math.round(nextProgress * Math.max(0, latest.current.values.length - 1));
    if (nextIndex !== previewIndex.current) {
      previewIndex.current = nextIndex;
      latest.current.onPreview(latest.current.values[nextIndex]);
    }
  };

  const selectIndex = (index: number) => latest.current.onSelect(latest.current.values[index]);

  const responder = useRef(PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onMoveShouldSetPanResponder: () => true,
    onPanResponderGrant: () => { didMove.current = false; },
    onPanResponderMove: event => {
      didMove.current = true;
      updateFromPageX(event.nativeEvent.pageX);
    },
    onPanResponderRelease: event => {
      const count = latest.current.values.length;
      const releaseProgress = didMove.current ? currentProgress.current : progressFromPageX(event.nativeEvent.pageX);
      const nearestIndex = Math.round(releaseProgress * Math.max(0, count - 1));
      const snappedProgress = nearestIndex / Math.max(1, count - 1);
      currentProgress.current = snappedProgress;
      previewIndex.current = nearestIndex;
      latest.current.onPreview(latest.current.values[nearestIndex]);
      progress.value = withTiming(snappedProgress, {duration: 160}, finished => {
        if (finished) runOnJS(selectIndex)(nearestIndex);
      });
    },
    onPanResponderTerminationRequest: () => false,
  })).current;

  return (
    <View>
      <View
        ref={sliderRef}
        collapsable={false}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Esforço de raciocínio"
        accessibilityValue={{min: 0, max: values.length - 1, now: previewIndex.current, text: labels[values[previewIndex.current]] || values[previewIndex.current]}}
        accessibilityActions={[{name: 'increment'}, {name: 'decrement'}]}
        onAccessibilityAction={event => {
          const next = Math.max(0, Math.min(values.length - 1, previewIndex.current + (event.nativeEvent.actionName === 'increment' ? 1 : -1)));
          previewIndex.current = next;
          onPreview(values[next]);
          onSelect(values[next]);
        }}
        onLayout={event => {
          width.current = event.nativeEvent.layout.width;
          setLayoutWidth(width.current);
          sliderRef.current?.measureInWindow(x => { pageXOffset.current = x; });
        }}
        {...responder.panHandlers}
        style={s.slider}
      >
        <View pointerEvents="none" style={s.track}>
          <Reanimated.View style={[s.fill, fillStyle]} />
        </View>
        <View pointerEvents="none" style={[s.steps, {left: endpointInset, right: endpointInset}]}>
          {values.map(value => <View key={value} style={s.dot} />)}
        </View>
        <Reanimated.View pointerEvents="none" style={[s.thumb, thumbStyle]} />
      </View>
    </View>
  );
});
