# Моя пряжа Test — локальный проект

Это самостоятельный проект для Android Studio. Для дальнейшей работы Git/GitHub не требуется.

## Разделение приложений
- Рабочая «Моя пряжа»: `ru.yarnnotebook.app`
- Тестовая «Моя пряжа Test»: `ru.yarnnotebook.test`

Из-за разных applicationId Android хранит базу, настройки, файлы и данные двух приложений отдельно. Их можно держать установленными одновременно.

## Первый запуск
1. Распакуйте папку `MoyaPryazhaTest`, например в `C:\Users\Gleb\AndroidStudioProjects\MoyaPryazhaTest`.
2. Android Studio → File → Open → выберите эту папку.
3. Дождитесь Gradle Sync.
4. Откройте `vkid.local.properties`.
5. Замените `PASTE_VK_PROTECTED_KEY_HERE` на «Защищённый ключ» приложения VK 54803965.
6. Нажмите ▶ Run.

## VK ID
- App ID: 54803965
- Android package: `ru.yarnnotebook.test`
- SHA-1: `0A:84:FD:BD:83:61:98:93:CC:52:84:75:7C:F7:97:86:7D:2F:3A:82`
- VK ID SDK: 2.7.3
- Scope: `photos`

Файл тестовой подписи уже находится в `app/yarn-debug.p12`, поэтому SHA-1 при локальной debug-сборке сохраняется.

## Сборка APK
В Android Studio: Build → Build APK(s)

Либо запустите `BUILD_TEST_APK.bat`.

APK появится в:
`app\build\outputs\apk\debug\app-debug.apk`
