# MemeFlow MVP: ТЗ для мобильного приложения

## Цель

Реализовать Android-приложение MemeFlow для MVP, которое позволяет:

- зарегистрироваться и войти в аккаунт;
- смотреть публичный feed мемов;
- искать пользователей, подборки и мемы;
- открывать публичные профили пользователей;
- загружать мемы:
  - картинкой из файла;
  - картинкой по URL;
  - `YouTube Shorts` по URL;
- создавать и редактировать мемы;
- создавать и редактировать подборки;
- работать с группами пользователей;
- видеть собственные мемы и подборки;
- понимать direct/effective доступ у мемов;
- отправлять жалобы на контент.

Источник истины для API:

- [docs/memeflow-mvp-openapi.yaml](/Users/gleb/AndroidStudioProjects/MemeFlow/docs/memeflow-mvp-openapi.yaml:1)

Архитектурные договоренности:

- [docs/mvp-architecture.md](/Users/gleb/AndroidStudioProjects/MemeFlow/docs/mvp-architecture.md:1)

Backend ТЗ:

- [docs/backend-mvp-spec.md](/Users/gleb/AndroidStudioProjects/MemeFlow/docs/backend-mvp-spec.md:1)

## Платформа и стек

- Платформа: Android
- UI: `Jetpack Compose`
- Навигация: `Navigation Compose`
- Архитектура presentation-слоя: `MVVM`
- Состояния: `StateFlow`
- Асинхронность: `Kotlin Coroutines`
- Сеть: `Retrofit` или `Ktor Client`
- JSON: `kotlinx.serialization` или `Moshi`
- Изображения: `Coil`
- Локальное хранение:
  - `DataStore` для токенов/сессии
  - по необходимости `Room` позже, но для MVP не обязателен

## Общая архитектура приложения

Для MVP достаточно одного Gradle-модуля `app`, но с четким делением по пакетам.

Рекомендуемая структура:

```text
com.memeflow.app
  core/
    network/
    auth/
    model/
    ui/
    common/
  data/
    remote/
    repository/
    mapper/
  feature/
    auth/
    feed/
    search/
    upload/
    meme/
    collection/
    profile/
    groups/
    moderation/
    settings/
  navigation/
```

## Архитектурные правила

### UI

- `Composable`-экраны не ходят в API напрямую.
- Каждый экран работает через свою `ViewModel`.
- `ViewModel` получает данные только через `Repository` или `UseCase`.
- Весь UI рендерится из одного `UiState` плюс одноразовых `UiEvent`.

### Data layer

- Весь HTTP доступ идет через единый API-клиент.
- Ответы API сначала попадают в DTO.
- DTO маппятся в domain/UI models.
- UI не должен знать детали OpenAPI-DTO.

### Ошибки

- Все сетевые ошибки приводятся к единому типу:
  - `Unauthorized`
  - `Forbidden`
  - `NotFound`
  - `Validation`
  - `Network`
  - `Unknown`
- На уровне UI ошибки показываются человекочитаемо.

## Главные сущности на клиенте

### Session

- access token
- refresh token
- current user short info
- флаг `isAuthorized`

### Meme

- `id`
- `author`
- `media`
- `caption`
- `tags`
- `directVisibility`
- `directSharedGroupIds`
- `effectiveVisibility`
- `effectiveSharedGroupIds`
- `accessSource`
- `moderationStatus`
- `createdAt`

### MemeDetails

Дополнительно к `Meme`:

- `canEdit`
- `collectionIds`
- `inheritedAccessFromCollections`

### Collection

- `id`
- `name`
- `description`
- `visibility`
- `sharedGroupIds`
- `itemCount`
- `coverThumbnailUrl`
- `items`

### Group

- `id`
- `name`
- `members`
- `currentUserRole`

### MediaAsset

- `id`
- `kind`
- `sourceType`
- `provider`
- `status`
- `originalUrl`
- `storageUrl`
- `thumbnailUrl`

## Навигация приложения

Нижний `bottom bar` доступен на основных экранах:

- `Главная`
- `Загрузить`
- `Мой профиль`
- `Настройки`

Отдельные маршруты:

