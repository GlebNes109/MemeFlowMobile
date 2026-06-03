# MemeFlow MVP: ТЗ для backend

## Цель

Реализовать backend для MVP MemeFlow, который поддерживает:

- регистрацию и логин пользователей;
- загрузку картинок и импорт медиа по URL;
- сохранение `YouTube Shorts` как внешних ссылок с preview и metadata;
- создание мемов;
- создание подборок;
- группы пользователей и инвайты;
- модель доступа `private / groups / public`;
- базовую модерацию;
- публичный feed, профиль пользователя и поиск.

Источник истины для HTTP-контракта:

- [docs/memeflow-mvp-openapi.yaml](/Users/gleb/AndroidStudioProjects/MemeFlow/docs/memeflow-mvp-openapi.yaml:1)

Архитектурные договоренности:

- [docs/mvp-architecture.md](/Users/gleb/AndroidStudioProjects/MemeFlow/docs/mvp-architecture.md:1)

## Общие требования

- Архитектура backend: модульный монолит.
- API: REST + JSON.
- Идентификаторы: UUID.
- Все timestamps хранить в UTC.
- Пагинация в списках: cursor-based.
- Основная БД: PostgreSQL.
- Хранилище файлов: S3-compatible storage.
- Авторизация: JWT access token + refresh token.
- Модель ролей на старте: `user`, `moderator`, `admin`.

## Модули backend

### Auth

- регистрация;
- логин;
- refresh token;
- получение текущего пользователя из access token.

### Users

- профиль текущего пользователя;
- публичный профиль пользователя;
- список собственных мемов и подборок;
- список публичных мемов и подборок пользователя.

### Groups

- создание группы;
- список моих групп;
- просмотр группы;
- приглашение пользователя в группу;
- принятие и отклонение инвайта;
- проверка членства для group-scoped контента.

### Media

- загрузка изображения файлом;
- регистрация внешней картинки по URL;
- регистрация `YouTube Shorts` по URL;
- получение статуса `MediaAsset`;
- фоновое извлечение metadata и thumbnail.

### Memes

- создание мема из `MediaAsset`;
- просмотр мема;
- редактирование собственного мема;
- удаление собственного мема;
- пересчет effective access.

### Collections

- создание подборки;
- редактирование подборки;
- удаление подборки;
- добавление мемов в подборку;
- удаление мемов из подборки;
- пересчет effective access мемов, затронутых подборкой.

### Feed

- публичный feed;
- accessible feed для авторизованного пользователя;
- feed группы.

### Search

- поиск по пользователям;
- поиск по подборкам;
- поиск по мемам по тегам и подписям.

### Moderation

- хранение moderation status;
- очередь на модерацию;
- решения `approved / rejected`;
- пользовательские жалобы.

## Доменные правила

### Пользователи и группы

- Пользователь может состоять в нескольких группах.
- Группа содержит участников с ролями `owner` и `member`.
- При создании группы создатель автоматически получает роль `owner`.
- Инвайт в группу создается только участником с ролью `owner`.

### Медиа

- `image_upload`: файл, загруженный на наш backend.
- `image_url`: внешняя картинка, сохраненная по URL.
- `youtube_short_url`: внешняя ссылка на short-video.
- В MVP short-video не скачивается на наш сервер как основной видеофайл.
- Для short-video backend сохраняет:
  - canonical URL;
  - title, если доступен;
  - duration, если доступна;
  - thumbnail;
  - provider metadata.

### Мем

- Мем всегда ссылается на один `MediaAsset`.
- Мем может входить в несколько подборок.
- У мема есть:
  - `direct_visibility`;
  - `direct_shared_group_ids[]`;
  - `effective_visibility`;
  - `effective_shared_group_ids[]`.

### Подборка

- Подборка имеет собственный доступ:
  - `private`;
  - `groups`;
  - `public`.
- Подборка может содержать несколько мемов.
- Один мем может состоять в нескольких подборках.
- У подборки есть собственные `shared_group_ids[]`, если `visibility=groups`.

## Формальная модель доступа

### Термины

- **direct access**: доступ, который автор задал самому мему напрямую.
- **effective access**: итоговый доступ мема после учета всех подборок, куда он входит.

