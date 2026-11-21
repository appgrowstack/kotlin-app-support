# kotlin-app-support

小さなAndroidのアプリの中のサポート。開発者が答えるのに要る文脈を添える問い合わせのフォームと、書く前に探せるヘルプの記事。問い合わせは送り先（transport）へ行く。自分へのメール（`MailSupportTransport`）か、返事もアプリの中に持ってくるサポートのサーバー。

[English](./README.md)

[swift-app-support](https://github.com/appgrowstack/swift-app-support)のKotlinの側。送り先・文脈・記事・検索・9つの言語の文言が同じ。

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)
![JVM](https://img.shields.io/badge/JVM-17-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

> 開発は非公開のモノリポでしていて、ここへは片方向にコピーしている。Issueは受ける。PRは受けない。

## できること

- **書く前に答える。** `SupportCenter.suggestions` が、入力の途中の文にもう答えているかもしれない記事を探す（`HelpSearch` は日本語のような空白の無い言葉も探せる）
- **文脈を添える。** アプリのバージョン・ビルド・OS・端末の機種・配布元・購読の状態・フォームを開いた画面・記録したセッション・直近の出来事（型のまま）・出したキャンペーン。アプリのほかの所で利用者が書いた文は入れない
- **単体で動く。** `MailSupportTransport` は本文の下に文脈を読める行で付けたメールを開く。`BundledHelpArticles` はアプリに同梱した記事を読む
- **残りはtransportが決める。** `SupportTransport` はできることを `capabilities` で返す。返事があれば、アプリは自分の問い合わせの一覧を出し、`SupportState.unreadCount` をバッジに使える。画像があれば、フォームはスクリーンショットを4枚まで受ける
- **9つの言語を入れてある。** `SupportStrings` に画面の文言を英語・日本語・韓国語・簡体字・繁体字・スペイン語・ブラジルのポルトガル語・ドイツ語・フランス語で持つ（swift-app-supportと同じ）。`SupportStrings.forLanguage` が1つを選ぶ
- **Androidに依存しない。** `support-core` はKotlin/JVM（coroutinesとkotlinx.serialization）。メールはアプリの `Intent` で開き、端末はアプリが `SupportDevice` で渡し、画面はアプリのComposeが `SupportCenter.state` から表示する

## はじめに

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

## 入れ方

JitPack: `com.github.appgrowstack:kotlin-app-support:0.3.0`。

## 動く環境

- Kotlin 2.4、JVM 17

## ライセンス

MIT（[LICENSE](LICENSE)）。
