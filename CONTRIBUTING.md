# Contributing

Thank you for helping make Hedgehog Garden kinder and more accessible.

## Before opening a pull request

- Keep the game offline and child-safe by default.
- Do not add advertising, analytics, accounts, dynamic code, or network SDKs.
- Keep English and Russian string resources in sync.
- Use only assets with a clearly documented open-source or public-domain license.
- Run `./scripts/verify_android.sh`.

## Translations

User-facing strings live in `app/src/main/res/values/strings.xml` and
`app/src/main/res/values-ru/strings.xml`. The current in-game selector is
deliberately limited to English and Russian. Keep both files complete and use
the bundled Nunito typeface for consistent Latin and Cyrillic typography.

## Visual changes

Runtime art is drawn by `HedgehogGardenView` at a 1280×720 logical resolution.
Keep controls at least 48 logical pixels across and never rely on color alone
to communicate whether an objective is complete.

## Commit hygiene

- Never commit a keystore, signing password, local SDK path, or Play credential.
- Keep generated build output out of Git.
- Add the exact license text for every new third-party asset.
