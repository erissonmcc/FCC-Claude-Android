import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  screen: {flex: 1, backgroundColor: theme.colors.backdrop},
  header: {flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 12, gap: 18},
  back: {width: 44, height: 44, borderRadius: 22, backgroundColor: theme.colors.raised, alignItems: 'center', justifyContent: 'center'},
  backText: {color: theme.colors.text, fontSize: 34, lineHeight: 40},
  content: {paddingHorizontal: 20, paddingBottom: 36, gap: theme.spacing.sm},
  title: {color: theme.colors.text, fontSize: 23, fontWeight: '700'},
  section: {color: theme.colors.muted, fontSize: 15, fontWeight: '600', marginTop: 20, marginLeft: 6},
  card: {backgroundColor: theme.colors.surface, borderColor: theme.colors.line, borderWidth: 1, borderRadius: 22, padding: 16, gap: 8},
  fieldLabel: {color: theme.colors.muted, fontSize: theme.typography.xs, marginTop: 7},
  textField: {minHeight: 44, borderColor: theme.colors.line, borderWidth: 1, borderRadius: 10, paddingHorizontal: theme.spacing.md, color: theme.colors.text, backgroundColor: theme.colors.raised},
  choiceRow: {flexDirection: 'row', flexWrap: 'wrap', backgroundColor: theme.colors.backdrop, borderRadius: theme.spacing.md},
  actions: {flexDirection: 'row', justifyContent: 'flex-end', marginTop: theme.spacing.sm},
  action: {minHeight: 42, minWidth: 40, paddingHorizontal: 11, alignItems: 'center', justifyContent: 'center', borderRadius: 16},
  actionText: {fontSize: theme.typography.md, fontWeight: '600'},
  muted: {color: theme.colors.muted, fontSize: theme.typography.xs, lineHeight: 17},
}));
