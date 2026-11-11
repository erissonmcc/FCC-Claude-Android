import React, {useEffect, useState} from 'react';
import {Animated, BackHandler, Image, Pressable as NativePressable, StyleSheet, Text, View} from 'react-native';
import {Portal} from '@gorhom/portal';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {stylesheet} from './styles';

const icons = {
  menu: require('../../assets/lucide/menu.png'),
  square_pen: require('../../assets/lucide/square_pen.png'),
  square: require('../../assets/lucide/square.png'),
  wifi: require('../../assets/lucide/wifi.png'),
};

type HeaderProps = {
  title?: string;
  path?: string;
  busy: boolean;
  scrollY: Animated.Value;
  onOpenDrawer: () => void;
  onNewConversation: () => void;
  onRefresh: () => void;
  onStop: () => void;
  onSettings: () => void;
};

export function Header({busy, scrollY, onOpenDrawer, onNewConversation, onRefresh, onStop, onSettings}: HeaderProps) {
  const {styles} = useStyles(stylesheet);
  const insets = useSafeAreaInsets();
  const backdropOpacity = scrollY.interpolate({inputRange: [0, 20, 48], outputRange: [0, 0.58, 1], extrapolate: 'clamp'});
  return <View pointerEvents="box-none" style={styles.topBarOverlay}>
    <Animated.View pointerEvents="none" style={[styles.topBarBackdrop, {opacity: backdropOpacity}]}>
      <Image source={{uri: 'topbar_backdrop_fade'}} resizeMode="stretch" style={StyleSheet.absoluteFillObject} />
    </Animated.View>
    <View style={[styles.topBar, {paddingTop: insets.top + 8}]}>
      <Pressable accessibilityRole="button" accessibilityLabel="Abrir conversas" onPress={onOpenDrawer} android_ripple={{color: '#FFFFFF20', radius: 24, foreground: true}} style={styles.drawerButton}>
        <Image source={icons.menu} style={styles.drawerIcon} />
      </Pressable>
      <View style={styles.headerCenter} />
      <View style={styles.headerActions}>
        <Pressable accessibilityRole="button" accessibilityLabel="Nova conversa" onPress={onNewConversation} android_ripple={{color: '#FFFFFF20', radius: 24, foreground: true}} style={styles.headerAction}>
          <Image source={icons.square_pen} style={styles.headerActionIcon} />
        </Pressable>
        <HeaderMenu busy={busy} onRefresh={onRefresh} onStop={onStop} onSettings={onSettings} />
      </View>
    </View>
  </View>;
}

function HeaderMenu({busy, onRefresh, onStop, onSettings}: Pick<HeaderProps, 'busy' | 'onRefresh' | 'onStop' | 'onSettings'>) {
  const {styles} = useStyles(stylesheet);
  const [mounted, setMounted] = useState(false);
  const [visible, setVisible] = useState(false);
  const [position, setPosition] = useState<{top: number; left: number; width: number} | null>(null);
  const opacity = React.useRef(new Animated.Value(0)).current;
  const scale = React.useRef(new Animated.Value(0.86)).current;
  const triggerRef = React.useRef<View>(null);
  const overlayRef = React.useRef<View>(null);
  const menuHeight = busy ? 160 : 112;

  const measureMenu = () => {
    overlayRef.current?.measureInWindow((overlayX, overlayY, overlayWidth, overlayHeight) => {
      triggerRef.current?.measureInWindow((x, y, width, height) => {
        const panelWidth = Math.min(260, overlayWidth - 24);
        const below = y + height - overlayY + 8;
        const top = below + menuHeight <= overlayHeight - 12 ? below : Math.max(12, y - overlayY - menuHeight - 8);
        setPosition({width: panelWidth, left: Math.max(12, Math.min(x + width - overlayX - panelWidth, overlayWidth - panelWidth - 12)), top});
      });
    });
  };

  useEffect(() => {
    if (!visible || !position) return;
    const animation = Animated.parallel([
      Animated.timing(opacity, {toValue: 1, duration: 190, useNativeDriver: true}),
      Animated.spring(scale, {toValue: 1, damping: 19, stiffness: 240, mass: 0.7, useNativeDriver: true}),
    ]);
    animation.start();
    return () => animation.stop();
  }, [visible, position, opacity, scale]);

  useEffect(() => {
    if (!visible) return;
    const back = BackHandler.addEventListener('hardwareBackPress', () => { close(); return true; });
    return () => back.remove();
  }, [visible]);

  const open = () => {
    opacity.setValue(0);
    scale.setValue(0.86);
    setPosition(null);
    setMounted(true);
    setVisible(true);
    requestAnimationFrame(measureMenu);
  };
  const close = (action?: () => void) => {
    setVisible(false);
    Animated.parallel([
      Animated.timing(opacity, {toValue: 0, duration: 130, useNativeDriver: true}),
      Animated.timing(scale, {toValue: 0.86, duration: 130, useNativeDriver: true}),
    ]).start(({finished}) => {
      if (finished) {
        setMounted(false);
        setPosition(null);
        action?.();
      }
    });
  };

  return <>
    <View ref={triggerRef} collapsable={false}>
      <Pressable accessibilityRole="button" accessibilityLabel="Mais opções" accessibilityState={{expanded: visible}} onPress={open} android_ripple={{color: '#FFFFFF20', radius: 24, foreground: true}} style={styles.headerAction}>
        <View pointerEvents="none" style={styles.moreDots}><View style={styles.moreDot} /><View style={styles.moreDot} /><View style={styles.moreDot} /></View>
      </Pressable>
    </View>
    {mounted && <Portal>
      <View ref={overlayRef} collapsable={false} style={styles.headerMenuOverlay} onLayout={measureMenu}>
        <NativePressable accessibilityLabel="Fechar menu" style={StyleSheet.absoluteFill} onPress={() => close()} />
        <Animated.View pointerEvents={visible && position ? 'auto' : 'none'} style={[styles.headerMenuPanel, position, {opacity, transform: [{scale}]}]}>
          <Pressable accessibilityRole="button" style={styles.headerMenuItem} onPress={() => close(onRefresh)}>
            <Image source={icons.wifi} style={styles.headerMenuIcon} /><Text style={styles.headerMenuLabel}>Atualizar conexão</Text>
          </Pressable>
          {busy && <Pressable accessibilityRole="button" style={styles.headerMenuItem} onPress={() => close(onStop)}>
            <Image source={icons.square} style={styles.headerMenuIcon} /><Text style={styles.headerMenuLabel}>Parar resposta</Text>
          </Pressable>}
          <Pressable accessibilityRole="button" style={styles.headerMenuItem} onPress={() => close(onSettings)}>
            <Text style={styles.headerMenuGlyph}>⚙</Text><Text style={styles.headerMenuLabel}>Configurações</Text>
          </Pressable>
        </Animated.View>
      </View>
    </Portal>}
  </>;
}