- Splash / session bootstrap
- Auth
- Feed
- Search
- Public Profile
- Meme Details
- Collection Details
- Upload Flow
- Create / Edit Meme
- Create / Edit Collection
- My Profile
- My Memes
- My Collections
- Groups List
- Group Details
- Invitations
- Settings

## Список экранов MVP

### 1. Splash

Назначение:

- проверить наличие токенов;
- восстановить сессию;
- открыть `Feed` или `Auth`.

Состояния:

- loading
- authorized
- unauthorized

### 2. Auth

Экран(ы):

- login
- registration

Поля:

- `login`
- `password`
- `displayName` для регистрации

Сценарии:

- пользователь логинится;
- пользователь регистрируется;
- при успехе открывается `Feed`.

Валидация:

- пустые поля;
- минимальная длина пароля;
- конфликт логина;
- неверные учетные данные.

### 3. Feed

Назначение:

- показать основной feed мемов.

Источник данных:

- `GET /v1/feed`

Поведение:

- для гостя используется `scope=public`;
- для авторизованного пользователя можно использовать `scope=accessible`;
- список пагинируется по cursor;
- элемент feed открывает `Meme Details` или `Collection Details`, если это вход в подборку через профиль/поиск.

Карточка мема в ленте показывает:

- превью картинки или thumbnail short-video;
- автора;
- подпись;
- теги;
- индикатор типа контента;
- признак публичности, если это нужно для UI;
- действие “пожаловаться”.

Состояния:

- initial loading
- content
- empty
- pagination loading
- pagination error
- full screen error

### 4. Search

Назначение:

- искать пользователей, подборки, мемы.

Источник данных:

- `GET /v1/search`

Поведение:

- поиск запускается с debounce;
- минимальная длина запроса: 2 символа;
- на экране три секции результатов:
  - users
  - collections
  - memes

### 5. Public Profile

Назначение:

- показывать публичную страницу пользователя.

Источник данных:

- `GET /v1/users/{userId}`
- `GET /v1/users/{userId}/memes`
- `GET /v1/users/{userId}/collections`

Контент:

- аватар;
- логин;
- display name;
- bio;
- публичные мемы;
- публичные подборки.

### 6. My Profile

Назначение:

- показывать собственный профиль и личный контент.

Источник данных:

- `GET /v1/users/me`
- `GET /v1/users/me/memes`
- `GET /v1/users/me/collections`

Контент:

- профиль;
- мои группы;
- мои мемы;
- мои подборки;
- переходы в группы, инвайты, редактирование профиля.

Важно:

- на этом экране должны быть видны не только публичные, но и private/groups элементы.

### 7. Upload Flow

Экран выбора способа загрузки:

- загрузить картинку файлом;
- вставить ссылку на картинку;
- вставить ссылку на `YouTube Shorts`.

#### 7.1 Upload Image File

API:

- `POST /v1/media/images`
- затем `POST /v1/memes`

Поток:

1. пользователь выбирает файл;
2. клиент загружает `MediaAsset`;
3. после успешного upload открывается форма создания мема.

#### 7.2 Import Image URL

API:

- `POST /v1/media/external-imports`
- `GET /v1/media/{mediaAssetId}` polling
- затем `POST /v1/memes`

Поток:

1. пользователь вставляет URL;
2. создается `MediaAsset` со статусом `processing` или `ready`;
3. если статус не `ready`, клиент делает polling;
4. после `ready` открывается форма создания мема.

#### 7.3 Import YouTube Shorts URL

API:

- `POST /v1/media/external-imports`
- `GET /v1/media/{mediaAssetId}` polling
- затем `POST /v1/memes`

Особенность:

- short не скачивается как видеофайл;
- клиент работает с thumbnail и metadata.

### 8. Create / Edit Meme

Назначение:

- создать мем после подготовки `MediaAsset`;
- редактировать собственный мем.

API:

- `POST /v1/memes`
- `PATCH /v1/memes/{memeId}`
- `GET /v1/memes/{memeId}`

Поля:

- preview media
- caption
- tags
- access type:
  - private
  - groups
  - public
- выбор групп, если `groups`

Требования по UX:

- если выбран `groups`, нельзя сохранить без выбранной хотя бы одной группы;
- при редактировании нужно показывать:
  - direct access;
  - effective access;
  - список подборок, которые расширяют доступ.

UI-объяснение:

