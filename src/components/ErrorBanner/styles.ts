import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  banner: {position: 'absolute', left: 16, right: 16, zIndex: 30, elevation: 30, maxHeight: 160, paddingHorizontal: 14, paddingVertical: theme.spacing.sm, borderRadius: 12, backgroundColor: '#482F2B'},
  message: {color: theme.colors.danger, fontSize: theme.typography.sm, lineHeight: 19},
  hint: {color: '#E3CBC4', fontSize: 12, lineHeight: 16, marginTop: 6},
}));
