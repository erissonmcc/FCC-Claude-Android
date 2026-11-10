import React from 'react';
import {Pressable as NativePressable, type PressableProps} from 'react-native';
import {useStyles} from 'react-native-unistyles';
import {stylesheet} from './styles';

export function Pressable({style, android_ripple, ...props}: PressableProps) {
  const {styles} = useStyles(stylesheet);
  return <NativePressable
    {...props}
    android_ripple={android_ripple === undefined ? {color: '#FFFFFF20', foreground: true} : android_ripple}
    style={state => [styles.clip, typeof style === 'function' ? style(state) : style]}
  />;
}
