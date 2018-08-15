'use strict';

const express = require('express');
const path = require('path');
const crypto = require('crypto');
const mysql = require('promise-mysql');
const strftime = require('strftime');
const bluebird = require('bluebird');
const request = bluebird.promisifyAll(require('request'), {multiArgs: true});
const bodyParser = require('body-parser');
const session = require('express-session');

const async = (generator) => {
  return (req, res) => {
    bluebird.coroutine(generator)(req, res).catch((err) => {
      console.error('[Internal Server Error] ' + err.stack);
      res.status(500).send({ code: 500, error: err.message });
    });
  };
};

const app = express();

app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');

app.use(require('express-ejs-layouts'));

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: false }));
app.use(session({'secret': 'optwitter', resave: true, saveUninitialized: true}));
app.use(express.static(path.join(__dirname, '../public')));


const db = mysql.createPool({
  host: process.env.OPT_ISUCON_DB_HOST || 'localhost',
  port: process.env.OPT_ISUCON_DB_PORT || 3306,
  user: process.env.OPT_ISUCON_DB_USER || 'root',
  password: process.env.OPT_ISUCON_DB_PASSWORD,
  database: process.env.OPT_ISUCON_DB_NAME || 'optwitter',
  connectionLimit: 1,
  charset: 'utf8mb4'
});

const PERPAGE = 50;
const OPTOMO_ENDPOINT = 'http://localhost:8081/';

const getAllTweets = (until) => {
  if (until) {
    return db.query( 'SELECT * FROM tweets WHERE created_at < ? ORDER BY created_at DESC', [until]);
  } else {
    return db.query( 'SELECT * FROM tweets ORDER BY created_at DESC');
  }
};

const getUserId = (name) => {
  return new Promise((resolve, reject) => {
    if (!name) {
      resolve(null);
      return;
    }
    db.query(
      'SELECT * FROM users WHERE name = ?',
      [name]
    ).then((rows) => {
      resolve(rows[0] ? rows[0].id : null);
    }).catch(reject);
  });
};

const getUserName = (id) => {
  return new Promise((resolve, reject) => {
    if (!id) {
      resolve(null);
      return;
    }
    db.query(
      'SELECT * FROM users WHERE id = ?',
      [id]
    ).then((rows) => {
      resolve(rows[0] ? rows[0].name : null);
    }).catch(reject);
  });
};

