import {StyleSheet} from 'react-native';
import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  overlay: {...StyleSheet.absoluteFillObject, zIndex: 100, elevation: 20},
  dots: {width: 24, height: 24, alignItems: 'center', justifyContent: 'center', gap: 3},
  dot: {width: 3.5, height: 3.5, borderRadius: 2, backgroundColor: theme.colors.text},
  panel: {position: 'absolute', transformOrigin: 'top right', padding: theme.spacing.sm, backgroundColor: '#212124', borderRadius: 22, borderWidth: 1, borderColor: '#FFFFFF24', elevation: 12},
  item: {minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: theme.spacing.md, paddingHorizontal: theme.spacing.md, borderRadius: 14},
  icon: {width: 19, height: 19, tintColor: theme.colors.muted},
  glyph: {width: 19, color: theme.colors.muted, fontSize: 18, textAlign: 'center'},
  label: {color: theme.colors.text, fontSize: theme.typography.base, fontWeight: '500'},
  destructive: {tintColor: '#CF9999', color: '#CF9999'},
}));
