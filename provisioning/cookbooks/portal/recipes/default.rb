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
NODE_ENV=#{node["env"]}
START_DATE=#{node["start_date"]}
END_DATE=#{node["end_date"]}
PORT=80
YJ_ISUCON_DB_HOST=#{node["db_host"]}
YJ_ISUCON_DB_PORT=#{node["db_port"]}
YJ_ISUCON_DB_USER=#{node["db_user"]}
YJ_ISUCON_DB_PASSWORD=#{node["db_pass"]}
YJ_ISUCON_DB_NAME=#{node["db_name"]}
YJ_ISUCON_SECRET_KEY=#{node["secret_key"]}
  END
end


## node.js

directory "/usr/local/src/nodejs" do
  owner "root"
  group "root"
  mode 0755
  action :create
end

nodejs_version = "8.11.3"

remote_file "/usr/local/src/nodejs/node-v#{nodejs_version}-linux-x64.tar.gz" do
  source "https://nodejs.org/dist/v#{nodejs_version}/node-v#{nodejs_version}-linux-x64.tar.gz"
  action :create_if_missing
end

bash "install-nodejs" do
  user "root"
  cwd "/usr/local/src/nodejs"
  code <<-"END"
set -exu -o pipefail
tar -C /usr/local --strip-components 1 -xzf node-v#{nodejs_version}-linux-x64.tar.gz
touch /usr/local/src/nodejs/nodejs-install.done
  END
  not_if { ::File.exists?("/usr/local/src/nodejs/nodejs-install.done") }
end


## portal

package "make"
package "gcc-c++"

bash "install-portal" do
  user "root"
  cwd ::File.dirname(File.dirname(File.dirname(Chef::Config[:file_cache_path])))
  code <<-"END"
set -exu -o pipefail

mkdir -p /var/www/portal
rsync -av --delete portal/ /var/www/portal/

cd /var/www/portal
npm install
NODE_ENV=#{node["env"]} npm run build:prod:ngc
  END
end

file "/etc/systemd/system/portal.service" do
  owner "root"
  group "root"
  mode 0755
  content <<-"END"
[Unit]
Description=yahoo japan isucon portal server

[Service]
Type=simple
EnvironmentFile=/etc/environment
WorkingDirectory=/var/www/portal
ExecStart=/usr/local/bin/node /var/www/portal/dist/server/index.js
Restart=always
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
  END
  notifies :run, "execute[systemctl-daemon-reload]", :immediately
  notifies :restart, "service[portal]", :delayed
end

service "portal" do
  supports :start => true, :status => true, :restart => true
  action [:enable, :start]
end