const htmlify = (text) => {
  text = text || '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/'/g, '&apos;')
    .replace(/"/g, '&quot;')
    .replace(/#(\S+)(\s|$)/g, '<a class="hashtag" href="/hashtag/$1">#$1</a>$2');
};

app.get('/', async(function*(req, res) {
  const name = yield getUserName(req.session.userId);
  if (!name) {
    const flush = req.session.flush;
    req.session.flush = null;
    res.render('index', {
      name,
      flush,
    });
    return;
  }

  const [_, data] = yield request.getAsync(OPTOMO_ENDPOINT + name);
  const friends = JSON.parse(data).friends;

  const friendsName = {};
  let tweets = [];
  const rows = yield getAllTweets(req.query.until);
  for (let row of rows) {
    row.html = htmlify(row.text);
    row.time = strftime('%F %T', new Date(row.created_at));
    friendsName[row.user_id] = friendsName[row.user_id] || (yield getUserName(row.user_id));
    row.name = friendsName[row.user_id];
    if (friends.indexOf(friendsName[row.user_id]) !== -1) {
      tweets.push(row);
    }
    if (tweets.length === PERPAGE) {
      break;
    }
  }

  if (req.query.append) {
    res.render('_tweets', {
      layout: false,
      tweets,
    });
    return;
  }

  res.render('index', {
    name,
    tweets
  });
}));

app.post('/', async(function*(req, res) {
  const name = yield getUserName(req.session.userId);
  const text = req.body.text;
  if (!name || !text) {
    res.redirect('/');
    return;
  }


  yield db.query(
    'INSERT INTO tweets (user_id, text, created_at) VALUES (?, ?, NOW())',
    [req.session.userId, text]
  );

  res.redirect('/');
}));

app.get('/initialize', async(function*(req, res) {
  yield db.query('DELETE FROM tweets WHERE id > 100000')
  yield db.query('DELETE FROM users WHERE id > 1000')
  const [response, body] = yield request.getAsync(OPTOMO_ENDPOINT + 'initialize');
  if (response.statusCode !== 200) {
    res.status(500).send('error');
  } else {
    res.status(200).json({ result: "ok" });
  }
}));

app.post('/login', async(function*(req, res) {
  const name = req.body.name;
  const password = req.body.password;

  const rows = yield db.query(
    'SELECT * FROM users WHERE name = ?',
    [name]
  );

  if (!rows[0]) {
    res.status(404).end('not found');
    return;
  }

  const sha1 = crypto.createHash('sha1');
  sha1.update(rows[0].salt + password);
  const sha1digest = sha1.digest('hex');
  if (rows[0].password !== sha1digest) {
    req.session.flush = 'ログインエラー';
    res.redirect('/');
    return;
  }

  req.session.userId = rows[0].id;
  res.redirect('/');
}));

app.post('/logout', (req, res) => {
  req.session.userId = null;
  res.redirect('/');
});


app.post('/follow', async(function*(req, res) {
  const name = yield getUserName(req.session.userId);
  if (!name) {
    res.redirect('/');
    return;
  }
  const user = req.body.user;
  const [response, data] = yield request.postAsync({
    url: OPTOMO_ENDPOINT + name,
    form: { user: user }
  });
  if (response.statusCode !== 200) {
    res.status(500).end('error');
    return;
  }

  res.redirect('/' + user);
}));

app.post('/unfollow', async(function*(req, res) {
  const name = yield getUserName(req.session.userId);
  if (!name) {
    res.redirect('/');
    return;
  }
  const user = req.body.user;
  const [response, data] = yield request.deleteAsync({
    url: OPTOMO_ENDPOINT + name,
    form: { user: user }
  });
  if (response.statusCode !== 200) {
    res.status(500).end('error');
    return;
  }

  res.redirect('/' + user);
}));

const search = function*(req, res) {
  const name = yield getUserName(req.session.userId);
  let query = req.query.q;
  if (req.params.tag) {
    query = '#' + req.params.tag;
  }

  const friendsName = {};
  let tweets = [];
  const rows = yield getAllTweets(req.query.until);
  for (let row of rows) {
    row.html = htmlify(row.text);
    row.time = strftime('%F %T', new Date(row.created_at));
    friendsName[row.user_id] = friendsName[row.user_id] || (yield getUserName(row.user_id));
    row.name = friendsName[row.user_id];
    if (row.text.indexOf(query) !== -1) {
      tweets.push(row);
    }
    if (tweets.length === PERPAGE) {
      break;
    }
  }

  if (req.query.append) {
    res.render('_tweets', {
      layout: false,
      tweets
    });
    return;
  }

  res.render('search', {
    name,
    query,
    tweets
  });
};

app.get('/search', async(search));
app.get('/hashtag/:tag', async(search));

app.get('/:user', async(function*(req, res) {
  const name = yield getUserName(req.session.userId);
  const user = req.params.user;
  const mypage = name === user;

  const userId = yield getUserId(user);
  if (!userId) {
    res.status(404).end('not found');
    return;
  }

  let isFriend = false;
  if (name) {
    const [_, data] = yield request.getAsync(OPTOMO_ENDPOINT + name);
    const friends = JSON.parse(data).friends;
    isFriend = friends.indexOf(user) !== -1;
  }

  let rows;
  if (req.query.until) {
    rows = yield db.query(
      'SELECT * FROM tweets ' +
      'WHERE user_id = ? AND created_at < ? ORDER BY created_at DESC',
      [userId, req.query.until]
    );
  } else {
    rows = yield db.query(
      'SELECT * FROM tweets ' +
      'WHERE user_id = ? ORDER BY created_at DESC',
      [userId]
    );
  }

  let tweets = [];
  for (let row of rows) {
    row.html = htmlify(row.text);
    row.time = strftime('%F %T', new Date(row.created_at));
    row.name = user;
    tweets.push(row);
    if (tweets.length === PERPAGE) {
      break;
    }
  }

  if (req.query.append) {
    res.render('_tweets', {
      layout: false,
      tweets,
    });
    return;
  }

  res.render('user', {
    name,
    user,
    mypage,
    isFriend,
    tweets
  });
}));

module.exports = app;
