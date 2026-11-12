import 'react-native-gesture-handler';
import {AppRegistry} from 'react-native';
import App from './src/App';
import SettingsApp from './src/screens/Settings/SettingsApp';

AppRegistry.registerComponent('FccClaude', () => App);
AppRegistry.registerComponent('FccSettings', () => SettingsApp);
