# kotlin-app-support

In-app support for a small Android app: a contact form that attaches the context a developer needs
and help articles to search before writing. Requests go to a transport: a mail to you
(`MailSupportTransport`), or a support server that also brings the replies into the app.

The Kotlin side of [swift-app-support](https://github.com/appgrowstack/swift-app-support): the same
transport, context, articles, search and words in nine languages.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)
![JVM](https://img.shields.io/badge/JVM-17-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

> Developed in a private monorepo and copied here one way. Issues are welcome; pull requests are
> not accepted.

## Features

- **Answers before the request.** `SupportCenter.suggestions` finds help articles that may already
  answer what the person is typing (`HelpSearch` works for Japanese and other languages without
  spaces)
- **Context attached.** App version, build, OS, device model, channel, subscription state, the
  screen the form came from, the recorded session, recent events (typed) and in-app campaigns
  shown. Nothing the person typed elsewhere in the app
- **Works on its own.** `MailSupportTransport` opens a mail to you with the context as readable
  lines below the text; `BundledHelpArticles` reads the articles shipped in the app
- **A transport decides the rest.** `SupportTransport` says what it can do (`capabilities`): with
  replies, the app lists the person's requests and `SupportState.unreadCount` gives the badge; with
  images, the form takes up to four screenshots
- **Nine languages built in.** `SupportStrings` has the screens' words in English, Japanese, Korean,
  Simplified and Traditional Chinese, Spanish, Brazilian Portuguese, German and French, the same as
  swift-app-support. `SupportStrings.forLanguage` picks one
- **No Android dependency.** `support-core` is Kotlin/JVM (coroutines and kotlinx.serialization).
  The app opens the mail with its own `Intent`, passes the device (`SupportDevice`) and draws the
  screens in Compose from `SupportCenter.state`. Those screens are the app's, so the app puts its
  screen recorder's mark on the fields and messages (for kotlin-app-sessions, `Modifier.sessionMask()`)

## Quick Start

```kotlin
val support = SupportCenter(
    transport = MailSupportTransport("support@example.com") { uri ->
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri))) }.isSuccess
    },
    articles = BundledHelpArticles.fromJson(assets.open("help.json").bufferedReader().use { it.readText() }),
    lang = "ja",
    context = { screen ->
        SupportContext.current(
            appVersion = BuildConfig.VERSION_NAME, build = BuildConfig.VERSION_CODE.toString(),
            device = SupportDevice(Build.VERSION.RELEASE, Build.MODEL),
            channel = SupportContext.Channel.PLAY, isPremium = premium.isPremium, screen = screen,
        )
    },
)
val strings = SupportStrings.forLanguage(support.lang)

// in a composable
val state by support.state.collectAsState()
```

## Installation

JitPack: `com.github.appgrowstack:kotlin-app-support:0.5.0`.

## Requirements

- Kotlin 2.4, JVM 17

## License

MIT — see [LICENSE](LICENSE).
