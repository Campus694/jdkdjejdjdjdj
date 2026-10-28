# Payment Helper

Android app scaffold: Kotlin, Jetpack Compose, Material 3, minSdk 26.

## Build APK
Open this folder in Android Studio (JDK 17; Android SDK 35), wait for Gradle sync, then run:
`gradle assembleDebug`
APK output: `app/build/outputs/apk/debug/app-debug.apk`

Or push the project to a GitHub repository and run the included **Build Android APK** workflow. Download the `PaymentHelper-debug-apk` artifact from the workflow run.

## Install / use
1. Install the APK.
2. Choose the bank app.
3. Open Android Settings > Accessibility > Payment Helper balance reader and enable it yourself.
4. Return to Payment Helper and tap **Проверить баланс**.
5. Keep the bank's main account/balance screen visible while the app reads visible text.

## Notes
- Android and bank apps may restrict accessibility reads or background app launches. This is a best-effort prototype, not a guarantee for every bank.
- The service only inspects visible accessibility text from the package selected by the user. It does not tap controls, read password fields, transmit data, or bypass bank protections.
- Test only with your own account/device and follow your bank's terms.
