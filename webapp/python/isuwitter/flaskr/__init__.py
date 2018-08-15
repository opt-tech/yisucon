from flask import Flask, request, json, abort, jsonify, render_template
from . import db


def create_app():
    # create and configure the app
    app = Flask(
        __name__,
        instance_relative_config=True,
        static_url_path=''
    )

    @app.route('/')
    def _index():
        return 'Hello, World!'

    @app.route('/hey')
    def _index2():
        return render_template('index.html')

    @app.route('/<me>', methods=['POST'])
    def _me_post(me):
        s = request.data.decode('utf-8')
        _json = json.loads(s)
        new_friend = _json['user']
        friends = db.get_friends(me)
        if not friends:
            abort(500, 'error')
        else:
            if new_friend in friends:
                abort(500, new_friend + ' is already your friends.')
            else:
                friends.append(new_friend)
                db.set_friends(friends, me)
                return jsonify({'friends': friends})

    @app.route('/<me>', methods=['DELETE'])
    def _me_delete(me):
        s = request.data.decode('utf-8')
        _json = json.loads(s)
        delete_friend = _json['user']
        friends = db.get_friends(me)
        if not friends:
            abort(500, 'error')
        else:
            if delete_friend not in friends:
                abort(500, delete_friend + ' is not your friends.')
            else:
                friends.remove(delete_friend)
                db.set_friends(friends, me)
                return jsonify({'friends': friends})

    return app
