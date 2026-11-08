# Enriched Markdown lifecycle fix

`react-native-enriched-markdown+0.4.0.patch` backports the attached-view guard
from the official `react-native-enriched-markdown@1.1.1` implementation.

On this Android device, setting selectable text before its native view is
attached crashes in `TextView.checkForResize()` because layout parameters are
null. Keep the latest rendered text pending until `onAttachedToWindow()` and
guard asynchronous error updates in the same way as upstream.

The patch also initializes missing layout parameters before text and selection
updates. React Native 0.81.5 already uses this safeguard in its own
`ReactAndroid/src/main/java/com/facebook/react/views/text/ReactTextView.java`
(`EMPTY_LAYOUT_PARAMS` and `setText(ReactTextUpdate)`), while this SDK view
inherits directly from `AppCompatTextView`. This also covers the observed crash
in `setTextIsSelectable()` during Fabric property updates before mounting.

The upstream 1.1.x compatibility table requires React Native 0.83 or newer;
this application currently uses 0.81.5. The patch preserves the current SDK
renderer and text selection and is applied by `patch-package` after installation.

Source inspected: https://registry.npmjs.org/react-native-enriched-markdown/-/react-native-enriched-markdown-1.1.1.tgz

## Persistent Metro cache

`@react-native+gradle-plugin+0.81.5.patch` removes the unconditional
`--reset-cache` argument from Android bundle tasks. Metro stores transformed
modules in the project-local `.metro-cache/` directory configured in
`metro.config.js`. The directory is ignored by Git and survives terminal
sessions. Bundle tasks still rerun when their inputs change; Gradle can skip
them when inputs and outputs are unchanged.
