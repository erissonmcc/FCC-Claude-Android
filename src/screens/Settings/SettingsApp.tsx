import React, {useState} from 'react';
import {AppState, NativeModules, View} from 'react-native';
import {SafeAreaProvider, initialWindowMetrics} from 'react-native-safe-area-context';
import {PortalProvider} from '@gorhom/portal';
import {KeyboardProvider} from 'react-native-keyboard-controller';
import {SettingsScreen} from './SettingsScreen';

type SettingsValues = {cwd: string; model: string; mode: string; effort: string; backendUrl: string; backendToken: string; localWorkspace: string};
type Props = Partial<SettingsValues>;

const FccChat = NativeModules.FccChat as {
  applySettings(cwd: string, model: string, mode: string, effort: string): Promise<void>;
  configureBackend(url: string, token: string): Promise<void>;
  configureLocalWorkspace(path: string): Promise<void>;
  finishSettings(): void;
  openFileAccessSettings(): Promise<void>;
  hasFileAccess(): Promise<boolean>;
};

export default function SettingsApp(props: Props) {
  const [values, setValues] = useState<SettingsValues>({
    cwd: props.cwd ?? '/storage/emulated/0',
    model: props.model ?? '',
    mode: props.mode ?? 'config',
    effort: props.effort ?? '',
    backendUrl: props.backendUrl ?? 'https://fcc-backend-v2-sdui.onrender.com',
    backendToken: props.backendToken ?? '',
    localWorkspace: props.localWorkspace ?? '/storage/emulated/0',
  });
  const [fileAccess, setFileAccess] = useState(false);
  React.useEffect(() => {
    const refresh = () => { void FccChat.hasFileAccess().then(setFileAccess).catch(() => setFileAccess(false)); };
    refresh();
    const subscription = AppState.addEventListener('change', state => { if (state === 'active') refresh(); });
    return () => subscription.remove();
  }, []);
  const update = <K extends keyof SettingsValues>(key: K, value: SettingsValues[K]) => setValues(current => ({...current, [key]: value}));

  return <SafeAreaProvider initialMetrics={initialWindowMetrics} style={{flex: 1, backgroundColor: '#000000'}}>
    <KeyboardProvider>
      <PortalProvider>
        <View style={{flex: 1, backgroundColor: '#000000'}}>
          <SettingsScreen
            {...values}
            fileAccess={fileAccess}
            onOpenFileAccess={async () => { await FccChat.openFileAccessSettings(); setFileAccess(await FccChat.hasFileAccess()); }}
            onClose={() => FccChat.finishSettings()}
            onChooseFolder={async () => {}}
            onChangeCwd={value => update('cwd', value)}
            onChangeModel={value => update('model', value)}
            onChangeMode={value => update('mode', value)}
            onChangeEffort={value => update('effort', value)}
            onChangeBackendUrl={value => update('backendUrl', value)}
            onChangeBackendToken={value => update('backendToken', value)}
            onChangeLocalWorkspace={value => update('localWorkspace', value)}
            onSave={async () => {
              await FccChat.configureBackend(values.backendUrl, values.backendToken);
              await FccChat.configureLocalWorkspace(values.localWorkspace);
              await FccChat.applySettings(values.cwd, values.model, values.mode, values.effort);
              FccChat.finishSettings();
            }}
          />
        </View>
      </PortalProvider>
    </KeyboardProvider>
  </SafeAreaProvider>;
}
