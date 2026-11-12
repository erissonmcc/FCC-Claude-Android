import React from 'react';
import {Animated, Image, StyleSheet, Text, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {ContextMenu, ContextMenuDots, type ContextMenuAction} from '../ContextMenu/ContextMenu';
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
  const actions: ContextMenuAction[] = [
    {label: 'Atualizar conexão', icon: icons.wifi, onPress: onRefresh},
    ...(busy ? [{label: 'Parar resposta', icon: icons.square, onPress: onStop}] : []),
    {label: 'Configurações', glyph: '⚙', onPress: onSettings},
  ];
  return <ContextMenu
    accessibilityLabel="Mais opções"
    actions={actions}
    renderTrigger={({onPress, expanded}) => <Pressable accessibilityRole="button" accessibilityLabel="Mais opções" accessibilityState={{expanded}} onPress={onPress} android_ripple={{color: '#FFFFFF20', radius: 24, foreground: true}} style={styles.headerAction}>
      <ContextMenuDots />
      </Pressable>}
  />;
}
