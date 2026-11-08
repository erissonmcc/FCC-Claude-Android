import React from 'react';
import {Pressable as NativePressable, StyleSheet, type PressableProps} from 'react-native';

export function Pressable({style, android_ripple, ...props}: PressableProps) {
  return <NativePressable
    {...props}
    android_ripple={android_ripple === undefined ? {color: '#FFFFFF20', foreground: true} : android_ripple}
    style={state => [styles.clip, typeof style === 'function' ? style(state) : style]}
  />;
}

const styles = StyleSheet.create({clip: {overflow: 'hidden'}});
