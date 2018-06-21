require 'mysql2-cs-bind'
require 'digest'
require './tweet'

def db
  Thread.current[:yj_isucon_db] ||= Mysql2::Client.new(
    host: ENV['YJ_ISUCON_DB_HOST'] || 'localhost',
    port: ENV['YJ_ISUCON_DB_PORT'] ? ENV['YJ_ISUCON_DB_PORT'].to_i : 3306,
    username: ENV['YJ_ISUCON_DB_USER'] || 'root',
    password: ENV['YJ_ISUCON_DB_PASSWORD'],
    reconnect: true,
  )
end

def register(name, pw)
  chars = [*'A'..'~']
  salt = (1..20).map{ chars.sample }.join('')
  salted_password = encode_with_salt(pw, salt)
  db.xquery(%|
    INSERT INTO isuwitter.users (name, salt, password)
    VALUES (?, ?, ?)
  |, name, salt, salted_password)
  db.last_id
end

def encode_with_salt(password, salt)
  Digest::SHA1.hexdigest(salt + password)
end

# data should be array of some [user_id, text]
def bulk_tweet(data)
  value_string = ' (?, ?, ?) '
  values = ([value_string] * data.size).join(',')
  db.xquery(%|
    INSERT INTO isuwitter.tweets (user_id, text, created_at) VALUES
  | + values, data.flatten)
  db.last_id
end

def relation(user, friends)
  db.xquery(%|
    INSERT INTO isutomo.friends (me, friends)
    VALUES (?, ?)
  |, user, friends.join(','))
  db.last_id
end

def random_date
  from = Time.mktime(2013, 4, 1)
  to = Time.now
  Time.at(from + rand * (to.to_f - from.to_f)).strftime("%F %T")
end

users = File.read('./name.txt').strip.downcase.split("\n")

# seed data
db.query('TRUNCATE isuwitter.users')
db.query('TRUNCATE isuwitter.tweets')
db.query('TRUNCATE isutomo.friends')

min_friend = users.size / 2 - 1
max_friend = users.size - 1
tweets = []
users.each do |user|
  # caesar cipher
  pass = user.split('').map{ |c| c == 'z' ? 'a' : c.next }.join('')
  id = register(user, pass)
  100.times do
    tweets.push [id, random_tweet, random_date]
  end
  # 500-1000 friends including himself
  friends = users.shuffle[0..rand(min_friend..max_friend)].unshift(user).uniq
  relation(user, friends)
end

tweets.sort_by{|t| t[2]}.each_slice(100) do |ts|
  bulk_tweet ts
end
