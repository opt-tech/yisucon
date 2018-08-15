import urllib.request
import hashlib
from flask import Flask, request, json, abort, jsonify, render_template, make_response, redirect
from . import db

PERPAGE = 50


def create_app():
    # create and configure the app
    app = Flask(
        __name__,
        instance_relative_config=True,
        static_url_path=''
    )

    @app.route('/initialize')
    def initialize():
        db.initialize()
        req = urllib.request.Request('http://localhost:8081/initialize')
        with urllib.request.urlopen(req) as res:
            if res.status != 200:
                return abort(500, 'error')
        return jsonify({'result': 'ok'})

    @app.route('/')
    def _index():
        name = db.get_user_name(request.cookies.get('userId'))
        if not name:
            flush = request.cookies.get('flush')
            res = make_response(render_template('index.html', flush=flush))
            res.set_cookie('flush', '')
            return res
        req = urllib.request.Request('http://localhost:8081/' + name)
        with urllib.request.urlopen(req) as res:
            friends = json.load(res)['friends']

            tweets = []
            for _row in db.get_all_tweets(request.args.get('until')):
                row = {}
                row['id'] = _row[0]
                row['user_id'] = _row[1]
                row['name'] = db.get_user_name(row['user_id'])
                row['text'] = _row[2]
                row['created_at'] = _row[3]
                row['html'] = db.htmlify(row['text'])
                row['time'] = row['created_at'].strftime('%Y-%m-%d %H:%M:%S')
                if row['name'] in friends:
                    tweets.append(row)
                if len(tweets) == PERPAGE:
                    break
            if not request.args.get('append'):
                return render_template('index.html', name=name, tweets=tweets)
            else:
                return render_template('_tweets.html', tweets=tweets)

    @app.route('/', methods=['POST'])
    def post():
        name = db.get_user_name(request.cookies.get('userId'))
        text = request.form['text']
        if (not name) or (not text):
            return redirect("/", code=302)
        db.insert(request.cookies.get('userId'), text)
        return redirect("/", code=302)

    @app.route('/logout', methods=['POST'])
    def logout():
        res = make_response(redirect("/", code=302))
        res.set_cookie('flush', '')
        res.set_cookie('userId', '')
        return res

    @app.route('/<user>')
    def get_user(user):
        name = db.get_user_name(request.cookies.get('userId'))
        my_page = user == name

        user_id = db.get_user_id(user)
        if not user_id:
            abort(404, 'not found')
        else:
            is_friend = False
            if name:
                req = urllib.request.Request('http://localhost:8081/' + name)
                with urllib.request.urlopen(req) as res:
                    friends = json.load(res)['friends']
                    is_friend = user in friends
            tweets = []
            for _row in db.get_user_tweets(user_id, request.args.get('until')):
                row = {}
                row['id'] = _row[0]
                row['user_id'] = _row[1]
                row['name'] = user
                row['text'] = _row[2]
                row['created_at'] = _row[3]
                row['html'] = db.htmlify(row['text'])
                row['time'] = row['created_at'].strftime('%Y-%m-%d %H:%M:%S')
                tweets.append(row)
                if len(tweets) == PERPAGE:
                    break
            if not request.args.get('append'):
                return render_template('user.html', is_friend=is_friend, my_page=my_page, name=name, tweets=tweets, user=user)
            else:
                return render_template('_tweets.html', tweets=tweets)

    @app.route('/follow', methods=['POST'])
    def follow():
        name = db.get_user_name(request.cookies.get('userId'))
        if not name:
            return redirect("/", code=302)
        user = request.form['user']
        req = urllib.request.Request(
            'http://localhost:8081/' + name,
            data=json.dumps({"user": user}).encode("utf-8"),
            method="POST",
            headers={"Content-Type" : "application/json"}
        )
        with urllib.request.urlopen(req) as res:
            if res.status != 200:
                return abort(500, 'error')
        return redirect("/" + user, code=302)

    @app.route('/unfollow', methods=['POST'])
    def unfollow():
        name = db.get_user_name(request.cookies.get('userId'))
        if not name:
            return redirect("/", code=302)
        user = request.form['user']
        req = urllib.request.Request(
            'http://localhost:8081/' + name,
            data=json.dumps({"user": user}).encode("utf-8"),
            method="DELETE",
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req) as res:
            if res.status != 200:
                return abort(500, 'error')
        return redirect("/" + user, code=302)

    @app.route('/login', methods=['POST'])
    def login():
        name = request.form['name']
        password = request.form['password']
        user = db.find_user(name)
        if not user:
            abort(404, 'not found')
        else:
            sha1digest = hashlib.sha1((user[2] + password).encode('utf-8')).hexdigest()
            if user[3] != sha1digest:
                res = make_response(redirect("/", code=302))
                res.set_cookie('flush', 'ログインエラー')
                return res
            res = make_response(redirect("/", code=302))
            res.set_cookie('userId', str(user[0]))
            return res

    @app.route('/search')
    def search_query():
        return search(request.args.get('q'))

    @app.route('/hashtag/<tag>')
    def search_tag(tag):
        return search('#' + tag)

    def search(query):
        name = db.get_user_name(request.cookies.get('userId'))
        tweets = []
        for _row in db.get_all_tweets(request.args.get('until')):
            row = {}
            row['text'] = _row[2]
            if query not in row['text']:
                continue
            row['id'] = _row[0]
            row['user_id'] = _row[1]
            row['name'] = db.get_user_name(row['user_id'])
            row['created_at'] = _row[3]
            row['html'] = db.htmlify(row['text'])
            row['time'] = row['created_at'].strftime('%Y-%m-%d %H:%M:%S')
            tweets.append(row)
            if len(tweets) == PERPAGE:
                break
        if not request.args.get('append'):
            return render_template('search.html', query=query, name=name, tweets=tweets)
        else:
            return render_template('_tweets.html', tweets=tweets)

    return app
