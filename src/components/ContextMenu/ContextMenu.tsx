import React, {useEffect, useRef, useState} from 'react';
import {Animated, BackHandler, Image, Pressable as NativePressable, StyleSheet, Text, View, type ImageSourcePropType} from 'react-native';
import {Portal} from '@gorhom/portal';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {stylesheet} from './styles';

export type ContextMenuAction = {
  label: string;
  onPress: () => void;
  icon?: ImageSourcePropType;
  glyph?: string;
  destructive?: boolean;
};

export function ContextMenuDots() {
  const {styles: s} = useStyles(stylesheet);
  return <View pointerEvents="none" style={s.dots}>
    <View style={s.dot} /><View style={s.dot} /><View style={s.dot} />
  </View>;
}

export function ContextMenu({
  accessibilityLabel,
  actions,
  renderTrigger,
  panelWidth = 260,
}: {
  accessibilityLabel: string;
  actions: ContextMenuAction[];
  renderTrigger: (controls: {onPress: () => void; expanded: boolean}) => React.ReactNode;
  panelWidth?: number;
}) {
  const {styles: s} = useStyles(stylesheet);
  const [mounted, setMounted] = useState(false);
  const [visible, setVisible] = useState(false);
  const [position, setPosition] = useState<{top: number; left: number; width: number} | null>(null);
  const opacity = useRef(new Animated.Value(0)).current;
  const scale = useRef(new Animated.Value(0.86)).current;
  const triggerRef = useRef<View>(null);
  const overlayRef = useRef<View>(null);
  const menuHeight = actions.length * 48 + 16;

  const measureMenu = () => {
    overlayRef.current?.measureInWindow((overlayX, overlayY, overlayWidth, overlayHeight) => {
      triggerRef.current?.measureInWindow((x, y, width, height) => {
        const widthLimit = Math.min(panelWidth, overlayWidth - 24);
        const below = y + height - overlayY + 8;
        const top = below + menuHeight <= overlayHeight - 12 ? below : Math.max(12, y - overlayY - menuHeight - 8);
        setPosition({
          width: widthLimit,
          left: Math.max(12, Math.min(x + width - overlayX - widthLimit, overlayWidth - widthLimit - 12)),
          top,
        });
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
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      close();
      return true;
    });
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
      if (!finished) return;
      setMounted(false);
      setPosition(null);
      action?.();
    });
  };

  return <>
    <View ref={triggerRef} collapsable={false}>
      {renderTrigger({onPress: open, expanded: visible})}
    </View>
    {mounted && <Portal>
      <View ref={overlayRef} collapsable={false} style={s.overlay} onLayout={measureMenu}>
        <NativePressable accessibilityLabel={`Fechar ${accessibilityLabel}`} style={StyleSheet.absoluteFill} onPress={() => close()} />
        <Animated.View pointerEvents={visible && position ? 'auto' : 'none'} style={[s.panel, position, {opacity, transform: [{scale}]}]}>
          {actions.map(action => <Pressable
            key={action.label}
            accessibilityRole="button"
            style={s.item}
            onPress={() => close(action.onPress)}
          >
            {action.icon && <Image source={action.icon} style={[s.icon, action.destructive && s.destructive]} />}
            {action.glyph && <Text style={[s.glyph, action.destructive && s.destructive]}>{action.glyph}</Text>}
            <Text style={[s.label, action.destructive && s.destructive]}>{action.label}</Text>
          </Pressable>)}
        </Animated.View>
      </View>
    </Portal>}
  </>;
}