- пользователь должен видеть, что direct access можно изменить;
- но effective access может остаться шире из-за подборок.

### 9. Meme Details

Назначение:

- показать полную информацию о меме;
- дать действия над мемом.

Контент:

- медиа;
- автор;
- caption;
- tags;
- direct access;
- effective access;
- коллекции, в которые входит мем;
- объяснение inherited access, если есть;
- кнопки edit/delete для автора;
- report для других пользователей.

Отдельно:

- для external video должен быть понятный UI, что это внешнее видео, а не локально захостенный файл.

### 10. Create / Edit Collection

Назначение:

- создать и отредактировать подборку.

API:

- `POST /v1/collections`
- `PATCH /v1/collections/{collectionId}`
- `POST /v1/collections/{collectionId}/items`
- `DELETE /v1/collections/{collectionId}/items/{memeId}`

Поля:

- name
- description
- access type:
  - private
  - groups
  - public
- выбор групп, если `groups`
- список выбранных мемов

Требования по UX:

- при выборе более широкого уровня доступа, чем у некоторых мемов, нужно показать предупреждение:
  - подборка расширит доступ к этим мемам;
  - effective access этих мемов изменится.

Это критично, потому что иначе пользователь не поймет, почему private мем внезапно стал доступен группе или публично.

### 11. Collection Details

Назначение:

- показать подборку и ее содержимое.

Контент:

- название;
- описание;
- автор;
- access level подборки;
- элементы подборки в порядке `position`;
- переход в `Meme Details`.

### 12. Groups List

Назначение:

- показать все группы текущего пользователя.

API:

- `GET /v1/groups/my`
- `GET /v1/group-invitations`

Контент:

- мои группы;
- входящие инвайты;
- кнопка создать группу.

### 13. Create Group

API:

- `POST /v1/groups`

Поля:

- name

### 14. Group Details

Назначение:

- показать состав группы и group feed.

API:

- `GET /v1/groups/{groupId}`
- `GET /v1/groups/{groupId}/feed`
- `POST /v1/groups/{groupId}/invites`

Контент:

- название группы;
- участники;
- мемы, доступные группе;
- приглашение пользователя в группу.

### 15. Invitations

Назначение:

- принять или отклонить инвайт.

API:

- `POST /v1/group-invitations/{inviteId}/accept`
- `POST /v1/group-invitations/{inviteId}/decline`

### 16. Settings

Минимум для MVP:

- logout
- базовая информация о профиле

Опционально:

- редактирование `displayName`
- редактирование `bio`

## Требования к отображению доступа

Это самый важный пользовательский момент.

### Термины в UI

- `private` -> “Приватный”
- `groups` -> “Ограниченный”
- `public` -> “Публичный”

### Что обязательно показывать

Для собственного мема:

- direct access;
- effective access;
- если они отличаются, короткое пояснение:
  - “Доступ расширен через подборки”.

Для подборки:

- ее текущий доступ;
- предупреждение перед сохранением, если она расширяет доступ некоторых мемов.

### Когда показывать предупреждение

При создании или редактировании подборки приложение должно уметь выделять случаи:

- meme direct access = `private`, collection = `groups`
- meme direct access = `private`, collection = `public`
- meme direct access = `groups`, collection = `public`

Пользователь должен явно увидеть, что подборка расширит effective access.

## Состояния экранов

У каждого списка и detail-экрана должен быть единый набор состояний:

- `Loading`
- `Content`
- `Empty`
- `Error`

Для форм:

- `Idle`
- `Submitting`
- `Success`
- `ValidationError`
- `Error`

Для polling импорта:

- `Processing`
- `Ready`
- `Failed`
- `Blocked`

## Polling для external import

Нужно для:

- картинки по URL;
- `YouTube Shorts`.

Правила:

- после создания `MediaAsset` клиент проверяет `status`;
- если статус `processing`, запускает polling `GET /v1/media/{mediaAssetId}`;
- polling останавливается на `ready`, `failed`, `blocked`;
- при `ready` открывается форма создания мема;
- при `failed` или `blocked` показывается понятная ошибка.

## Работа с сессией

### Требования

- access token автоматически подставляется в авторизованные запросы;
- refresh token используется для обновления access token;
- при `401` клиент пробует refresh один раз;
- если refresh не удался, пользователь разлогинивается и попадает на `Auth`.

