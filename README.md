# Essential Remap

[English](#english) · [Русский](#русский) · [❤️ Donate](https://abdulkus.github.io/donate) · [Releases](../../releases)

[![Android build and release](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml/badge.svg)](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml)

---

## English

**Essential Remap** is a free utility for remapping the physical **Essential Key** on Nothing OS.

Assign different actions to a **single press**, **double press**, and **long press** — including while the phone is locked or the **screen is completely off**.

Tested on **Nothing Phone (3a), Nothing Phone (4a), and Nothing Phone (4a) Pro**.

### Key features

- **Screen-off remapping** — Essential Key actions can work even when the display is off.
- **Persistent ADB identity** — after the initial Wireless Debugging pairing, the app securely reuses the saved ADB key whenever possible, so you normally do not need to enter a new pairing code every time the screen-off monitor is restarted.
- **Automatic updates from GitHub Releases** — the app checks for new versions, downloads the APK inside the app, verifies the package/version/signing certificate, and then opens the standard Android installer.
- **Separate actions for single press, double press, and hold**, with independent lock-screen and screen-off settings.
- **No root required.**
- Launch apps, camera, flashlight, screenshots and URLs.
- Home, Back, Recents, notifications and Quick Settings.
- Media controls, assistant and Circle to Search.
- Sound mode: normal, vibrate, silent or toggle.
- Configurable haptic feedback.
- 10 interface languages: EN, RU, DE, FR, PL, UA, HI, CH, JP, KO.

### How it works

With the screen on, Essential Remap uses an Android **Accessibility Service** only to intercept the Essential Key. Window content retrieval is disabled; the service does not read screen contents, messages, passwords or typed text.

Nothing OS normally reserves the button for Essential Space. To enable the key outside that default behavior, the built-in setup uses the phone's own local **Wireless Debugging** connection to:

- disable the Nothing packages that consume the Essential Key without deleting their data;
- keep `nt_block_essential_key=1`;
- install and start a small **shell monitor** for screen-off key handling.

The monitor runs under Android's non-root `shell` user. A small Java helper, launched through `app_process`, waits for Essential Key events from `gpio-keys` using `getevent`. It classifies presses at the input source and sends them over a local Unix socket that verifies both peers' UIDs. Transport delays cannot turn a tap into a hold. A DUMP-protected explicit broadcast is used only when the socket is unavailable to help reconnect the app.

Input and delivery have separate threads, bounded queues, stale-event rejection and duplicate-action receipts. Reader/helper crashes trigger a limited number of recovery attempts. During normal idle operation there is no polling, network traffic, heartbeat, wakeup alarm or CPU wake lock. Short partial wake locks cover the input handoff, gesture and action only; they do not light the display. Allow unrestricted battery use in Android settings for deep-sleep handling. The status checks a live connection rather than just remembering a successful installation.

After upgrading to **0.1.28**, restart the sleep monitor once in Settings to install revision 9. Updating the APK alone does not replace an already running shell process. Device verification steps are in [screen-off testing](docs/screen-off-testing.md).

The setup script is installed through the local ADB connection, checked before being activated, and can be restarted from Essential Remap. All package changes are reversible from the app and Essential Space can be restored at any time.

A full phone reboot naturally stops Android shell processes. Essential Remap detects that the screen-off monitor needs to be started again. Because the app stores its ADB identity locally, it can usually reconnect with the previously authorized key instead of requiring a new 6-digit pairing code. Wireless Debugging still has to be enabled by the user after a reboot if Android disabled it.

### Install

1. Download the latest signed APK from [Releases](../../releases).
2. Choose a language.
3. Follow the in-app setup and enable **Essential Remap** in Android Accessibility settings.
4. Assign actions to the Essential Key.
5. Enable **Run while screen is off** if you want screen-off remapping; the app will guide you through Wireless Debugging setup.

> Before uninstalling Essential Remap, use **Restore Essential Space** in the app.

### Privacy

Essential Remap contains **no analytics or telemetry**. Settings, ADB credentials and action configuration remain on the device. Network access is used only for GitHub update checks and user-configured HTTP actions.

### License & attribution

This repository is currently distributed under the **MIT License**. Parts of the low-level key handling and local ADB setup are based on the MIT-licensed [wreck2053/essential-key](https://github.com/wreck2053/essential-key). See [NOTICE](NOTICE).

---

## Русский

**Essential Remap** — бесплатная утилита для переназначения физической кнопки **Essential Key** в Nothing OS.

Для **одиночного нажатия**, **двойного нажатия** и **удержания** можно назначить разные действия — в том числе на экране блокировки и при **полностью выключенном дисплее**.

Приложение протестировано на **Nothing Phone (3a), Nothing Phone (4a) и Nothing Phone (4a) Pro**.

### Главные возможности

- **Работа при выключенном экране** — назначенные действия Essential Key могут выполняться даже когда дисплей выключен.
- **Сохранение ADB-ключа** — после первого сопряжения через Wireless Debugging приложение безопасно использует сохранённый ADB-ключ повторно. Обычно не нужно каждый раз вводить новый шестизначный код при перезапуске screen-off monitor.
- **Автоматические обновления через GitHub Releases** — приложение само проверяет новые версии, скачивает APK, проверяет пакет, версию и сертификат подписи, после чего открывает стандартный установщик Android.
- **Отдельные действия для одиночного, двойного нажатия и удержания**, со своими настройками блокировки и выключенного экрана.
- **Root не требуется.**
- Запуск приложений, камеры, фонарика, скриншота и ссылок.
- Домой, Назад, Недавние, уведомления и быстрые настройки.
- Управление медиа, ассистент и Circle to Search.
- Режим звука: обычный, вибрация, без звука или переключение.
- Настраиваемая сила виброотклика.
- 10 языков интерфейса: EN, RU, DE, FR, PL, UA, HI, CH, JP, KO.

### Как это работает

При включённом экране Essential Remap использует системную **службу специальных возможностей** только для перехвата Essential Key. Получение содержимого окон отключено: приложение не читает содержимое экрана, сообщения, пароли или вводимый текст.

Nothing OS по умолчанию резервирует кнопку для Essential Space. Чтобы освободить Essential Key и добавить полноценную работу вне стандартного поведения, встроенная настройка использует локальную **беспроводную отладку Android** на самом телефоне и:

- отключает пакеты Nothing, которые перехватывают Essential Key, не удаляя их данные;
- сохраняет `nt_block_essential_key=1`;
- устанавливает и запускает небольшой **shell-monitor** для обработки кнопки при выключенном дисплее.

Монитор работает от пользователя Android `shell`, без root. Небольшой Java-процесс запускается через `app_process`, ожидает события Essential Key через `getevent` и определяет жест рядом с источником ввода. С приложением он общается через локальный Unix-сокет с проверкой UID обеих сторон. Задержка доставки не превращает клик в удержание. Защищённый разрешением DUMP broadcast используется только при недоступности сокета, чтобы восстановить связь с приложением.

Чтение и доставка разделены; очереди ограничены, устаревшие события отбрасываются, повторное выполнение защищено подтверждениями. При завершении чтения или helper-процесса выполняется ограниченное число попыток восстановления. В обычном простое нет опроса, сетевого трафика, heartbeat, будильников или CPU wake lock. Короткая блокировка сна покрывает только передачу нажатия, распознавание и действие; экран от неё не загорается. Для работы в глубоком сне разрешите приложению использование батареи без ограничений. Статус проверяет живое соединение, а не только факт прошлой установки.

После обновления до **0.1.28** один раз перезапустите монитор сна в настройках — это установит ревизию 9. Обновление APK само по себе не заменяет уже работающий shell-процесс. Сценарии проверки описаны в [screen-off testing](docs/screen-off-testing.md).

Сам shell-скрипт устанавливается через локальное ADB-соединение, проверяется перед активацией и при необходимости может быть заново запущен прямо из Essential Remap. Все изменения пакетов обратимы, а Essential Space можно восстановить из настроек приложения.

После полной перезагрузки телефона Android завершает shell-процессы, поэтому monitor нужно запустить снова. Essential Remap определяет это состояние и предлагает восстановить работу. Поскольку ADB-идентификатор сохраняется локально, приложение обычно подключается уже авторизованным ключом и не требует нового шестизначного кода сопряжения. Если после перезагрузки Android отключил Wireless Debugging, пользователю нужно только снова включить его.

### Установка

1. Скачайте последний подписанный APK из [Releases](../../releases).
2. Выберите язык.
3. Пройдите встроенную настройку и включите **Essential Remap** в специальных возможностях Android.
4. Назначьте действия на Essential Key.
5. Включите **«Работать на выключенном экране»**, если нужна работа с погашенным дисплеем — приложение само проведёт через настройку Wireless Debugging.

> Перед удалением Essential Remap используйте в приложении **«Восстановить Essential Space»**.

### Конфиденциальность

В Essential Remap **нет аналитики и телеметрии**. Настройки, ADB-ключи и конфигурация действий остаются на устройстве. Интернет используется только для проверки обновлений на GitHub и HTTP-действий, которые пользователь настроил сам.

### Лицензия и авторство

Сейчас репозиторий распространяется по лицензии **MIT**. Часть низкоуровневой обработки кнопки и локальной ADB-настройки основана на MIT-проекте [wreck2053/essential-key](https://github.com/wreck2053/essential-key). Подробности — в [NOTICE](NOTICE).
