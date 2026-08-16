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

The shell monitor runs under Android's non-root `shell` user. It waits directly for input events from the phone's `gpio-keys` device using Android's `getevent` interface and filters for the Essential Key event only. It does not continuously poll the system and does not hold a CPU wake lock while waiting.

When the Essential Key is pressed with the display off, the shell monitor forwards a narrowly scoped, permission-protected event to Essential Remap. The app then classifies the physical press as single, double or long and executes the action configured by the user. The normal Accessibility path and the shell path are deduplicated so the same physical press is not handled twice.

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

Shell-monitor работает от системного пользователя Android `shell`, без root. Он напрямую ожидает события устройства `gpio-keys` через стандартный Android-инструмент `getevent` и отфильтровывает только событие Essential Key. Постоянного опроса системы нет, CPU wake lock во время ожидания не удерживается.

Когда Essential Key нажимается при выключенном экране, shell-monitor передаёт в Essential Remap узко ограниченное, защищённое разрешением событие. Приложение определяет, было это одиночное нажатие, двойное или удержание, и выполняет действие, выбранное пользователем. События от Accessibility и shell-monitor дедуплицируются, поэтому одно физическое нажатие не выполняется дважды.

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
