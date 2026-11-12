import {StyleSheet} from 'react-native';
import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  topBarOverlay: {position: 'absolute', top: 0, left: 0, right: 0, zIndex: 20},
  topBarBackdrop: {...StyleSheet.absoluteFillObject, bottom: -90},
  topBar: {minHeight: 72, paddingHorizontal: theme.spacing.md, paddingVertical: theme.spacing.sm, flexDirection: 'row', alignItems: 'center'},
  drawerButton: {width: 48, height: 48, minWidth: 48, minHeight: 48, paddingHorizontal: 0, borderRadius: 24, borderWidth: 1, borderColor: theme.colors.line, backgroundColor: theme.colors.raised, alignItems: 'center', justifyContent: 'center'},
  drawerIcon: {width: 22, height: 22, tintColor: theme.colors.muted},
  headerCenter: {position: 'relative', flex: 1, minWidth: 0, paddingHorizontal: theme.spacing.md},
  headerTitle: {color: theme.colors.text, fontSize: 16, fontWeight: '600'},
  headerPath: {color: theme.colors.muted, fontSize: 11, marginTop: 2},
  headerActions: {flexDirection: 'row', alignItems: 'center', backgroundColor: theme.colors.raised, borderWidth: 1, borderColor: theme.colors.line, borderRadius: 26, overflow: 'hidden'},
  headerAction: {width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center'},
  headerActionIcon: {width: 22, height: 22, tintColor: theme.colors.text},
}));
