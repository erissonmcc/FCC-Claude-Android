import './theme';
import React, {useCallback, useState} from 'react';
import {SafeAreaProvider, initialWindowMetrics} from 'react-native-safe-area-context';
import {PortalProvider} from '@gorhom/portal';
import {KeyboardProvider} from 'react-native-keyboard-controller';
import ChatScreen from './screens/Chat/ChatScreen';
import {SettingsScreen} from './screens/Settings/SettingsScreen';

export default function App() {
  const [showSettings, setShowSettings] = useState(false);
  const [cwd, setCwd] = useState('/storage/emulated/0');
  const [model, setModel] = useState('');
  const [mode, setMode] = useState('config');
  const [effort, setEffort] = useState('');
  const [backendUrl, setBackendUrl] = useState('https://fcc-backend-v2-sdui.onrender.com');
  const [backendToken, setBackendToken] = useState('');

  const onConfigChange = useCallback((next: {
    cwd: string; model: string; mode: string; effort: string;
    backendUrl: string; backendToken: string;
  }) => {
    setCwd(next.cwd);
    setModel(next.model);
    setMode(next.mode);
    setEffort(next.effort);
    setBackendUrl(next.backendUrl);
    setBackendToken(next.backendToken);
  }, []);

  const onSaveSettings = useCallback(async () => {
    setShowSettings(false);
  }, []);

  return <SafeAreaProvider initialMetrics={initialWindowMetrics} style={{flex: 1, backgroundColor: '#000000'}}>
    <KeyboardProvider>
      <PortalProvider>
        {showSettings ? (
          <SettingsScreen
            cwd={cwd}
            model={model}
            mode={mode}
            effort={effort}
            backendUrl={backendUrl}
            backendToken={backendToken}
            onClose={() => setShowSettings(false)}
            onChooseFolder={async () => { }}
            onChangeCwd={setCwd}
            onChangeModel={setModel}
            onChangeMode={setMode}
            onChangeEffort={setEffort}
            onChangeBackendUrl={setBackendUrl}
            onChangeBackendToken={setBackendToken}
            onSave={onSaveSettings}
          />
        ) : (
          <ChatScreen
            cwd={cwd}
            model={model}
            mode={mode}
            effort={effort}
            backendUrl={backendUrl}
            backendToken={backendToken}
            onSettings={() => setShowSettings(true)}
            onConfigChange={onConfigChange}
          />
        )}
      </PortalProvider>
    </KeyboardProvider>
  </SafeAreaProvider>;
};
