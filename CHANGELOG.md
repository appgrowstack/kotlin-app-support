# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.5.0] - 2026-11-23

### Changed

- No code change. The version follows swift-app-support 0.5.0 (`supportPrivateContent(_:)` is SwiftUI only).

## [0.4.0] - 2026-11-22

### Changed

- `SupportTransport.submit` and `SupportCenter.submit` return `SupportSubmission` (the request's id and
  the `unsentImages`). A transport throws only when the request was not made; images that fail after
  that come back instead, so sending the form again never makes a second request.

## [0.3.0] - 2026-11-22

### Added

- `support-core`: the Kotlin side of swift-app-support. `SupportSdk.NAME` and `SupportSdk.VERSION` for a connector to report.
- `SupportTransport` (submit, tickets, reply and `capabilities`), `HelpArticleSource` and
  `SupportError`.
- `MailSupportTransport`: opens a `mailto:` URI through the app with the context as readable lines.
- `SupportContext` (`current(appVersion, build, device, channel, …)`, `forSending()` with the
  contract's limits, `readableLines`), `SupportDevice`, and the request, ticket, message, article and
  typed value models in the contract's JSON shape.
- `BundledHelpArticles` (falls back to the base language, then English) and `HelpSearch` (words, and
  pairs of characters for CJK text).
- `SupportCenter`: the requests, articles, loading state, last error and unread count as a
  `StateFlow`, with `SupportSeenStore` for the read marks.
- `SupportStrings` in English, Japanese, Korean, Simplified and Traditional Chinese, Spanish,
  Brazilian Portuguese, German and French, with `forLanguage`.
