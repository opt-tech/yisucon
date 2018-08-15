# Provisioning

```bash
$ git clone https://github.com/yahoojapan/yisucon.git
$ cd yisucon/provisioning
$ sudo ./provision.sh development isucon
```

## :memo: note

競技用インスタンスのセットアップ。まず CentOS 7.2 のインスタンスを起動して

```
$ git clone https://github.com/yahoojapan/yisucon.git
$ cd yisucon/provisioning
$ sudo ./provision.sh development isucon
```

これで 80番 にアクセスしたら node のアプリケーションが起動しています  
他の言語に切り替える場合は systemctl からサービスを停止・開始してください

```
$ sudo systemctl stop isucon-node
$ sudo systemctl start isucon-go-optomo
$ sudo systemctl start isucon-go-optwitter
```

:memo: `go` と `ruby`, `java` では二種類のサービスを起動します

```bash
$ systemctl list-unit-files | grep isucon
isucon-go-optomo.service                 disabled
isucon-go-optwitter.service               disabled
isucon-java-optomo.service               disabled
isucon-java-optwitter.service             disabled
isucon-node.service                       enabled
isucon-php.service                        disabled
isucon-ruby-optomo.service               disabled
isucon-ruby-optwitter.service             disabled
```

:memo: `php` では `nginx` の設定を切り替えてください

```bash
$ sudo systemctl start isucon-php
$ sudo cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.bak
$ sudo cp /etc/nginx/php-fpm.conf /etc/nginx/nginx.conf
$ sudo nginx -t -c /etc/nginx/nginx.conf
$ sudo systemctl restart nginx
```

その他、起動している関連システム

- nginx
- mariadb

## Benchmarker

```bash
$ git clone https://github.com/yahoojapan/yisucon.git
$ cd yisucon/provisioning

# 適宜環境変数を設定してください
$ vim environments/development.json

$ sudo ./provision.sh development benchmarker
```

## Portal

```bash
$ git clone https://github.com/yahoojapan/yisucon.git
$ cd yisucon/provisioning

# 適宜環境変数を設定してください
$ vim environments/development.json

$ sudo ./provision.sh development portal
```

## :memo: note

ポータルとベンチマーカから利用する共通データベースの構築処理は provisioning に含まれていません  
下記の手順を参考に適宜構築してください

:memo: 同一ホストにポータルとベンチマーカを構築する

```bash
$ git clone https://github.com/yahoojapan/yisucon.git
$ cd yisucon/provisioning

$ vim environments/development.json
{
  "json_class": "Chef::Environment",
  "default_attributes": {
    "env": "development",
    "start_date": "2017/01/01 00:00:00",
    "end_date": "2017/01/01 23:59:59",
    "portal_host": "127.0.0.1",
    "db_host": "127.0.0.1",
    "db_port": 3306,
    "db_user": "root",
    "db_pass": "",
    "db_name": "isucon",
    "secret_key": "GENERATE SECRET KEY"
  }
}

$ sudo yum install mariadb mariadb-server
$ sudo systemctl start mariadb
$ sudo systemctl enable mariadb

$ mysql -u root < ../benchmarker/init.sql

$ sudo ./provision.sh development portal
$ sudo ./provision.sh development benchmarker
```

:memo: 開始時刻と終了時刻を設定する

基本的には環境変数で開始時刻と終了時刻を設定します。
環境変数がない場合、もしくは形式として正しくない場合は、プロビジョニング時に現在時刻を開始時刻とし、一週間後を終了時刻とします。
