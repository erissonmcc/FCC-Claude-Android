import {UnistylesRegistry, UnistylesRuntime} from 'react-native-unistyles';
import {darkTheme} from './themes';

type AppThemes = {dark: typeof darkTheme};

declare module 'react-native-unistyles' {
  export interface UnistylesThemes extends AppThemes {}
}

UnistylesRegistry.addThemes({dark: darkTheme});
UnistylesRuntime.setTheme('dark');

export {colors} from './colors';
export {spacing} from './spacing';
export {typography} from './typography';
