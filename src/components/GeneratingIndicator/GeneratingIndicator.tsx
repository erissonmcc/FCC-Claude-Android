import React, {useEffect, useMemo, useRef, useState} from 'react';
import {Animated, Easing, Text, View} from 'react-native';
import {useStyles} from 'react-native-unistyles';
import type {Run} from '../ToolActivity/ToolActivity';
import {stylesheet} from './styles';

function elapsedLabel(createdAt: number, now: number) {
  const seconds = Math.max(0, Math.floor((now - createdAt) / 1000));
  return seconds >= 60 ? `${Math.floor(seconds / 60)}m ${seconds % 60}s` : `${seconds}s`;
}

function tokenLabel(tokens: number) {
  return tokens >= 1000 ? `${(tokens / 1000).toFixed(1)}k tokens` : `${tokens} tokens`;
}

export function GeneratingIndicator({run}: {run?: Run}) {
  const {styles: s} = useStyles(stylesheet);
  const [now, setNow] = useState(Date.now());
  const dotOpacities = useMemo(() => [new Animated.Value(0.35), new Animated.Value(0.35), new Animated.Value(0.35)], []);
  const textOpacity = useRef(new Animated.Value(0.7)).current;

  useEffect(() => {
    if (!run?.createdAt) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [run?.createdAt]);

  useEffect(() => {
    const dots = dotOpacities.map((opacity, index) => Animated.loop(Animated.sequence([
      Animated.delay(index * 160),
      Animated.timing(opacity, {toValue: 1, duration: 400, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
      Animated.timing(opacity, {toValue: 0.35, duration: 400, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
    ])));
    const shimmer = Animated.loop(Animated.sequence([
      Animated.timing(textOpacity, {toValue: 1, duration: 800, easing: Easing.linear, useNativeDriver: true}),
      Animated.timing(textOpacity, {toValue: 0.7, duration: 800, easing: Easing.linear, useNativeDriver: true}),
    ]));
    dots.forEach(animation => animation.start());
    shimmer.start();
    return () => {
      dots.forEach(animation => animation.stop());
      shimmer.stop();
    };
  }, [dotOpacities, textOpacity]);

  const metrics = [
    run?.createdAt ? elapsedLabel(run.createdAt, now) : null,
    run?.consumedTokens != null ? tokenLabel(run.consumedTokens) : null,
  ].filter(Boolean).join(' • ');

  return <View style={s.row}>
    <View style={s.dots} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {dotOpacities.map((opacity, index) => <Animated.View key={index} style={[s.dot, {opacity}]} />)}
    </View>
    <Animated.Text style={[s.label, {opacity: textOpacity}]}>Claude está trabalhando…</Animated.Text>
    {!!metrics && <Text style={s.metrics}>({metrics})</Text>}
  </View>;
}