### Порядок уровней доступа

Порядок силы доступа такой:

1. `public`
2. `groups`
3. `private`

### Правило вычисления effective access

Для каждого мема backend должен собрать все источники доступа:

- direct access самого мема;
- access каждой подборки, в которую мем включен.

Дальше применяется правило:

1. Если среди источников есть хотя бы один `public`, то:
   - `effective_visibility = public`
   - `effective_shared_group_ids = []`
2. Иначе, если есть хотя бы один `groups`, то:
   - `effective_visibility = groups`
   - `effective_shared_group_ids = union(all group ids from direct + collections)`
3. Иначе:
   - `effective_visibility = private`
   - `effective_shared_group_ids = []`

### Примеры

- private meme + groups collection => meme effective access becomes `groups`
- private meme + public collection => meme effective access becomes `public`
- public meme + private collection => meme remains `public`
- groups meme(A) + groups collection(B) => effective groups are `A ∪ B`

### Ключевой принцип реализации

Подборка не должна затирать `direct_visibility` мема.  
Она влияет только на `effective access`.

Это нужно для корректного поведения в двух случаях:

- мем удалили из подборки;
- подборка стала более закрытой или была удалена.

В этих случаях backend должен заново пересчитать effective access мема по оставшимся источникам.

## Когда backend обязан пересчитывать effective access

- после создания мема;
- после изменения доступа у мема;
- после создания подборки с `meme_ids`;
- после изменения доступа у подборки;
- после добавления мемов в подборку;
- после удаления мемов из подборки;
- после удаления подборки.

## Требования к API-поведению

### Auth

- `POST /v1/auth/register` создает пользователя и сразу возвращает сессию.
- `POST /v1/auth/login` логинит пользователя по `login/password`.
- `POST /v1/auth/refresh` выдает новую пару токенов.

### Users

- `GET /v1/users/me` возвращает текущий профиль и группы пользователя.
- `GET /v1/users/me/memes` возвращает все собственные мемы пользователя, включая непубличные.
- `GET /v1/users/me/collections` возвращает все собственные подборки пользователя.
- `GET /v1/users/{userId}` возвращает только публичную информацию.
- `GET /v1/users/{userId}/memes` возвращает только публичные и approved мемы.
- `GET /v1/users/{userId}/collections` возвращает только публичные и approved подборки.

### Media

- `POST /v1/media/images` принимает multipart upload картинки.
- `POST /v1/media/external-imports` принимает URL картинки или `YouTube Shorts`.
- `GET /v1/media/{mediaAssetId}` нужен мобильному клиенту для polling статуса импорта.
- Статусы `MediaAsset`:
  - `processing`
  - `ready`
  - `failed`
  - `blocked`

### Memes

- Создание мема возможно только из существующего `MediaAsset`.
- При `visibility=groups` `shared_group_ids` обязателен и не должен быть пустым.
- `GET /v1/memes/{memeId}` должен возвращать:
  - direct access;
  - effective access;
  - подборки, которые расширяют доступ к мему;
  - признак `can_edit`.

### Collections

- Подборка может быть создана сразу с `meme_ids`.
- Порядок `meme_ids` в create/add запросах должен задавать порядок отображения.
- Если подборка шире, чем direct access мемов внутри, backend обязан пересчитать effective access затронутых мемов.
- Если мем удален из подборки, backend обязан пересчитать его effective access.

### Groups

- Доступ к group feed имеет только участник группы.
- Если контент имеет `groups`-доступ с несколькими группами, пользователь получает доступ при членстве хотя бы в одной из них.

### Feed и Search

- Публичный feed показывает только `approved + effective_visibility=public`.
- Accessible feed авторизованного пользователя показывает:
  - все `approved + public`;
  - все `approved + groups`, где у пользователя есть membership хотя бы в одной из групп.
- Поиск ищет:
  - пользователей;
  - подборки;
  - мемы по тегам и подписям.

### Moderation

- Новый мем и новая подборка по умолчанию получают `moderation_status=pending`.
- До `approved` контент не должен попадать в публичный feed и публичный профиль.
- Модератор может выставить `approved` или `rejected`.
- Пользователь может отправить report на мем или подборку.

## Предлагаемая схема таблиц

### users

