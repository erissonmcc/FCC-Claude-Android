import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  modalRoot: {flex: 1, flexDirection: 'row'},
  scrim: {flex: 1, backgroundColor: '#0009'},
  panel: {width: '92%', maxWidth: 420, maxHeight: '90%', alignSelf: 'center', backgroundColor: theme.colors.surface, borderRadius: 18},
  content: {padding: theme.spacing.lg + 2, gap: theme.spacing.sm},
  title: {color: theme.colors.text, fontSize: theme.typography.title - 3, fontWeight: '700', marginBottom: theme.spacing.xs},
  fieldLabel: {color: theme.colors.muted, fontSize: theme.typography.xs, marginTop: 7},
  textField: {minHeight: 44, borderColor: theme.colors.line, borderWidth: 1, borderRadius: 10, paddingHorizontal: theme.spacing.md, color: theme.colors.text, backgroundColor: theme.colors.raised},
  choiceRow: {flexDirection: 'row', flexWrap: 'wrap', backgroundColor: theme.colors.backdrop, borderRadius: theme.spacing.md},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', marginTop: theme.spacing.sm},
  action: {minHeight: 42, minWidth: 40, paddingHorizontal: 11, alignItems: 'center', justifyContent: 'center', borderRadius: 16},
  actionText: {fontSize: theme.typography.md, fontWeight: '600'},
  muted: {color: theme.colors.muted, fontSize: theme.typography.xs, lineHeight: 17},
}));
