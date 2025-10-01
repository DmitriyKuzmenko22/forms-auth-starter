# Forms Auth Starter V3

## Быстрый старт

- Keycloak: http://localhost:8080 (admin/admin)
- API Swagger UI: http://localhost:8081/swagger-ui/index.html

### 1) Поднять инфраструктуру (с импортом realм-а)
```bash
docker compose down -v
docker compose up -d keycloak api_db

# дождаться готовности Keycloak
until curl -sf http://localhost:8080/realms/forms-realm/.well-known/openid-configuration >/dev/null; do 
  echo waiting for keycloak...; sleep 2; 
done
```

В `docker-compose.yml` Keycloak стартует с `--import-realm`, а realm-файл лежит в `keycloak/realms/forms-realm.json`.

### 2) Получить токен (service account клиента `curl-client`)
Service-account уже включён и клиент создан импортом. Токен берём по client_credentials:
```bash
TOKEN=$(curl -s -X POST -H 'Content-Type: application/x-www-form-urlencoded' \
  http://keycloak:8080/realms/forms-realm/protocol/openid-connect/token \
  -d 'grant_type=client_credentials' \
  -d 'client_id=curl-client' \
  -d 'client_secret=dev-curl-secret-123' | jq -r .access_token)

# опционально — проверить роли в токене
echo "$TOKEN" | jq -R 'split(".")[1] | @base64d | fromjson | .realm_access.roles'
```

Если в списке ролей нет `"user"` — назначь её сервис-аккаунту клиента (делается один раз):

```bash
# 2.1 Получить админ-токен (realm master)
ADMIN_TOKEN=$(curl -s -X POST 'http://localhost:8080/realms/master/protocol/openid-connect/token' \
  -d grant_type=password -d client_id=admin-cli \
  -d username=admin -d password=admin | jq -r .access_token)

# 2.2 Найти внутренний id клиента curl-client
CLIENT_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/admin/realms/forms-realm/clients?clientId=curl-client' | jq -r '.[0].id')

# 2.3 Получить id сервис-аккаунта этого клиента
SA_USER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/admin/realms/forms-realm/clients/$CLIENT_ID/service-account-user" | jq -r '.id')

# 2.4 Взять JSON описания роли user и назначить её сервис-аккаунту
ROLE_USER=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/admin/realms/forms-realm/roles/user')

curl -s -X POST "http://localhost:8080/admin/realms/forms-realm/users/$SA_USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "[$ROLE_USER]"

# 2.5 Взять новый токен и убедиться, что роль user присутствует
TOKEN=$(curl -s -X POST -H 'Content-Type: application/x-www-form-urlencoded' \
  http://keycloak:8080/realms/forms-realm/protocol/openid-connect/token \
  -d 'grant_type=client_credentials' \
  -d 'client_id=curl-client' \
  -d 'client_secret=dev-curl-secret-123' | jq -r .access_token)

echo "$TOKEN" | jq -R 'split(".")[1] | @base64d | fromjson | .realm_access.roles'
# вывод должен содержать: [ ..., "user", ... ]
```

Примечание: issuer API настроен на `http://keycloak:8080/realms/forms-realm`, поэтому токен берём у host `keycloak` (не `localhost`).

### 3) Запустить API и применить миграции
```bash
docker compose build auth_api
docker compose up -d auth_api
docker compose logs -f auth_api
```
Миграции Flyway создадут таблицы и сид `demo` форму (`V5__seed_demo_form.sql`).

### 4) Вызвать защищённый эндпоинт
Токены живут ~300 сек — получи новый и сразу вызови:
```bash
TOKEN=$(curl -s -X POST -H 'Content-Type: application/x-www-form-urlencoded' \
  http://keycloak:8080/realms/forms-realm/protocol/openid-connect/token \
  -d 'grant_type=client_credentials' \
  -d 'client_id=curl-client' \
  -d 'client_secret=dev-curl-secret-123' | jq -r .access_token)

curl -i -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:8081/submissions \
  -d '{"formCode":"demo"}'
```

Ожидаем: HTTP 200 и данные созданного черновика.

## Траблшутинг
* __401 invalid_token / issuer mismatch__: убедись, что переменная `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` у `auth_api` = `http://keycloak:8080/realms/forms-realm`, и токен получен от host `keycloak`.
* __401 expired__: токен просрочен, возьми новый.
* __500 Form not found__: убедись, что применена миграция `V5__seed_demo_form.sql` (пересобери/перезапусти `auth_api`).
* __JSONB ошибки__: поле `payload` у `FormSubmission` аннотировано `@JdbcTypeCode(SqlTypes.JSON)`; пересобери `auth_api`, если меняли зависимости.
