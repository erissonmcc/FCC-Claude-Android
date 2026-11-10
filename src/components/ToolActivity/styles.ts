import {createStyleSheet} from 'react-native-unistyles';

export const stylesheet = createStyleSheet(theme => ({
  work:{paddingHorizontal:0}, workHeader:{minHeight:48,flexDirection:'row',alignItems:'center',gap:4,borderBottomWidth:1,borderBottomColor:'#999B9F73'},
  groupHeader:{minHeight:40,flexDirection:'row',alignItems:'center',gap:8},toolHeader:{minHeight:40,flexDirection:'row',alignItems:'center',gap:10,paddingHorizontal:0,paddingVertical:4},
  icon:{width:16,height:16,tintColor:theme.colors.muted},summary:{flex:1,color:theme.colors.muted,fontSize:13,lineHeight:19},label:{color:theme.colors.muted,fontSize:12},value:{color:theme.colors.text,fontSize:13,lineHeight:19},
  details:{paddingHorizontal:0,paddingBottom:12,gap:8},field:{gap:5},nested:{paddingLeft:10,gap:6},codeBox:{backgroundColor:theme.colors.surface,borderRadius:10},code:{color:theme.colors.text,fontSize:12,lineHeight:18,fontFamily:'monospace',padding:10},expand:{paddingVertical:12},
}));
