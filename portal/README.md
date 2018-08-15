# Y!SUCON ポータル
## インストール
```
npm install -g yarn
yarn install
```

環境変数に以下の値を入れること
```
OPT_ISUCON_DB_HOST
OPT_ISUCON_DB_USER
OPT_ISUCON_DB_PASSWORD
OPT_ISUCON_DB_PORT
OPT_ISUCON_DB_NAME
OPT_ISUCON_SECRET_KEY
MYM_USER
MYM_PASS
```

## 開発時
```
npm run watch:dev
```

## 本番実行
```
# プロセス永続化
npm run start:prod

# 永続化プロセスの終了
npm run stop:prod
```

Angular2のAOTビルドはまだ使えません
