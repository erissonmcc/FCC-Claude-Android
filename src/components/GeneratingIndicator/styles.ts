import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  row: {flexDirection: 'row', alignItems: 'center', gap: 10, marginLeft: theme.spacing.xs, marginVertical: 14},
  dots: {width: 28, height: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  dot: {width: 5, height: 5, borderRadius: 3, backgroundColor: theme.colors.muted},
  label: {color: theme.colors.text, fontSize: theme.typography.base},
  metrics: {color: theme.colors.muted, fontSize: theme.typography.base},
}));
