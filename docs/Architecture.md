# Архитектура MiniSIEM

## Общая схема
MiniSIEM — монолитное серверное веб-приложение. Браузер взаимодействует с сервером по HTTP; REST API обрабатывает запросы, сервисный слой выполняет бизнес-операции, репозитории обращаются к SQLite через Spring Data JPA и Hibernate.

## Логические слои

| Слой | Компоненты | Ответственность |
|---|---|---|
| Представление | HTML, CSS, JavaScript | Отображение страниц и вызов API |
| Контроллеры | AuthController, SecurityOperationsController, HomeController | HTTP-маршруты и ответы |
| DTO | LoginRequest, RegisterRequest, EventRequest и другие | Формат входных и выходных данных |
| Сервисы | AuthService, SecurityOperationsService | Бизнес-логика |
| Репозитории | UserRepository, SecurityEventRepository и другие | Доступ к данным через JPA |
| Сущности | User, SecurityEvent, SecurityAlert, SystemLog, SystemSettings | Модель хранения |
| Безопасность | SecurityConfig | Сессии, CSRF, аутентификация и авторизация |
| Хранилище | SQLite | Постоянное хранение |

## REST API

### Аутентификация
- GET `/api/auth/csrf`
- POST `/api/auth/register`
- POST `/api/auth/login`
- POST `/api/auth/logout`
- POST `/api/auth/bootstrap-admin`
- GET `/api/auth/me`

### Пользовательские операции (USER и ADMIN)
- GET `/api/user/dashboard`
- GET `/api/user/events`
- GET `/api/user/events/export`
- POST `/api/user/events`
- GET `/api/user/alerts`
- GET `/api/user/alerts/{id}`
- PATCH `/api/user/alerts/{id}`
- GET `/api/user/logs`

### Административные операции (ADMIN)
- GET, POST `/api/admin/users`
- DELETE `/api/admin/users/{id}`
- GET, PUT `/api/admin/settings`

## Обработка события
1. Клиент отправляет POST `/api/user/events` с обязательными полями `title`, `severity`, `source`, `destination`.
2. Spring Security проверяет сессию, роль и CSRF-токен.
3. Контроллер валидирует запрос и передаёт его в `SecurityOperationsService`.
4. Сервис сохраняет `SecurityEvent`.
5. Для `HIGH` или `CRITICAL` создаётся `SecurityAlert`.
6. Клиент получает DTO с результатом операции.

## Модель хранения
Используются таблицы `users`, `security_events`, `security_alerts`, `system_logs`, `system_settings`. Предупреждение ссылается на событие через уникальное поле `event_id`. В коде это поле-идентификатор, а не ORM-связь JPA.

## Ограничения
- События поступают через REST API; сбор с конечных устройств агентами не подтверждён.
- Правило предупреждения основано на критичности, а не на сложной корреляции.
- `retentionDays` хранится как настройка; автоматическое удаление старых записей не подтверждено.
- SQLite используется как локальное хранилище учебного прототипа.
