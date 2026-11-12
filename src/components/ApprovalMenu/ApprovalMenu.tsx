import React, {useEffect, useRef, useState} from 'react';
import {Alert, Animated, AppState, BackHandler, Image, Keyboard, Pressable as NativePressable, ScrollView, StyleSheet, Text, View} from 'react-native';
import {Portal} from '@gorhom/portal';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {stylesheet} from './styles';

type ApprovalMode = 'ask' | 'auto_review' | 'full_access';
const options: {id: ApprovalMode; title: string; description: string; icon: number}[] = [
  {id: 'ask', title: 'Solicitar aprovação', description: 'Sempre pedir aprovação para editar arquivos externos e usar a internet', icon: require('../../assets/lucide/hand.png')},
  {id: 'auto_review', title: 'Aprovar por mim', description: 'O Claude avalia cada ação, aprova as seguras e bloqueia as suspeitas', icon: require('../../assets/lucide/badge_check.png')},
  {id: 'full_access', title: 'Acesso completo', description: 'Aprova ferramentas sem pedir confirmação, salvo regras e ações críticas', icon: require('../../assets/lucide/shield.png')},
];

export function ApprovalMenu({mode, onSelectMode}: {mode: string; onSelectMode: (mode: ApprovalMode) => void}) {
  const {styles: s} = useStyles(stylesheet);
  const [visible, setVisible] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [keyboardSettled, setKeyboardSettled] = useState(true);
  const [position, setPosition] = useState<{left: number; bottom: number; width: number} | null>(null);
  const opacity = useRef(new Animated.Value(0)).current;
  const triggerRef = useRef<View>(null);
  const overlayRef = useRef<View>(null);
  const selectedMode: ApprovalMode = mode === 'auto_review' || mode === 'full_access' ? mode : 'ask';

  const positionMenu = (onPositioned?: () => void) => {
    overlayRef.current?.measureInWindow((overlayX, overlayY, overlayWidth, overlayHeight) => {
      triggerRef.current?.measureInWindow((x, y, width) => {
        const panelWidth = Math.min(420, overlayWidth - 32);
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
      const animation = Animated.timing(opacity, {toValue: 0, duration: 160, useNativeDriver: true});
      animation.start(({finished}) => finished && setMounted(false));
      return () => animation.stop();
    }
    if (!position || !keyboardSettled) return;
    const animation = Animated.timing(opacity, {toValue: 1, duration: 180, useNativeDriver: true});
    animation.start();
    return () => animation.stop();
  }, [visible, position, opacity, keyboardSettled]);

  useEffect(() => {
    if (!visible) return;
    const reposition = () => {
      setPosition(null);
      setKeyboardSettled(false);
      requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(() => positionMenu(() => setKeyboardSettled(true)))));
    };
    const keyboardShown = Keyboard.addListener('keyboardDidShow', reposition);
    const keyboardHidden = Keyboard.addListener('keyboardDidHide', reposition);
    const appState = AppState.addEventListener('change', state => {if (state === 'active') requestAnimationFrame(() => positionMenu());});
    const back = BackHandler.addEventListener('hardwareBackPress', () => {setVisible(false); return true;});
    return () => {keyboardShown.remove(); keyboardHidden.remove(); appState.remove(); back.remove();};
  }, [visible]);

  const open = () => {
    const keyboardVisible = Keyboard.isVisible();
    setKeyboardSettled(!keyboardVisible);
    opacity.setValue(0);
    setPosition(null);
    setMounted(true);
    setVisible(true);
    if (keyboardVisible) requestAnimationFrame(() => requestAnimationFrame(() => positionMenu(() => setKeyboardSettled(true))));
  };

  return <>
    <View ref={triggerRef} collapsable={false} style={s.anchor} onLayout={() => {if (visible) positionMenu();}}>
      <Pressable accessibilityRole="button" accessibilityLabel="Como as ações devem ser aprovadas?" accessibilityState={{expanded: visible}} onPress={open} android_ripple={{color: '#FFFFFF20', foreground: true, radius: 20}} style={s.trigger}>
        <Image source={require('../../assets/lucide/hand.png')} style={s.icon} />
      </Pressable>
    </View>
    {mounted && <Portal>
      <View ref={overlayRef} collapsable={false} style={s.overlay} onLayout={() => positionMenu()}>
        <NativePressable accessibilityLabel="Fechar seletor de aprovação" style={StyleSheet.absoluteFill} onPress={() => setVisible(false)} />
        <Animated.View pointerEvents={visible ? 'auto' : 'none'} style={[s.panelFade, position, {opacity}]}>
          <View style={s.panel}>
            <View style={s.content}>
              <View style={s.heading}>
                <Text style={s.headingText}>Como as ações do Claude devem ser aprovadas?</Text>
                <Pressable accessibilityRole="button" onPress={() => Alert.alert('Aprovação das ações', 'Escolha quanto controle o Claude precisa ter para usar ferramentas, acessar a internet e alterar arquivos nesta sessão.')}>
                  <Text style={s.learnMore}>Saiba mais</Text>
                </Pressable>
              </View>
              <ScrollView style={s.options} bounces={false} showsVerticalScrollIndicator={false}>
                {options.map(option => {
                  const selected = selectedMode === option.id;
                  return <Pressable key={option.id} accessibilityRole="button" accessibilityState={{selected}} onPress={() => {onSelectMode(option.id); setVisible(false);}} style={s.option}>
                    <View style={s.optionIconWrap}><Image source={option.icon} style={[s.optionIcon, option.id === 'full_access' && s.warningIcon]} /></View>
                    <View style={s.optionCopy}>
                      <Text style={[s.optionTitle, option.id === 'full_access' && s.warning]}>{option.title}</Text>
                      <Text style={[s.optionDescription, option.id === 'full_access' && s.warning]}>{option.description}</Text>
                    </View>
                    {selected && <Image source={require('../../assets/lucide/check.png')} style={s.check} />}
                  </Pressable>;
                })}
              </ScrollView>
            </View>
          </View>
        </Animated.View>
      </View>
    </Portal>}
  </>;
}
