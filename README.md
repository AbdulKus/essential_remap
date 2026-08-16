# Essential Remap

[English](#english) · [Русский](#русский) · [❤️ Donate](https://abdulkus.github.io/donate) · [Releases](../../releases)

[![Android build and release](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml/badge.svg)](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml)

---

## English

**Essential Remap** is a free utility for remapping the physical **Essential Key** on Nothing OS. It was developed and tested primarily on the Nothing Phone (4a) Pro.

Assign a different action to a **single press**, **double press**, and **long press** — including while the phone is locked or the screen is off.

### Features

- Launch apps, camera, flashlight, screenshots and URLs.
- Home, Back, Recents, notifications and Quick Settings.
- Media controls, assistant and Circle to Search.
- Sound mode: normal, vibrate, silent or toggle.
- Configurable haptic feedback.
- Separate lock-screen and screen-off behavior for every press type.
- Built-in update checker for GitHub Releases with APK signature verification.
- 10 interface languages: EN, RU, DE, FR, PL, UA, HI, CH, JP, KO.
- No root required.

### How it works

For normal screen-on use, Essential Remap uses an Android **Accessibility Service** only to intercept the Essential Key. It does not read screen content, messages, passwords or typed text.

Nothing OS normally reserves the key for Essential Space. For optional **screen-off support**, the app uses Android's local **Wireless Debugging** once during setup to:

- release the Essential Key from Nothing's Essential Space packages;
- keep `nt_block_essential_key=1`;
- start a small filtered shell monitor that listens only for the Essential Key input event.

The setup is reversible from the app. No package data is deleted.

After a full phone reboot the shell monitor stops, so screen-off handling needs to be restarted from Essential Remap. The saved ADB identity is reused when possible, so pairing normally does not need to be repeated.

### Install

1. Download the latest signed APK from [Releases](../../releases).
2. Choose a language.
3. Follow the in-app setup and enable **Essential Remap** in Android Accessibility settings.
4. Assign actions to the Essential Key.

If you enable screen-off support, the app will guide you through Wireless Debugging setup.

> Before uninstalling Essential Remap, use **Restore Essential Space** in the app.

### Privacy

Essential Remap contains **no analytics or telemetry**. Settings, ADB credentials and usage configuration remain on the device. Network access is used only for GitHub update checks and user-configured HTTP actions.

### License & attribution

This repository is currently distributed under the **MIT License**. Parts of the low-level key handling and local ADB setup are based on the MIT-licensed [wreck2053/essential-key](https://github.com/wreck2053/essential-key). See [NOTICE](NOTICE).

---

## Русский

**Essential Remap** — бесплатная утилита для переназначения физической кнопки **Essential Key** в Nothing OS. В первую очередь приложение разрабатывается и тестируется на Nothing Phone (4a) Pro.

Для **одиночного нажатия**, **двойного нажатия** и **удержания** можно назначить разные действия — в том числе на экране блокировки и при выключенном дисплее.

### Возможности

- Запуск приложений, камеры, фонарика, скриншота и ссылок.
- Домой, Назад, Недавние, уведомления и быстрые настройки.
- Управление медиа, ассистент и Circle to Search.
- Режим звука: обычный, вибрация, без звука или переключение.
- Настраиваемая сила виброотклика.
- Отдельные настройки работы на блокировке и с выключенным экраном для каждого типа нажатия.
- Проверка обновлений через GitHub Releases, загрузка APK и проверка подписи перед установкой.
- 10 языков интерфейса: EN, RU, DE, FR, PL, UA, HI, CH, JP, KO.
- Root не требуется.

### Как это работает

При включённом экране Essential Remap использует системную **службу специальных возможностей** только для перехвата Essential Key. Приложение не читает содержимое экрана, сообщения, пароли или вводимый текст.

Nothing OS по умолчанию резервирует кнопку для Essential Space. Для дополнительной **работы при выключенном экране** приложение один раз использует локальную **беспроводную отладку Android**, чтобы:

- освободить Essential Key от пакетов Essential Space;
- сохранить `nt_block_essential_key=1`;
- запустить небольшой shell-монитор, который слушает только событие Essential Key.

Все изменения обратимы из самого приложения. Данные пакетов Nothing не удаляются.

После полной перезагрузки телефона shell-монитор останавливается, поэтому работу с выключенным экраном нужно снова запустить из Essential Remap. Сохранённый ADB-ключ используется повторно, поэтому заново выполнять сопряжение обычно не требуется.

### Установка

1. Скачайте последний подписанный APK из [Releases](../../releases).
2. Выберите язык.
3. Пройдите встроенную настройку и включите **Essential Remap** в специальных возможностях Android.
4. Назначьте действия на Essential Key.

Если нужна работа при выключенном экране, приложение само проведёт через настройку Wireless Debugging.

> Перед удалением Essential Remap используйте в приложении **«Восстановить Essential Space»**.

### Конфиденциальность

В Essential Remap **нет аналитики и телеметрии**. Настройки, ADB-ключи и конфигурация действий остаются на устройстве. Интернет используется только для проверки обновлений на GitHub и HTTP-действий, которые пользователь настроил сам.

### Лицензия и авторство

Сейчас репозиторий распространяется по лицензии **MIT**. Часть низкоуровневой обработки кнопки и локальной ADB-настройки основана на MIT-проекте [wreck2053/essential-key](https://github.com/wreck2053/essential-key). Подробности — в [NOTICE](NOTICE).
