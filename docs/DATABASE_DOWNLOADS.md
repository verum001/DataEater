# Database downloads

In Settings, expand Database Selection and tap Download databases. This opens a scrollable catalogue from catalog.json in verum001/DataEater-Databases. Maintainers update it alongside published releases; it avoids the GitHub API rate limit. It offers the newest non-draft, non-prerelease asset for each database filename, ordered by publication date. Refresh checks for newly published catalogue versions. A previously verified catalogue is retained if a refresh fails. Only .dataeater assets with a SHA-256 digest and an allowed repository download address are accepted.

Android manages transfers in the background. The app retains the transfer identity across restarts. Before installing, it checks the downloaded size, SHA-256 digest, package format and contents. It writes into a temporary file and publishes a complete verified database in the DataEater folder. Existing files are preserved, and versioned filenames allow older database versions to remain available. Downloaded bytes do not authenticate a publisher independently of GitHub.

No chats, documents, device request codes or vault keys are transmitted when loading the catalogue or downloading a database. Public GitHub requests and downloads expose ordinary connection metadata. Source manuals retain their original terms.

If storage access is requested, grant it and return to DataEater; the selected download starts automatically. Download buttons wait while the catalogue refreshes.

Descriptions are collapsed initially. Show description opens the full text for that database; Hide description collapses it. Version, size and transfer controls remain visible.
