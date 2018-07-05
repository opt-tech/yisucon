#!/usr/bin/env ruby


## systemd

execute "systemctl-daemon-reload" do
  command "systemctl daemon-reload"
  action :nothing
end


## mysql

package "mariadb"
package "mariadb-server"

service "mariadb" do
  supports :start => true, :status => true, :restart => true
  action [:enable, :start]
end

bash "init-db" do
  user "root"
  cwd ::File.dirname(File.dirname(File.dirname(Chef::Config[:file_cache_path])))
  code <<-"END"
set -ex
cd sql
mysql -uroot < schema.sql
cd ../webapp/sql
mysql -uroot -D isuwitter < seed_isuwitter.sql
mysql -uroot -D isutomo < seed_isutomo.sql
touch /etc/.initialized
  END
  not_if { ::File.exists?("/etc/.initialized") }
end


## nginx

package "pcre-devel"

directory "/usr/local/src/nginx" do
  owner "root"
  group "root"
  mode 0755
  action :create
end

nginx_version = "1.11.7-1"

remote_file "/usr/local/src/nginx/nginx-#{nginx_version}.el7.ngx.x86_64.rpm" do
  source "http://nginx.org/packages/mainline/rhel/7/x86_64/RPMS/nginx-#{nginx_version}.el7.ngx.x86_64.rpm"
  action :create_if_missing
end

package "nginx" do
  source "/usr/local/src/nginx/nginx-#{nginx_version}.el7.ngx.x86_64.rpm"
  provider Chef::Provider::Package::Rpm
  action :install
end

file "/etc/nginx/nginx.conf" do
  owner "root"
  group "root"
  content <<-'END'
user nobody;
worker_processes 1;

events {
  worker_connections 1024;
}

http {
  include mime.types;
  default_type application/octet-stream;
  sendfile on;
  keepalive_timeout 65;

  server {
    listen 80;
    root /var/www/webapp/public;

    location / {
      proxy_set_header Host $host;
      proxy_pass http://localhost:8080;
    }
  }
}
  END
  notifies :restart, "service[nginx]", :immediately
end

file "/etc/nginx/php-fpm.conf" do
  owner "root"
  group "root"
  content <<-'END'
user nobody;
worker_processes 1;

events {
  worker_connections 1024;
}

http {
  include mime.types;
  default_type application/octet-stream;
  sendfile on;
  keepalive_timeout 65;

  upstream php-fpm {
    server localhost:8080;
  }

  server {
    location ~ ^/(css|fonts|js) {
      root /var/www/webapp/public;
    }

    location / {
      root /var/www/webapp/php;

      fastcgi_pass php-fpm;
      fastcgi_index index.php;
      fastcgi_read_timeout 120;

      fastcgi_param  SCRIPT_FILENAME    $document_root$fastcgi_script_name;
      fastcgi_param  QUERY_STRING       $query_string;
      fastcgi_param  REQUEST_METHOD     $request_method;
      fastcgi_param  CONTENT_TYPE       $content_type;
      fastcgi_param  CONTENT_LENGTH     $content_length;

      fastcgi_param  SCRIPT_NAME        $fastcgi_script_name;
      fastcgi_param  REQUEST_URI        $request_uri;
      fastcgi_param  DOCUMENT_URI       $document_uri;
      fastcgi_param  DOCUMENT_ROOT      $document_root;
      fastcgi_param  SERVER_PROTOCOL    $server_protocol;
      fastcgi_param  HTTPS              $https if_not_empty;

      fastcgi_param  GATEWAY_INTERFACE  CGI/1.1;
      fastcgi_param  SERVER_SOFTWARE    nginx/$nginx_version;

      fastcgi_param  REMOTE_ADDR        $http_x_forwarded_for;
      fastcgi_param  REMOTE_PORT        $remote_port;
      fastcgi_param  SERVER_ADDR        $server_addr;
      fastcgi_param  SERVER_PORT        $server_port;
      fastcgi_param  SERVER_NAME        $server_name;

      fastcgi_param  REDIRECT_STATUS    200;

      rewrite ^(.*)$ /isuwitter.php?$1 break;
    }
  }

  server {
    listen 8081;
    location ~ ^/(css|fonts|js) {
      root /var/www/webapp/public;
    }

    location / {
      root /var/www/webapp/php;

      fastcgi_pass php-fpm;
      fastcgi_index index.php;
      fastcgi_read_timeout 120;

      fastcgi_param  SCRIPT_FILENAME    $document_root$fastcgi_script_name;
      fastcgi_param  QUERY_STRING       $query_string;
      fastcgi_param  REQUEST_METHOD     $request_method;
      fastcgi_param  CONTENT_TYPE       $content_type;
      fastcgi_param  CONTENT_LENGTH     $content_length;

      fastcgi_param  SCRIPT_NAME        $fastcgi_script_name;
      fastcgi_param  REQUEST_URI        $request_uri;
      fastcgi_param  DOCUMENT_URI       $document_uri;
      fastcgi_param  DOCUMENT_ROOT      $document_root;
      fastcgi_param  SERVER_PROTOCOL    $server_protocol;
      fastcgi_param  HTTPS              $https if_not_empty;

      fastcgi_param  GATEWAY_INTERFACE  CGI/1.1;
      fastcgi_param  SERVER_SOFTWARE    nginx/$nginx_version;

      fastcgi_param  REMOTE_ADDR        $http_x_forwarded_for;
      fastcgi_param  REMOTE_PORT        $remote_port;
      fastcgi_param  SERVER_ADDR        $server_addr;
      fastcgi_param  SERVER_PORT        $server_port;
      fastcgi_param  SERVER_NAME        $server_name;

      fastcgi_param  REDIRECT_STATUS    200;

      rewrite ^(.*)$ /isutomo.php?$1 break;
    }
  }
}
  END
