# Yuku agent notes

## Features

Yuku is a minimal Android WebView browser written with Jetpack Compose. It provides tabbed and private browsing, an omnibox, history, bookmarks, downloads, reader mode, page dark mode, desktop mode, find in page, text-size controls, ad and cosmetic blocking, site permissions and settings, password and form autofill, and an overlay for links opened by other apps.

The bottom toolbar provides immediate access to the tab switcher, new tab flow, and browser menu. The menu exposes navigation controls and the main page tools without leaving the current page.

## Known caveats

- There is no automated test suite.
- WebView does not provide FedCM, Web Bluetooth, Web USB, or native web notifications. Notifications are supplied only while an open tab is running.
- Private tabs and their data are not persisted.
- Closing a tab has no undo or recently closed list.
- Bookmark folders, homepage configuration, translation, printing, and bookmark/history import or export are not available.
- Animation performance must be evaluated in a release build; debug builds are not representative.
