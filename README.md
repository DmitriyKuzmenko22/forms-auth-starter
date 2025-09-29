# Forms Auth Starter V3

### Запуск
```bash
docker compose up -d --build
./init-realm.sh
cd auth-api && ./gradlew bootRun   # если wrapper не работает у вас локально — gradle bootRun
```
Keycloak: http://localhost:8080 (admin/admin)  
Swagger: http://localhost:8081/swagger-ui/index.html

### Проверка
1) Токен:
```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/forms-realm/protocol/openid-connect/token"   -d grant_type=password -d client_id=forms-spa   -d username=test -d password=pass1234 | jq -r .access_token)
```
2) Эндпоинты:
```bash
curl -s http://localhost:8081/public/ping
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/me | jq .
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/profile | jq .

# Формы
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"code":"tax_return","title":"Налоговый возврат","version":"1.0","jsonSchema":"{\"type\":\"object\"}"}'   http://localhost:8081/forms/upsert | jq .

# Маппинг
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"formCode":"tax_return","fieldPath":"applicantName","source":"profile.fullName"}'   http://localhost:8081/mapping/upsert | jq .

# Черновик → автозаполнение → отправка
SUB=$(curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"formCode":"tax_return"}' http://localhost:8081/submissions | jq -r .id)
curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8081/submissions/$SUB/autofill | jq .
curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8081/submissions/$SUB/submit | jq .
```
