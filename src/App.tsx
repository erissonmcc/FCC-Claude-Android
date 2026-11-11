import './theme';
import React from 'react';
import {SafeAreaProvider, initialWindowMetrics} from 'react-native-safe-area-context';
import {PortalProvider} from '@gorhom/portal';
import {KeyboardProvider} from 'react-native-keyboard-controller';
import ChatScreen from './screens/Chat/ChatScreen';

export default function App() {
  return <SafeAreaProvider initialMetrics={initialWindowMetrics} style={{flex: 1, backgroundColor: '#000000'}}>
    <KeyboardProvider>
      <PortalProvider>
        <ChatScreen />
      </PortalProvider>
    </KeyboardProvider>
  </SafeAreaProvider>;
}
