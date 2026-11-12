import {StyleSheet} from 'react-native';
import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  anchor: {marginRight: theme.spacing.xs},
  trigger: {width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center', overflow: 'hidden'},
  icon: {width: 20, height: 20, tintColor: theme.colors.muted},
  overlay: {...StyleSheet.absoluteFillObject, zIndex: 100, elevation: 20},
  panelFade: {position: 'absolute', height: 340, elevation: 12},
  panel: {width: '100%', height: '100%', overflow: 'hidden', backgroundColor: theme.colors.effortSurface, borderRadius: 26, borderWidth: 1, borderColor: '#FFFFFF24'},
  content: {flex: 1, paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.md, paddingBottom: theme.spacing.sm},
  heading: {flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: theme.spacing.xs},
  headingText: {flex: 1, color: theme.colors.muted, fontSize: theme.typography.base, lineHeight: 21, paddingRight: theme.spacing.sm},
  learnMore: {color: theme.colors.muted, fontSize: theme.typography.sm, textDecorationLine: 'underline'},
  options: {flex: 1},
  option: {minHeight: 72, flexDirection: 'row', alignItems: 'center', paddingVertical: theme.spacing.sm, paddingHorizontal: theme.spacing.sm, borderRadius: 16},
  optionIconWrap: {width: 36, alignItems: 'center', marginRight: theme.spacing.sm},
  optionIcon: {width: 24, height: 24, tintColor: theme.colors.muted},
  optionCopy: {flex: 1},
  optionTitle: {color: theme.colors.text, fontSize: theme.typography.base, lineHeight: 21},
  optionDescription: {color: theme.colors.muted, fontSize: theme.typography.sm, lineHeight: 19, marginTop: 2},
  warning: {color: '#E66B13'},
  warningIcon: {tintColor: '#E66B13'},
  check: {width: 19, height: 19, tintColor: theme.colors.muted, marginLeft: theme.spacing.sm},
}));
