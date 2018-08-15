require 'digest/sha1'
require 'json'
require 'net/http'

require 'sinatra/base'
require 'sinatra/json'
require 'mysql2-cs-bind'

module Optwitter
  class WebApp < Sinatra::Base
    use Rack::Session::Cookie, key: 'isu_session', secret: 'kioicho'
    set :public_folder, File.expand_path('../../public', __FILE__)

    PERPAGE = 50
    OPTOMO_ENDPOINT = 'http://localhost:8081'

    helpers do
      def db
        Thread.current[:optwitter_db] ||= Mysql2::Client.new(
          host: ENV['OPT_ISUCON_DB_HOST'] || 'localhost',
          port: ENV['OPT_ISUCON_DB_PORT'] ? ENV['OPT_ISUCON_DB_PORT'].to_i : 3306,
          username: ENV['OPT_ISUCON_DB_USER'] || 'root',
          password: ENV['OPT_ISUCON_DB_PASSWORD'],
          database: ENV['OPT_ISUCON_DB_NAME'] || 'optwitter',
          reconnect: true,
        )
      end

      def get_all_tweets until_time
        if until_time
          db.xquery(%| SELECT * FROM tweets WHERE created_at < ? ORDER BY created_at DESC |, until_time)
        else
          db.query(%| SELECT * FROM tweets ORDER BY created_at DESC |)
        end
      end

      def get_user_id name
        return nil if name.nil?

        user = db.xquery(%| SELECT * FROM users WHERE name = ? |, name).first
        user ? user['id'] : nil
      end

      def get_user_name id
        return nil if id.nil?

        user = db.xquery(%| SELECT * FROM users WHERE id = ? |, id).first
        user['name']
      end

      def htmlify text
        text ||= ''
        text
          .gsub('&', '&amp;')
          .gsub('<', '&lt;')
          .gsub('>', '&gt;')
          .gsub('\'', '&apos;')
          .gsub('"', '&quot;')
          .gsub(/#(\S+)(\s|$)/, '<a class="hashtag" href="/hashtag/\1">#\1</a>\2')
      end
    end

    get '/' do
      @name = get_user_name session[:userId]
      if @name.nil?
        @flush = session[:flush]
        session.clear
        return erb :index, layout: :layout
      end

      url = URI.parse "#{OPTOMO_ENDPOINT}/#{@name}"
      req = Net::HTTP::Get.new url.path
      res = Net::HTTP.start(url.host, url.port) do |http|
        http.request req
      end
      friends = JSON.parse(res.body)['friends']

      friends_name = {}
      @tweets = []
      get_all_tweets(params[:until]).each do |row|
        row['html'] = htmlify row['text']
        row['time'] = row['created_at'].strftime '%F %T'
        friends_name[row['user_id']] ||= get_user_name row['user_id']
        row['name'] = friends_name[row['user_id']]
        @tweets.push row if friends.include? row['name']
        break if @tweets.length == PERPAGE
      end

      if params[:append]
        erb :_tweets, layout: false
      else

        erb :index, layout: :layout
      end
    end

    post '/' do
      name = get_user_name session[:userId]
      text = params[:text]
      if name.nil? || text == ''
        redirect '/'
      end

      db.xquery(%|
        INSERT INTO tweets (user_id, text, created_at) VALUES (?, ?, NOW())
      |, session[:userId], text)

      redirect '/'
    end

    get '/initialize' do
      db.query(%| DELETE FROM tweets WHERE id > 100000 |)
      db.query(%| DELETE FROM users WHERE id > 1000 |)
      url = URI.parse "#{OPTOMO_ENDPOINT}/initialize"
      req = Net::HTTP::Get.new url.path
      res = Net::HTTP.start(url.host, url.port) do |http|
        http.request req
      end
      halt 500, 'error' if res.code != '200'

      res = { result: 'OK' }
      json res
    end

    post '/login' do
      name = params[:name]
      password = params[:password]

      user = db.xquery(%| SELECT * FROM users WHERE name = ? |, name).first
      unless user
        halt 404, 'not found'
      end

      sha1digest = Digest::SHA1.hexdigest(user['salt'] + password)
      if user['password'] != sha1digest
        session[:flush] = 'ログインエラー'
        redirect '/'
      end

      session[:userId] = user['id']
      redirect '/'
    end

    post '/logout' do
      session.clear
      redirect '/'
    end

    post '/follow' do
      name = get_user_name session[:userId]
      if name.nil?
        redirect '/'
      end

      user = params[:user]
      url = URI.parse "#{OPTOMO_ENDPOINT}/#{name}"
      req = Net::HTTP::Post.new url.path
      req.set_form_data({'user' => user}, ';')
      res = Net::HTTP.start(url.host, url.port) do |http|
        http.request req
      end
      halt 500, 'error' if res.code != '200'

      redirect "/#{user}"
    end

    post '/unfollow' do
      name = get_user_name session[:userId]
      if name.nil?
        redirect '/'
      end

      user = params[:user]
      url = URI.parse "#{OPTOMO_ENDPOINT}/#{name}"
      req = Net::HTTP::Delete.new url.path
      req.set_form_data({'user' => user}, ';')
      res = Net::HTTP.start(url.host, url.port) do |http|
        http.request req
      end
      halt 500, 'error' if res.code != '200'

      redirect "/#{user}"
    end

    def search session, params
      @name = get_user_name session[:userId]
      @query = params[:q]
      @query = "##{params[:tag]}" if params[:tag]

      friends_name = {}
      @tweets = []
      get_all_tweets(params[:until]).each do |row|
        row['html'] = htmlify row['text']
        row['time'] = row['created_at'].strftime '%F %T'
        friends_name[row['user_id']] ||= get_user_name row['user_id']
        row['name'] = friends_name[row['user_id']]
        @tweets.push row if row['text'].include? @query
        break if @tweets.length == PERPAGE
      end

      if params[:append]
        erb :_tweets, layout: false
      else
        erb :search, layout: :layout
      end
    end

    get '/hashtag/:tag' do
      search session, params
    end

    get '/search' do
      search session, params
    end

    get '/:user' do
      @name = get_user_name session[:userId]
      @user = params[:user]
      @mypage = @name == @user

      user_id = get_user_id @user
      halt 404, 'not found' if user_id.nil?

      @is_friend = false
      if @name
        url = URI.parse "#{OPTOMO_ENDPOINT}/#{@name}"
        req = Net::HTTP::Get.new url.path
        res = Net::HTTP.start(url.host, url.port) do |http|
          http.request req
        end
        friends = JSON.parse(res.body)['friends']
        @is_friend = friends.include? @user
      end

      if params[:until]
        rows = db.xquery(%|
          SELECT * FROM tweets WHERE user_id = ? AND created_at < ? ORDER BY created_at DESC
        |, user_id, params[:until])
      else
        rows = db.xquery(%|
          SELECT * FROM tweets WHERE user_id = ? ORDER BY created_at DESC
        |, user_id)
      end

      @tweets = []
      rows.each do |row|
        row['html'] = htmlify row['text']
        row['time'] = row['created_at'].strftime '%F %T'
        row['name'] = @user
        @tweets.push row
        break if @tweets.length == PERPAGE
      end

      if params[:append]
        erb :_tweets, layout: false
      else
        erb :user, layout: :layout
      end
    end

  end
end
