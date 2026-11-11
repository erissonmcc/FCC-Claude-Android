import React from 'react';
import {ScrollView, Text, TextInput, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useStyles} from 'react-native-unistyles';
import {colors} from '../../theme';
import {Pressable} from '../../components/Pressable/Pressable';
import {stylesheet} from './styles';

type Props = {
  cwd: string;
  model: string;
  mode: string;
  effort: string;
  message?: string;
  onClose: () => void;
  onChooseFolder: () => void;
  onChangeCwd: (value: string) => void;
  onChangeModel: (value: string) => void;
  onChangeMode: (value: string) => void;
  onChangeEffort: (value: string) => void;
  onSave: () => void;
};

export function SettingsScreen({cwd, model, mode, effort, message, onClose, onChooseFolder, onChangeCwd, onChangeModel, onChangeMode, onChangeEffort, onSave}: Props) {
  const {styles} = useStyles(stylesheet);
  return <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="Voltar" onPress={onClose} style={styles.back}><Text style={styles.backText}>‹</Text></Pressable>
        <Text style={styles.title}>Configurações</Text>
      </View>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.section}>Projeto</Text>
        <View style={styles.card}>
        <Text style={styles.fieldLabel}>Pasta do projeto</Text>
        <TextInput value={cwd} onChangeText={onChangeCwd} style={styles.textField} placeholderTextColor={colors.muted} />
        <SettingAction label="Escolher pasta" tint={colors.accent} onPress={onChooseFolder} />
        </View>
        <Text style={styles.section}>Assistente</Text>
        <View style={styles.card}>
        <Text style={styles.fieldLabel}>Modelo</Text>
        <TextInput value={model} onChangeText={onChangeModel} style={styles.textField} placeholder="Modelo padrão" placeholderTextColor={colors.muted} />
        <Text style={styles.fieldLabel}>Permissões</Text>
        <View style={styles.choiceRow}>{[['config', 'Padrão'], ['ask', 'Perguntar'], ['full_access', 'Acesso total']].map(([value, label]) => <SettingAction key={value} label={label} tint={mode === value ? colors.accent : colors.muted} onPress={() => onChangeMode(value)} />)}</View>
        <Text style={styles.fieldLabel}>Esforço de raciocínio</Text>
        <View style={styles.choiceRow}>{['', 'low', 'medium', 'high', 'max'].map(value => <SettingAction key={value || 'default'} label={value || 'Padrão'} tint={effort === value ? colors.accent : colors.muted} onPress={() => onChangeEffort(value)} />)}</View>
        </View>
        <View style={styles.actions}>
          <SettingAction label="Cancelar" tint={colors.muted} onPress={onClose} />
          <SettingAction label="Salvar" tint={colors.accent} onPress={onSave} />
        </View>
        {!!message && <Text style={styles.muted}>{message}</Text>}
      </ScrollView>
  </SafeAreaView>;
}

function SettingAction({label, tint, onPress}: {label: string; tint: string; onPress: () => void}) {
  const {styles} = useStyles(stylesheet);
  return <Pressable accessibilityRole="button" onPress={onPress} style={styles.action}>
    <Text style={[styles.actionText, {color: tint}]}>{label}</Text>
  </Pressable>;
}
