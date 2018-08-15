#!/usr/bin/env ruby


## systemd

execute "systemctl-daemon-reload" do
  command "systemctl daemon-reload"
  action :nothing
end


## limits

file "/etc/security/limits.d/99-limits-chef.conf" do
  owner "root"
  group "root"
  mode 0644
  content <<-'END'
* - nofile 65535
  END
end


## env

file "/etc/environment" do
  owner "root"
  group "root"
  mode 0644
  content <<-"END"
OPT_ISUCON_PORTAL_HOST=#{node["portal_host"]}
OPT_ISUCON_DB_HOST=#{node["db_host"]}
OPT_ISUCON_DB_PORT=#{node["db_port"]}
OPT_ISUCON_DB_USER=#{node["db_user"]}
OPT_ISUCON_DB_PASSWORD=#{node["db_pass"]}
OPT_ISUCON_DB_NAME=#{node["db_name"]}
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
set -exu -o pipefail

tar -C /usr/local -xzf go#{golang_version}.linux-amd64.tar.gz

ln -sfn /usr/local/go/bin/go /usr/local/bin/go
ln -sfn /usr/local/go/bin/gofmt /usr/local/bin/gofmt
ln -sfn /usr/local/go/bin/godoc /usr/local/bin/godoc

touch /usr/local/src/golang/golang-install.done
  END
  not_if { ::File.exists?("/usr/local/src/golang/golang-install.done") }
end


## glide

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
set -exu -o pipefail
tar -C /usr/local/bin --strip-components 1 -xzf glide-#{glide_version}.tar.gz
touch /usr/local/src/glide/glide-install.done
  END
  not_if { ::File.exists?("/usr/local/src/glide/glide-install.done") }
end


## benchmarker

bash "install-benchmarker" do
  user "root"
  cwd ::File.dirname(File.dirname(File.dirname(Chef::Config[:file_cache_path])))
  code <<-"END"
set -exu -o pipefail

export GOPATH=/tmp/gopath
mkdir -p $GOPATH/src/github.com/yahoojapan/yisucon
cp -r benchmarker $GOPATH/src/github.com/yahoojapan/yisucon
cd $GOPATH/src/github.com/yahoojapan/yisucon/benchmarker

glide install
go build
install -o root -g root -m 0655 benchmarker /usr/local/bin/benchmarker
  END
end

file "/etc/systemd/system/benchmarker.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=yahoo japan isucon benchmarker

[Service]
Type=simple
Nice=-20
EnvironmentFile=/etc/environment
ExecStart=/usr/local/bin/benchmarker
Restart=always
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
  notifies :restart, "service[benchmarker]", :delayed
end

service "benchmarker" do
  supports :start => true, :status => true, :restart => true
  action [:enable, :start]
end
