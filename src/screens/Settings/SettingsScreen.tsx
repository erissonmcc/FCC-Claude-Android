import React from 'react';
import {Modal, ScrollView, Text, TextInput, View} from 'react-native';
import {useStyles} from 'react-native-unistyles';
import {colors} from '../../theme';
import {Pressable} from '../../components/Pressable/Pressable';
import {stylesheet} from './styles';

type Props = {
  visible: boolean;
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

export function SettingsScreen({visible, cwd, model, mode, effort, message, onClose, onChooseFolder, onChangeCwd, onChangeModel, onChangeMode, onChangeEffort, onSave}: Props) {
  const {styles} = useStyles(stylesheet);
  return <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
    <View style={styles.modalRoot}>
      <Pressable android_ripple={null} style={styles.scrim} onPress={onClose} />
      <ScrollView style={styles.panel} contentContainerStyle={styles.content}>
        <Text style={styles.title}>Configurações</Text>
        <Text style={styles.fieldLabel}>Pasta do projeto</Text>
        <TextInput value={cwd} onChangeText={onChangeCwd} style={styles.textField} placeholderTextColor={colors.muted} />
        <SettingAction label="Escolher pasta" tint={colors.accent} onPress={onChooseFolder} />
        <Text style={styles.fieldLabel}>Modelo</Text>
        <TextInput value={model} onChangeText={onChangeModel} style={styles.textField} placeholder="Modelo padrão" placeholderTextColor={colors.muted} />
        <Text style={styles.fieldLabel}>Permissões</Text>
        <View style={styles.choiceRow}>{[['config', 'Padrão'], ['ask', 'Perguntar'], ['full_access', 'Acesso total']].map(([value, label]) => <SettingAction key={value} label={label} tint={mode === value ? colors.accent : colors.muted} onPress={() => onChangeMode(value)} />)}</View>
        <Text style={styles.fieldLabel}>Esforço de raciocínio</Text>
        <View style={styles.choiceRow}>{['', 'low', 'medium', 'high', 'max'].map(value => <SettingAction key={value || 'default'} label={value || 'Padrão'} tint={effort === value ? colors.accent : colors.muted} onPress={() => onChangeEffort(value)} />)}</View>
        <View style={styles.actions}>
          <SettingAction label="Cancelar" tint={colors.muted} onPress={onClose} />
          <SettingAction label="Salvar" tint={colors.accent} onPress={onSave} />
        </View>
        {!!message && <Text style={styles.muted}>{message}</Text>}
      </ScrollView>
    </View>
  </Modal>;
}

function SettingAction({label, tint, onPress}: {label: string; tint: string; onPress: () => void}) {
  const {styles} = useStyles(stylesheet);
  return <Pressable accessibilityRole="button" onPress={onPress} style={styles.action}>
    <Text style={[styles.actionText, {color: tint}]}>{label}</Text>
  </Pressable>;
}
