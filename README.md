# oracle-helper
Small app for sql scripts executing

# Example of usage

## Create database

```
POST /create-db
```

```json
{
  "databaseName": "test123123123"
}
```

## Drop database

```
POST /drop-db
```

```json
{
  "databaseName": "test123123123"
}
```

## Check service health

```
GET /health
```

## ENV

| Key | Default value | 
| -------- | -------- | 
| PORT | 8080 |
| APP_USERNAME | admin | 
| APP_PASSWORD | admin | 
| HEALTH_CHECK | 30 | 
| RENEW | 600 | 
| DB_URL | jdbc:oracle:thin:@localhost:1521:XE | 
| DB_USERNAME | scott | 
| DB_PASSWORD | tiger | 
| DB_DRIVER | oracle.jdbc.driver.OracleDriver | 
| SCRIPT_PATH | /scripts | 
