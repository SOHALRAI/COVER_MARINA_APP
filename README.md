# Marina Festival Android App

Android Studio project for `https://cover.marinapublications.com/`.

## What it does
- Opens the existing Marina Publications Festival Portal in an Android WebView.
- Keeps the website's existing login/session cookies.
- Intercepts the portal's `.share-btn` click.
- Downloads the selected image/video using the WebView session cookie.
- Shares the actual media file through Android `Intent.ACTION_SEND`.
- Uses AndroidX `FileProvider` content URIs and temporary read permissions.
- Supports website file upload chooser and Android back navigation.

## Build APK in Android Studio
1. Install Android Studio (latest stable) and Android SDK Platform 35.
2. Open this folder (`MarinaFestivalAndroid`) in Android Studio. If prompted, select Gradle 8.9 (the wrapper distribution URL is configured).
3. Allow Gradle sync to finish.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

For a signed release APK: **Build > Generate Signed Bundle / APK**.

## Important deployment notes
- The app points to HTTPS at `cover.marinapublications.com`. Ensure the domain has a valid SSL certificate.
- The website's Share buttons must retain the `.share-btn` class and `data-url` / `data-title` attributes. The app intercepts these buttons and shares the real media file.
- This project does not contain website passwords or database credentials. Users log in through the website as usual.
- The native share sheet displays compatible Android apps. Select WhatsApp to send the actual attachment.
- Large media files are temporarily downloaded to the app cache (maximum 150 MB per share).

## Package
`com.marina.publications.festivalportal`


## Build APK automatically on GitHub (without installing Android Studio)
1. Create a new GitHub repository.
2. Upload all files from this project to its `main` branch.
3. Open the repository's **Actions** tab.
4. Run **Build Marina Festival APK** (or wait for the first push build).
5. Open the completed workflow run and download the `MarinaFestival-debug-apk` artifact.
6. Extract the downloaded artifact ZIP; it contains `app-debug.apk`.

This workflow builds the debug APK using GitHub-hosted Android build tools. The APK is unsigned for Play Store distribution but can be installed for testing after enabling installation from that source.
