import React from 'react';
import {Text} from 'react-native';
import {useStyles} from 'react-native-unistyles';
import {Pressable} from '../Pressable/Pressable';
import {errorPreview} from './errorPresentation';
import {stylesheet} from './styles';

export function ErrorBanner({message, top, onCopy}: {message: string; top: number; onCopy: (message: string) => void}) {
  const {styles: s} = useStyles(stylesheet);
  const preview = errorPreview(message);
  return <Pressable
    accessibilityRole="button"
    accessibilityLabel={`Copiar erro: ${preview}`}
    onPress={() => onCopy(message)}
    style={[s.banner, {top}]}
  >
    <Text numberOfLines={4} ellipsizeMode="tail" style={s.message}>{preview}</Text>
    <Text style={s.hint}>Toque para copiar os detalhes</Text>
  </Pressable>;
}
