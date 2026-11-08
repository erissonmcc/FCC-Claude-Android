const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
const {FileStore} = require('metro-cache');
const {bundleModeMetroConfig} = require('react-native-worklets/bundleMode');
const path = require('path');

let config = getDefaultConfig(__dirname);

config.watchFolders.push(
  path.resolve(__dirname, 'node_modules/react-native-worklets/.worklets'),
);

const defaultResolver = config.resolver.resolveRequest;
config = mergeConfig(config, bundleModeMetroConfig);

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName.startsWith('react-native-worklets/.worklets/')) {
    return bundleModeMetroConfig.resolver.resolveRequest(
      context,
      moduleName,
      platform,
    );
  }
  if (defaultResolver) {
    return defaultResolver(context, moduleName, platform);
  }
  return context.resolveRequest(context, moduleName, platform);
};

config.maxWorkers = process.env.PREFIX ? 1 : config.maxWorkers;
config.cacheStores = [new FileStore({root: path.join(__dirname, '.metro-cache')})];
config.cacheVersion = 'fcc-chat-v1';

module.exports = config;
