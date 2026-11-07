import React from 'react';
import {AppRegistry, Linking} from 'react-native';
import {EnrichedMarkdownText} from 'react-native-enriched-markdown';
const {markdownProps} = require('./markdown-props');
const {useStreamingReveal} = require('./use-streaming-reveal');

function FccMarkdown({markdown = '', streaming = false, textColor, accent, codeBackground}) {
  const revealed = useStreamingReveal(markdown, streaming);
  const onLinkPress = ({url}) => {
    if (/^https?:\/\//i.test(url)) Linking.openURL(url).catch(() => {});
  };
  return <EnrichedMarkdownText
    {...markdownProps(revealed.text, revealed.streaming, textColor, accent, codeBackground)}
    flavor="github"
    selectable
    onLinkPress={onLinkPress}
    streamingConfig={{tableMode: 'progressive', codeBlockMode: 'progressive'}}
    markdownStyle={{
      paragraph: {color: textColor, fontSize: 15, lineHeight: 23},
      link: {color: accent},
      codeBlock: {backgroundColor: codeBackground, color: textColor},
      code: {backgroundColor: codeBackground, color: textColor},
    }}
  />;
}

AppRegistry.registerComponent('FccMarkdown', () => FccMarkdown);
