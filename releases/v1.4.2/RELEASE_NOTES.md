# DataEater 1.4.2

Delete conversations using the trash button beside each session in the side panel.

- Confirm before deleting a conversation.
- Deleting the current conversation opens another; deleting the last creates a blank conversation.
- Deletion waits until the app is idle.
- Storage failures keep the conversation visible and allow retry.

The Android database builder, local-model input-overflow recovery, optional OpenRouter AI and reply settings remain available.

## Verification and downloads

Archived checks report 285 unit tests passing and two phone UI tests on vivo V2453A / Android 16. Tests used invented conversations and covered cancellation, background/current/last-session deletion, persistence and storage failures. Release lint recorded no errors and 50 warnings. These checks describe the archived 8 October APK; later local development changes are not included.

Install `DataEater-1.4.2.apk`. The existing release signing certificate is retained. `SHA256SUMS` verifies the APK and accompanying `DataEater-1.4.2-notices.zip`. Update over the existing app to retain chats and keys; uninstalling removes private app data.