end

service "nginx" do
  supports :status => true, :restart => true, :reload => true
  action [:enable, :start]
end


## node.js

directory "/usr/local/src/nodejs" do
  owner "root"
  group "root"
  mode 0755
  action :create
end

nodejs_version = "6.9.2"

remote_file "/usr/local/src/nodejs/node-v#{nodejs_version}-linux-x64.tar.gz" do
  source "https://nodejs.org/dist/v#{nodejs_version}/node-v#{nodejs_version}-linux-x64.tar.gz"
  action :create_if_missing
end

bash "install-nodejs" do
  user "root"
  cwd "/usr/local/src/nodejs"
  code <<-"END"
set -ex
tar -C /usr/local --strip-components 1 -xzf node-v#{nodejs_version}-linux-x64.tar.gz
  END
end


## golang

directory "/usr/local/src/golang" do
  owner "root"
  group "root"
  mode 0755
  action :create
end

golang_version = "1.7.4"

remote_file "/usr/local/src/golang/go#{golang_version}.linux-amd64.tar.gz" do
  source "https://storage.googleapis.com/golang/go#{golang_version}.linux-amd64.tar.gz"
  action :create_if_missing
end

bash "install-golang" do
  user "root"
  cwd "/usr/local/src/golang"
  code <<-"END"
set -ex
tar -C /usr/local -xzf go#{golang_version}.linux-amd64.tar.gz
ln -sfn /usr/local/go/bin/go /usr/local/bin/go
ln -sfn /usr/local/go/bin/gofmt /usr/local/bin/gofmt
ln -sfn /usr/local/go/bin/godoc /usr/local/bin/godoc
  END
end

directory "/usr/local/src/glide" do
  owner "root"
  group "root"
  mode 0755
  action :create
end

glide_version = "0.12.2"

remote_file "/usr/local/src/glide/glide-#{glide_version}.tar.gz" do
  source "https://github.com/Masterminds/glide/releases/download/v#{glide_version}/glide-v#{glide_version}-linux-amd64.tar.gz"
  action :create_if_missing
end

bash "install-glide" do
  user "root"
  cwd "/usr/local/src/glide"
  code <<-"END"
set -ex
tar -C /usr/local/bin --strip-components 1 -xzf glide-#{glide_version}.tar.gz
  END
end


## ruby

package "ruby-devel"
package "mariadb-devel"


## php

package "autoconf"
package "openssl-devel"
package "bison"
package "libxml2-devel"

directory "/usr/local/src/php" do
  owner "root"
  group "root"
  mode 0755
  action :create
end

php_version = "7.1.0"

remote_file "/usr/local/src/php/php-#{php_version}.tar.gz" do
  source "https://github.com/php/php-src/archive/php-#{php_version}.tar.gz"
  action :create_if_missing
end

bash "install-php" do
  user "root"
  cwd "/usr/local/src/php"
  code <<-"END"
set -ex