### Logout

При logout нужно:

- очистить токены;
- очистить in-memory session state;
- вернуть пользователя на экран авторизации.

## Репозитории

Минимальный набор:

- `AuthRepository`
- `UserRepository`
- `FeedRepository`
- `SearchRepository`
- `MediaRepository`
- `MemeRepository`
- `CollectionRepository`
- `GroupRepository`
- `ReportRepository`

## Use cases

Минимальный набор:

- `ObserveSessionUseCase`
- `LoginUseCase`
- `RegisterUseCase`
- `LoadFeedUseCase`
- `SearchUseCase`
- `UploadImageUseCase`
- `ImportExternalMediaUseCase`
- `PollMediaStatusUseCase`
- `CreateMemeUseCase`
- `UpdateMemeUseCase`
- `CreateCollectionUseCase`
- `UpdateCollectionUseCase`
- `AddMemesToCollectionUseCase`
- `RemoveMemeFromCollectionUseCase`
- `LoadMyProfileUseCase`
- `LoadPublicProfileUseCase`
- `LoadMyGroupsUseCase`
- `InviteUserToGroupUseCase`
- `RespondToInvitationUseCase`
- `CreateReportUseCase`

## ViewModel состав MVP

- `SplashViewModel`
- `AuthViewModel`
- `FeedViewModel`
- `SearchViewModel`
- `PublicProfileViewModel`
- `MyProfileViewModel`
- `UploadViewModel`
- `CreateEditMemeViewModel`
- `MemeDetailsViewModel`
- `CreateEditCollectionViewModel`
- `CollectionDetailsViewModel`
- `GroupsViewModel`
- `GroupDetailsViewModel`
- `InvitationsViewModel`
- `SettingsViewModel`

## Что должно быть реализовано в UI-kit / core-ui

- app scaffold
- bottom bar
- avatar
- meme card
- collection card
- empty state
- full-screen error state
- inline error
- loading placeholders
- chips for tags
- chips / badges for access level
- moderation status badge для owner-only экранов

## Особенности по контенту

### Image meme

- показывается как обычная картинка;
- поддерживается aspect ratio без кривого crop по умолчанию.

### External video meme

- показывается thumbnail;
- поверх может быть иконка play;
- пользователь должен понимать, что это short по ссылке.

## Минимальные нефункциональные требования

- приложение не должно падать при пустых списках;
- повторное открытие экрана не должно дублировать запросы бесконтрольно;
- пагинация не должна запускаться параллельно несколько раз;
- все длинные операции должны иметь loading state;
- сеть и ошибки должны переживаться без крэша UI;
- Compose UI должен корректно работать на телефонах с небольшим экраном.

## Acceptance criteria для мобильного MVP

- Пользователь может зарегистрироваться и залогиниться.
- После входа пользователь попадает в feed.
- Гость может открыть приложение и смотреть публичный feed.
- Пользователь может найти другого пользователя, подборку и мем через поиск.
- Пользователь может открыть публичный профиль.
- Пользователь может загрузить картинку файлом и создать мем.
- Пользователь может вставить URL картинки и создать мем после завершения импорта.
- Пользователь может вставить `YouTube Shorts` URL и создать мем на основе внешнего media asset.
- Пользователь может создать private, groups или public мем.
- Пользователь может выбрать группы для `groups`-доступа.
- Пользователь может видеть свои private/groups/public мемы в своем профиле.
- Пользователь может создать подборку и добавить в нее мемы.
- Если подборка расширяет доступ мемов, пользователь видит предупреждение до сохранения.
- Пользователь может открыть экран мема и увидеть direct/effective access.
- Пользователь может открыть список своих групп и group feed.
- Пользователь может принять или отклонить инвайт.
- Пользователь может отправить жалобу на мем или подборку.
- При истекшем access token приложение корректно обновляет сессию через refresh.

## Порядок реализации

### Этап 1

- session bootstrap
- auth
- feed
- base navigation

### Этап 2

- public profile
- search
- my profile

### Этап 3

- media upload/import
- create meme
- meme details

### Этап 4

- collections
- warning about access expansion

### Этап 5

- groups
- invitations
- group feed

### Этап 6

- reports
- settings
- polish states and validation
