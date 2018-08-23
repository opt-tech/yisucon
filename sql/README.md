## 初期データ生成スクリプト

DBs, Tables 作成
```
mysql -uroot < schema.sql
```

データ投入
```
bundle install
bundle exec ruby seed.rb
```


## 確定データ

webapp/sql を利用する

optwitter データ投入
```
mysql -uroot -D optwitter < seed_optwitter.sql
```

optomo データ投入
```
mysql -uroot -D optomo < seed_optomo.sql
```

## データソース

- name.txt

https://en.wikipedia.org/wiki/Category:Japanese_masculine_given_names