- `id`
- `login` unique
- `password_hash`
- `display_name`
- `avatar_url`
- `bio`
- `role`
- `created_at`

### refresh_tokens

- `id`
- `user_id`
- `token_hash`
- `expires_at`
- `created_at`
- `revoked_at`

### user_groups

- `id`
- `name`
- `owner_id`
- `created_at`

### group_memberships

- `group_id`
- `user_id`
- `role`
- `joined_at`

Уникальность:

- unique(`group_id`, `user_id`)

### group_invitations

- `id`
- `group_id`
- `inviter_user_id`
- `invitee_user_id`
- `status`
- `created_at`

### media_assets

- `id`
- `owner_id`
- `kind`
- `source_type`
- `provider`
- `original_url`
- `storage_url`
- `thumbnail_url`
- `title`
- `duration_seconds`
- `width`
- `height`
- `status`
- `metadata_json`
- `created_at`

### memes

- `id`
- `author_id`
- `media_asset_id`
- `caption`
- `direct_visibility`
- `effective_visibility`
- `moderation_status`
- `created_at`
- `updated_at`

### meme_tags

- `meme_id`
- `tag`

### meme_direct_groups

- `meme_id`
- `group_id`

### meme_effective_groups

- `meme_id`
- `group_id`

### collections

- `id`
- `author_id`
- `name`
- `description`
- `visibility`
- `moderation_status`
- `created_at`
- `updated_at`

### collection_groups

- `collection_id`
- `group_id`

### collection_items

- `collection_id`
- `meme_id`
- `position`
- `created_at`

Уникальность:

- unique(`collection_id`, `meme_id`)
- unique(`collection_id`, `position`)

### reports

- `id`
- `reporter_user_id`
- `target_type`
- `target_id`
- `reason`
- `comment`
- `status`
- `created_at`

### moderation_actions

- `id`
- `moderator_user_id`
- `target_type`
- `target_id`
- `decision`
- `comment`
- `created_at`

## Индексы, которые нужны сразу

- `users(login)`
- `memes(author_id, created_at desc)`
- `memes(effective_visibility, moderation_status, created_at desc)`
- `collections(author_id, created_at desc)`
- `collections(visibility, moderation_status, created_at desc)`
- `group_memberships(user_id)`
- `collection_items(meme_id)`
- `meme_direct_groups(group_id)`
- `meme_effective_groups(group_id)`
- `reports(target_type, target_id)`

## Фоновые задачи

### Import worker

- валидирует URL;
- определяет тип импорта: `image_url` или `youtube_short_url`;
- тянет metadata;
- сохраняет thumbnail при необходимости;
- переводит `media_assets.status` в `ready` или `failed`.

### Moderation worker

На MVP может быть очень простым:

- отправляет новый контент в moderation queue;
- при необходимости ставит `blocked` у media asset;
- не публикует контент без `approved`.

## Требования к транзакционности

- Создание подборки с `meme_ids` должно выполняться транзакционно:
  - создать подборку;
  - создать `collection_items`;
  - пересчитать effective access затронутых мемов.
- Изменение доступа подборки должно быть транзакционным вместе с пересчетом мемов.
- Удаление мема из подборки должно быть транзакционным вместе с пересчетом его effective access.

## Acceptance criteria для MVP

- Пользователь может зарегистрироваться и залогиниться.
- Пользователь может загрузить картинку и создать из нее мем.
- Пользователь может вставить URL картинки и создать мем.
- Пользователь может вставить `YouTube Shorts` URL и создать meme-card с внешним video preview.
- Пользователь может создать группу и пригласить другого пользователя.
- Пользователь может создать private, groups или public мем.
- Пользователь может создать подборку с теми же уровнями доступа.
- При помещении private мема в groups подборку effective access мема становится `groups`.
- При помещении любого непубличного мема в public подборку effective access мема становится `public`.
- При удалении мема из более широкой подборки effective access мема пересчитывается корректно.
- Публичный feed не показывает pending/rejected контент.
- Group feed доступен только участникам соответствующей группы.
- `GET /v1/memes/{memeId}` возвращает direct и effective access.
- По OpenAPI-контракту можно сгенерировать клиент для мобильного приложения без ручных договоренностей “в чате”.
