[English](./README.md) | 日本語

# kotlin-app-support

小さな Android のアプリの中のサポート。開発者が答えるのに要る文脈を添える問い合わせのフォームと、書く前に探せるヘルプの記事。問い合わせは選んだ送り先（transport）へ行く。サポートのアドレスへのメール（`MailSupportTransport`）か、問い合わせと返事もアプリの中に持ってくるサポートのサーバー。

[swift-app-support](https://github.com/appgrowstack/swift-app-support) の Kotlin の側。送り先・文脈・記事・検索・9つの言語の文言が同じ。

> 開発は非公開のモノリポでしていて、ここへは片方向にコピーしている。Issue は受ける。PR は受けない。

## できること

- **書く前に答える。** `SupportCenter.suggestions` が、入力の途中の文にもう答えている記事を探す（`HelpSearch` は日本語のような空白の無い言葉も探せる）
- **文脈を自動で添える。** アプリのバージョン・ビルド・OS・端末・配布元・Premium か・フォームを開いた画面・記録したセッション・直近の出来事（型のまま）・出したキャンペーン。アプリの中で利用者が書いた文は入れない
- **単体で動く。** `MailSupportTransport` は本文の下に文脈を読める行で付けたメールを開く。`BundledHelpArticles` はアプリに同梱した記事を読む
- **できることは transport が決める。** `SupportTransport` はできることを `capabilities` で返す。返事があれば自分の問い合わせの一覧を出し、`SupportState.unreadCount` をバッジに使える。画像があれば1件に4枚まで
- **9つの言語を入れてある。** `SupportStrings` に画面の文言を英語・日本語・韓国語・簡体字・繁体字・スペイン語・ブラジルのポルトガル語・ドイツ語・フランス語で持つ（swift-app-support と同じ文）。`SupportStrings.forLanguage` が選ぶ
- **Android に依存しない。** `support-core` は Kotlin/JVM（coroutines と kotlinx.serialization）。メールはアプリの `Intent` で開き、端末はアプリが `SupportDevice` で渡し、画面はアプリの Compose が `SupportCenter.state` から描く

使い方は[README.md](./README.md)のQuick Start。入れ方はJitPackの`com.github.appgrowstack:kotlin-app-support:0.3.0`。

## ライセンス

MIT（[LICENSE](LICENSE)）。
