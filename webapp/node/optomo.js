'use strict';

const express = require('express');
const mysql = require('promise-mysql');
const bodyParser = require('body-parser');
const exec = require('child_process').exec;

const app = express();

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({
  extended: false
}));


const db = mysql.createPool({
  host: process.env.OPT_ISUCON_DB_HOST || 'localhost',
  port: process.env.OPT_ISUCON_DB_PORT || 3306,
  user: process.env.OPT_ISUCON_DB_USER || 'root',
  password: process.env.OPT_ISUCON_DB_PASSWORD,
  database: process.env.OPT_ISUCON_DB_NAME || 'optomo',
  connectionLimit: 1,
  charset: 'utf8mb4'
});

const getFriends = (user) => {
  return new Promise((resolve, reject) => {
    db.query(
      'SELECT * FROM friends WHERE me = ?', [user]
    ).then((rows) => {
      resolve(rows[0].friends.split(','));
    }).catch(reject);
  });
};

const setFriends = (user, friends) => {
  return new Promise((resolve, reject) => {
    db.query(
      'UPDATE friends SET friends = ? WHERE me = ?', [friends.join(','), user]
    ).then(resolve, reject);
  });
};


app.get('/initialize', (req, res) => {
  exec('mysql -u root -D optomo < ' + __dirname + '/../sql/seed_optomo.sql', function(err) {
    if (err) {
      res.status(500).send('error');
    } else {
      res.status(200).json({ result: 'OK' });
    }
  });
});

app.get('/:me', (req, res) => {
  const me = req.params.me;
  getFriends(me).then((friends) => {
    res.status(200).json({
      friends: friends
    });
  }).catch((err) => {
    res.status(500).send('error');
  });
});

app.post('/:me', (req, res) => {
  const me = req.params.me;
  const newFriend = req.body.user;

  getFriends(me).then((friends) => {
    if (friends.indexOf(newFriend) !== -1) {
      return res.status(400).json({
        error: newFriend + ' is already your friend.'
      });
    }

    friends.push(newFriend);
    setFriends(me, friends).then(() => {
      res.status(200).json({
        friends: friends
      });
    });
  }).catch((err) => {
    res.status(500).send('error');
  });
});

app.delete('/:user', (req, res) => {
  const me = req.params.user;
  const delFriend = req.body.user;

  getFriends(me).then((friends) => {
    const index = friends.indexOf(delFriend);
    if (index === -1) {
      return res.status(400).json({
        error: delFriend + ' is not your friend.'
      });
    }

    friends.splice(index, 1);
    setFriends(me, friends).then(() => {
      res.status(200).json({
        friends: friends
      });
    });
  }).catch((err) => {
    res.status(500).send('error');
  });
});

module.exports = app;