tar zxvf php-#{php_version}.tar.gz
cd php-src-php-#{php_version}
./buildconf --force
./configure \
  --enable-fpm \
  --enable-mbstring \
  --without-mcrypt \
  --with-openssl \
  --enable-mysqlnd \
  --with-mysql-sock=/var/lib/mysql/mysql.sock \
  --with-mysqli=mysqlnd \
  --with-pdo-mysql=mysqlnd
make -j2
make install
touch /usr/local/src/php/php-install.done
  END
  not_if { ::File.exists?("/usr/local/src/php/php-install.done") }
end

## java

java_version = "1.8.0"

package "java-#{java_version}-openjdk"
package "java-#{java_version}-openjdk-devel"

## webapp

bash "install-webapp" do
  user "root"
  cwd ::File.dirname(File.dirname(File.dirname(Chef::Config[:file_cache_path])))
  code <<-"END"
set -ex
mkdir -p /var/www
chown root:root /var/www
rsync -av --delete webapp/ /var/www/webapp/

## nodejs
cd /var/www/webapp/node
npm install --production

## golang
cd /var/www/webapp
export GOPATH=/var/www/webapp
ln -sfv go src
cd /var/www/webapp/go/isutomo
glide install
go build
cd /var/www/webapp/go/isuwitter
glide install
go build

## ruby
cd /var/www/webapp/ruby
gem install bundler
bundle install

## php
cd /var/www/webapp/php
/usr/local/bin/php composer.phar install

## java
cd /var/www/webapp/java/isutomo
./mvnw clean package
cd /var/www/webapp/java/isuwitter
./mvnw clean package

chown -R root:root /var/www/webapp
  END
end

file "/etc/systemd/system/isucon-node.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-node

[Service]
Type=simple
User=centos
Group=centos
WorkingDirectory=/var/www/webapp/node
ExecStart=/usr/local/bin/npm start

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-node" do
  supports :start => true, :status => true, :restart => true
  action [:enable, :start]
end

file "/etc/systemd/system/isucon-go-isutomo.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-go-isutomo

[Service]
Type=simple
User=centos
Group=centos
WorkingDirectory=/var/www/webapp/go/isutomo
ExecStart=/var/www/webapp/go/isutomo/isutomo

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-go-isutomo" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end

file "/etc/systemd/system/isucon-go-isuwitter.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-go-isuwitter

[Service]
Type=simple
User=centos
Group=centos
WorkingDirectory=/var/www/webapp/go/isuwitter
ExecStart=/var/www/webapp/go/isuwitter/isuwitter

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-go-isuwitter" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end

file "/etc/systemd/system/isucon-ruby-isutomo.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-ruby-isutomo

[Service]
Type=simple
User=centos
Group=centos
WorkingDirectory=/var/www/webapp/ruby
ExecStart=/home/centos/bin/unicorn -c unicorn_isutomo.rb isutomo.ru

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-ruby-isutomo" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end

file "/etc/systemd/system/isucon-ruby-isuwitter.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-ruby-isuwitter

[Service]
Type=simple
User=centos
Group=centos
WorkingDirectory=/var/www/webapp/ruby
ExecStart=/home/centos/bin/unicorn -c unicorn_isuwitter.rb isuwitter.ru

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-ruby-isuwitter" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end

file "/etc/systemd/system/isucon-php.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-php

[Service]
Type=simple
User=root
Group=root
WorkingDirectory=/var/www/webapp/php
ExecStart=/usr/local/sbin/php-fpm --fpm-config /var/www/webapp/php/php-fpm.conf

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-php" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end

file "/etc/systemd/system/isucon-java-isutomo.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-java-isutomo

[Service]
Type=simple
User=root
Group=root
WorkingDirectory=/var/www/webapp/java/isutomo
Environment="_JAVA_OPTIONS=-Djava.security.egd=file:/dev/urandom"
ExecStart=/usr/bin/java -jar target/isutomo-0.0.1-SNAPSHOT.jar

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-java-isutomo" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end

file "/etc/systemd/system/isucon-java-isuwitter.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=isucon-java-isuwitter

[Service]
Type=simple
User=root
Group=root
Environment="_JAVA_OPTIONS=-Djava.security.egd=file:/dev/urandom"
WorkingDirectory=/var/www/webapp/java/isuwitter
ExecStart=/usr/bin/java -jar target/isuwitter-0.0.1-SNAPSHOT.jar

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
end

service "isucon-java-isuwitter" do
  supports :start => true, :status => true, :restart => true
  # action [:enable, :start]
end
