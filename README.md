# PDF Лист — Android APK для Samsung Galaxy S25 FE

Минималистичное приложение **только для чтения и пересылки PDF-файлов** по почте и в мессенджеры.

## Параметры сборки
- Package ID: `com.s25fe.pdfviewer`
- Target SDK: 35 (Android 15 / One UI)
- Min SDK: 26 (Android 8.0+)
- Права доступа: без `INTERNET` и без доступа ко всей памяти телефона.

## Как получить APK за 2 минуты

### Вариант 1: Через GitHub Actions (без установки Android Studio)
1. Создайте новый репозиторий на GitHub и загрузите все файлы из этого архива (включая скрытую папку `.github/workflows`).
2. Откройте вкладку **Actions** в репозитории GitHub.
3. Дождитесь окончания задачи **Build S25 FE Debug APK** и скачайте архив `PdfList-S25FE-debug-apk`, внутри которого находится `app-debug.apk`.
4. Скопируйте `app-debug.apk` на Samsung S25 FE и откройте через приложение «Мои файлы».

### Вариант 2: В Android Studio на компьютере
1. Откройте эту папку в **Android Studio** (Ladybug или новее).
2. Нажмите **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Передайте готовый `app-debug.apk` на Samsung Galaxy S25 FE.
