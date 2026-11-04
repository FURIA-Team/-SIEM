# MiniSIEM жүйесінің архитектурасы

## Жалпы сызба
MiniSIEM — монолитті серверлік веб-қосымша. Браузер сервермен HTTP арқылы байланысады; REST API сұраныстарды өңдейді, сервис қабаты бизнес-операцияларды орындайды, ал репозиторийлер Spring Data JPA және Hibernate арқылы SQLite дерекқорына жүгінеді.

## Логикалық қабаттар

| Қабат | Құрамдастар | Міндеті |
|---|---|---|
| Көрсету қабаты | HTML, CSS, JavaScript | Беттерді көрсету және API шақыру |
| Контроллерлер | AuthController, SecurityOperationsController, HomeController | HTTP маршруттары мен жауаптары |
| DTO | LoginRequest, RegisterRequest, EventRequest және басқалары | Кіріс және шығыс деректерінің пішімі |
| Сервистер | AuthService, SecurityOperationsService | Бизнес-логика |
| Репозиторийлер | UserRepository, SecurityEventRepository және басқалары | JPA арқылы деректерге қол жеткізу |
| Нысандар | User, SecurityEvent, SecurityAlert, SystemLog, SystemSettings | Деректерді сақтау моделі |
| Қауіпсіздік | SecurityConfig | Сессиялар, CSRF, аутентификация және авторизация |
| Дерекқор | SQLite | Деректерді тұрақты сақтау |

## REST API

### Аутентификация
- GET `/api/auth/csrf`
- POST `/api/auth/register`
- POST `/api/auth/login`
- POST `/api/auth/logout`
- POST `/api/auth/bootstrap-admin`
- GET `/api/auth/me`

### Пайдаланушы операциялары (USER және ADMIN)
- GET `/api/user/dashboard`
- GET `/api/user/events`
- GET `/api/user/events/export`
- POST `/api/user/events`
- GET `/api/user/alerts`
- GET `/api/user/alerts/{id}`
- PATCH `/api/user/alerts/{id}`
- GET `/api/user/logs`

### Әкімшілік операциялар (ADMIN)
- GET, POST `/api/admin/users`
- DELETE `/api/admin/users/{id}`
- GET, PUT `/api/admin/settings`

## Оқиғаны өңдеу
1. Клиент міндетті `title`, `severity`, `source`, `destination` өрістері бар POST `/api/user/events` сұранысын жібереді.
2. Spring Security сессияны, рөлді және CSRF токенін тексереді.
3. Контроллер сұранысты тексеріп, `SecurityOperationsService` сервисіне береді.
4. Сервис `SecurityEvent` нысанын сақтайды.
5. `HIGH` немесе `CRITICAL` деңгейлері үшін `SecurityAlert` жасалады.
6. Клиент операция нәтижесі бар DTO алады.

## Деректерді сақтау моделі
`users`, `security_events`, `security_alerts`, `system_logs`, `system_settings` кестелері қолданылады. Ескерту оқиғамен бірегей `event_id` өрісі арқылы байланысады. Кодта бұл өріс идентификатор болып табылады, JPA ORM байланысы емес.

## Шектеулер
- Оқиғалар REST API арқылы қабылданады; соңғы құрылғылардан агенттермен жинау расталмаған.
- Ескерту ережесі күрделі корреляцияға емес, қауіптілік деңгейіне негізделген.
- `retentionDays` баптау ретінде сақталады; ескі жазбаларды автоматты жою расталмаған.
- Оқу прототипінің жергілікті дерекқоры ретінде SQLite қолданылады.
