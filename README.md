# FCC Claude Android

Aplicativo Android nativo em Kotlin e Jetpack Compose, com módulos React Native usados para renderização Markdown e comportamento de streaming.

## Requisitos

- JDK 17
- Android SDK com Android 36 (compile SDK)
- Node.js e npm para os módulos React Native

## Executar

```sh
npm install
./gradlew assembleDebug
```

No Termux, se necessário, execute o wrapper com `sh ./gradlew assembleDebug --console=plain`.

Para iniciar o Metro durante o desenvolvimento React Native:

```sh
npm start
```

## Testes

```sh
./gradlew test
npm test
```
