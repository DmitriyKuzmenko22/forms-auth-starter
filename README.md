# Forms Auth Starter V3

## Быстрый старт (с UI регистрации/логина)

- **Запуск**: `docker compose up --build`
- **UI**: `http://localhost:8081/index.html`
- **Keycloak админка**: `http://localhost:8080` (admin/admin)

### Что запускается:
- Keycloak (IAM) с импортом realm `forms-realm`.
- PostgreSQL базы: `kc_db` (Keycloak), `api_db` (API).
- API с UI для регистрации/логина и личным кабинетом.
- API логирует шаги аутентификации.

### Тестирование:
1. Открой `http://localhost:8081/index.html`.
2. Зарегистрируйся (email/пароль/имя).
3. Войди — перенаправишься в личный кабинет с email/ролями.
4. Логи: `docker compose logs -f auth_api`.

### Остановка:
`docker compose down`

## Аутентификация пользователей (регистрация и логин)

Проект включает UI для пользовательской регистрации и логина, интегрированный с Keycloak.

### Общая логика
- **Keycloak** — сервер аутентификации, хранит пользователей, пароли (hash), роли, выдаёт JWT-токены.
- **API** — бизнес-приложение, делегирует аутентификацию Keycloak, хранит ссылки на пользователей и бизнес-данные.
- **Интеграция** — API общается с Keycloak через REST API (создание пользователей, получение токенов), токены валидируются локально.

### Что хранится в базах
- **`kc_db` (Keycloak PostgreSQL)**: Полные данные пользователей (id, username=email, hash пароля, enabled, emailVerified, requiredActions, роли realm/client).
- **`api_db` (наша PostgreSQL)**: Минимальные данные (user_account: id, keycloak_user_id, email, full_name) + бизнес-данные (формы, черновики).

### Регистрация
1. Пользователь заходит на `http://localhost:8081/index.html` → `register.html`.
2. Вводит email, пароль (мин. 6 символов), имя (опционально).
3. JS отправляет POST `/public/api/register` с JSON.
4. API проверяет email на уникальность в `api_db`.
5. API получает админ-токен Keycloak (авторизуется как админ).
6. API создаёт пользователя в Keycloak (POST `/admin/realms/forms-realm/users` с email, username, password, enabled=true, emailVerified=true, requiredActions=[], roles=["user"]).
7. API сохраняет в `api_db` запись с keycloak_user_id + email + full_name.
8. Возвращает успех; пользователь может логиниться.

### Логин
1. Пользователь заходит на `http://localhost:8081/login.html`.
2. Вводит email/пароль.
3. JS отправляет POST `/public/api/login` с JSON.
4. API отправляет запрос в Keycloak token endpoint (POST `/realms/forms-realm/protocol/openid-connect/token` с grant_type=password, client_id=forms-spa, username=email, password).
5. Keycloak проверяет credentials, выдаёт JWT (access_token, expires_in, refresh_token).
6. API возвращает JWT клиенту.
7. JS сохраняет токены в localStorage, редирект на `/dashboard.html`.

### Личный кабинет
1. `/dashboard.html` загружается, JS вызывает GET `/me` с `Authorization: Bearer <token>`.
2. **Spring Security автоматически валидирует JWT** (подпись по JWK, срок действия, issuer=http://keycloak:8080/realms/forms-realm).
3. Если токен валиден — метод `me(@AuthenticationPrincipal Jwt jwt)` получает объект Jwt с claims.
4. API извлекает email, roles из токена (не запрашивает Keycloak).
5. Возвращает JSON: `{"sub": "<user_id>", "email": "...", "roles": {"roles": ["user", ...]}}`.
6. JS показывает приветствие и роли.

**Важно: проверки токена делает Spring Security перед методом `me`. Если токен невалиден/истёк — возвращается 401, метод не вызывается.**

### Запуск UI
```bash
docker compose down -v
docker compose up --build
```
- Keycloak: http://localhost:8080 (admin/admin)
- UI: http://localhost:8081/index.html
- Зарегистрируйтесь, войдите, проверьте личный кабинет.

### Логи
API логирует все шаги (входные данные, вызовы Keycloak, сохранение в БД):
```bash
docker compose logs -f auth_api
```

## Траблшутинг
* __401 invalid_token / issuer mismatch__: убедись, что переменная `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` у `auth_api` = `http://keycloak:8080/realms/forms-realm`, и токен получен от host `keycloak`.
* __401 expired__: токен просрочен, возьми новый.
* __500 Form not found__: убедись, что применена миграция `V5__seed_demo_form.sql` (пересобери/перезапусти `auth_api`).
* __JSONB ошибки__: поле `payload` у `FormSubmission` аннотировано `@JdbcTypeCode(SqlTypes.JSON)`; пересобери `auth_api`, если меняли зависимости.
* __Регистрация/логин: Connection refused__: проверь, что Keycloak запущен и доступен по `http://keycloak:8080` (изнутри контейнера).
* __Регистрация: Account is not fully set up__: realm не переимпортирован, requiredActions не отключены — пересоздай с `docker compose down -v`.
* __Логин: invalid_grant__: проверь пароль, email, или requiredActions в Keycloak (должен быть пустой массив).
* __/me: 401__: токен истёк или невалиден — проверь issuer и подпись.
