<?php
require __DIR__ . '/vendor/autoload.php';
session_start();
const PER_PAGE = 50;
$container = new \Slim\Container();
$container['view'] = function () {
    return new \Slim\Views\PhpRenderer('./templates/');
};
$container['config'] = [
    'settings' => [
        'db' => [
            'host' => $_ENV['OPTWITTER_DB_HOST'] ?? 'localhost',
            'port' => $_ENV['OPTWITTER_DB_PORT'] ?? '3306',
            'user' => $_ENV['OPTWITTER_DB_USER'] ?? 'root',
            'password' => $_ENV['OPTWITTER_DB_PASSWORD'] ?? null,
            'database' => $_ENV['OPTWITTER_DB_NAME'] ?? 'optwitter'
        ],
        'optomo_end_point' => $_ENV['OPTOMO_ORIGIN'] ?? 'http://localhost:8081'
    ]
];
$app = new \Slim\App($container);


function db_open() {
    global $app;
    static $db;
    if (!$db) {
        $config = $app->getContainer()->get('config')['settings']['db'];
        $dsn = sprintf("mysql:host=%s;port=%s;dbname=%s;charset=utf8mb4", $config['host'], $config['port'], $config['database']);
        $options = array(
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        );
        $db = new \PDO($dsn, $config['user'], $config['password'], $options);
    }
    return $db;
}

function db_exec($query, $args = array()) {
    $stmt = db_open()->prepare($query);
    $stmt->execute($args);
    return $stmt;
}

function htmlify($tweet) {
    $tweet = str_replace('&', '&amp;', $tweet);
    $tweet = str_replace('<', '&lt;', $tweet);
    $tweet = str_replace('>', '&gt;', $tweet);
    $tweet = str_replace("'", '&apos;', $tweet);
    $tweet = str_replace('"', '&quot;', $tweet);
    $tweet = preg_replace('/#(\\S+)(\\s|$)/', '<a class="hashtag" href="hashtag/${1}">#${1}</a>${2}', $tweet);
    return $tweet;
}

function has_user_id() {
    return isset($_SESSION['user_id']);
}

function get_user($id) {
    $user = db_exec('SELECT * FROM users WHERE id = ?', array($id))->fetch();
    return $user;
}

function get_user_id($name) {
    $user = db_exec('SELECT id FROM users WHERE name = ?', array($name))->fetch();
    return $user['id'];
}

function load_friends($name) {
    global $app;
    $origin = $app->getContainer()->get('config')['settings']['optomo_end_point'];
    $url = sprintf("%s/%s", $origin, $name);
    $client = new \GuzzleHttp\Client;
    $response = $client->request('GET', $url)->getBody();
    $data = json_decode($response, true)['friends'];
    return $data;
}

function search($request, $response, $args) {

    $name = get_user($_SESSION['user_id'])['name'] ?? "";
    $query = $request->getQueryParams()['q'] ?? "";
    if (isset($args['tag'])) {
        $query = '#' . $args['tag'];
    }

    if (isset($request->getQueryParams()['until'])) {
      $rows = db_exec('SELECT * FROM tweets WHERE created_at < ? ORDER BY created_at DESC', array($request->getQueryParams()['until']))->fetchAll();
    } else {
      $rows = db_exec('SELECT * FROM tweets ORDER BY created_at DESC', array())->fetchAll();
    }

    $tweets = array();
    foreach ($rows as $row) {
        if (!$row['id'] || !$row['user_id'] || !$row['text'] || !$row['created_at']) {
            return;
        }
        $tweet = $row;
        $tweet['html'] = htmlify($row['text']);
        $tweet['time'] = strftime('%F %T', strtotime($row['created_at']));
        $tweet['user_name'] = get_user($row['user_id'])['name'];
        if (preg_match("/$query/", $row['text'])) {
            $tweets[] = $tweet;
        } else {
            continue;
        }
        if (count($tweets) == PER_PAGE) {
            break;
        }
    }

    $template = (isset($request->getQueryParams()['append']) && $request->getQueryParams()['append'] !== '') ? '_tweets.inc' : 'search.php';

    return array($response, $template, [
        'name' => $name,
        'tweets' => $tweets,
        'query' => $query
    ]);

}

$app->post('/login', function ($request, $response, $args) {

    $params = $request->getParsedBody();
    $name = $params['name'];
    $password = $params['password'];
    $row = db_exec('SELECT * FROM users WHERE name = ?', array($name))->fetch();
    if (!$row) {
        return $response->withStatus(404)->write('not found');
    }
    if ($row['password'] !== sha1($row['salt'].$password)) {
        $_SESSION['flush'] = 'ログインエラー';
        return $response->withRedirect('/');
    }

    $_SESSION['user_id'] = $row['id'];
    return $response->withRedirect('/');
});

$app->post('/logout', function ($request, $response, $args) {
    $_SESSION['user_id'] = null;
    return $response->withRedirect('/');
});

$app->get('/', function ($request, $response, $args) {
    if (!has_user_id()) {
        $flush = $_SESSION['flush'] ?? '';
        $_SESSION['flush'] = null;
        return $this->view->render($response, 'index.php', ['flush' => $flush]);
    }

    $user = get_user($_SESSION['user_id']);

    if (isset($request->getQueryParams()['until'])) {
      $rows = db_exec('SELECT * FROM tweets WHERE created_at < ? ORDER BY created_at DESC', array($request->getQueryParams()['until']))->fetchAll();
    } else {
      $rows = db_exec('SELECT * FROM tweets ORDER BY created_at DESC', array())->fetchAll();
    }

    $friends = load_friends($user['name']);

    $tweets = array();
    foreach($rows as $row) {
        if (!$row['id'] || !$row['user_id'] || !$row['text'] || !$row['created_at']) {
            return;
        }
        $tweet = $row;
        $tweet['html'] = htmlify($row['text']);
        $tweet['user_name'] = get_user($row['user_id'])['name'];
        $tweet['time'] = strftime('%F %T', strtotime($row['created_at']));
        foreach ($friends as $friend) {
            if ($friend === $tweet['user_name']) {
                $tweets[] = $tweet;
            }
        }
        if (count($tweets) == PER_PAGE) {
            break;
        }
    }
    $template = (isset($request->getQueryParams()['append']) && $request->getQueryParams()['append'] !== '') ? '_tweets.inc' : 'index.php';

    $this->view->render($response, $template, ['name' => $user['name'], 'tweets' => $tweets]);
});

$app->post('/', function ($request, $response, $args) {
    if(!has_user_id()){
        return $response->withRedirect('/');
    }

    $user_id = $_SESSION['user_id'];
    $params = $request->getParsedBody();
    $text = $params['text'];

    db_exec('INSERT INTO tweets (user_id, text, created_at) VALUES (?, ?, NOW())', array($user_id, $text));
    return $response->withRedirect('/');
});

$app->get('/search', function ($request, $response, $args) {
    list($response, $file, $params) = search($request, $response, $args);
    $this->view->render($response, $file, $params);
});

$app->get('/hashtag/{tag}', function ($request, $response, $args) {
    list($response, $file, $params) = search($request, $response, $args);
    $this->view->render($response, $file, $params);
});


$app->get('/initialize', function ($request, $response, $args) {
    db_exec('DELETE FROM tweets WHERE id > 100000');
    db_exec('DELETE FROM users WHERE id > 1000');
    $origin = $this->get('config')['settings']['optomo_end_point'];
    $url = sprintf("%s/%s", $origin, 'initialize');
    $client = new \GuzzleHttp\Client;
    $status = $client->request('GET', $url)->getStatusCode();
    if ($status !== 200) {
        return $response->withStatus(500)->write('error');
    }
    return $response->withJson(array('result' => 'ok'));
});

$app->post('/follow', function ($request, $response, $args) {
    if (!has_user_id()) {
        return $response->withRedirect('/');
    }

    $user = get_user($_SESSION['user_id'])['name'];
    $friend_name = $request->getParsedBody()['user'];
    $origin = $this->get('config')['settings']['optomo_end_point'];
    $url = sprintf("%s/%s", $origin, $user);
    $client = new \GuzzleHttp\Client;
    $status = $client->request('POST', $url, [
        'form_params' => [
            'user' => $friend_name
        ]
    ])->getStatusCode();
    if ($status !== 200) {
        return $response->withStatus(500)->write('error');
    }

    return $response->withRedirect('/');
});

$app->post('/unfollow', function ($request, $response, $args) {
    if (!has_user_id()) {
        return $response->withRedirect('/');
    }
    $user = get_user($_SESSION['user_id'])['name'];
    $friend_name = $request->getParsedBody()['user'];
    $origin = $this->get('config')['settings']['optomo_end_point'];
    $url = sprintf("%s/%s", $origin, $user);
    $client = new \GuzzleHttp\Client;
    $status = $client->request('DELETE', $url, [
        'form_params' => [
            'user' => $friend_name
        ]
    ])->getStatusCode();
    if ($status !== 200) {
        return $response->withStatus(500)->write('error');
    }

    return $response->withRedirect('/');
});

$app->get('/{user}', function ($request, $response, $args) {
    $name = '';
    if (has_user_id()) {
        $name = get_user($_SESSION['user_id'])['name'];
    }

    $user = $args['user'];
    $mypage = $user === $name;

    $user_id = get_user_id($user);
    if (!$user_id) {
        return $response->withStatus(404)->write('not found');
    }

    $is_friend = false;
    if ($name !== '') {
        $friends = load_friends($name);
        if (in_array($user, $friends)) {
            $is_friend = true;
        }
    }

    if (isset($request->getQueryParams()['until'])) {
      $rows = db_exec('SELECT * FROM tweets WHERE user_id = ? AND created_at < ? ORDER BY created_at DESC', array($user_id, $request->getQueryParams()['until']))->fetchAll();
    } else {
      $rows = db_exec('SELECT * FROM tweets WHERE user_id = ? ORDER BY created_at DESC', array($user_id))->fetchAll();
    }

    $tweets = array();
    foreach ($rows as $row) {

        if (!$row['id'] || !$row['user_id'] || !$row['text'] || !$row['created_at']) {
            return;
        }
        $tweet = $row;
        $tweet['html'] = htmlify($row['text']);
        $tweet['user_name'] = get_user($row['user_id'])['name'];
        $tweet['time'] = strftime('%F %T', strtotime($row['created_at']));
        $tweets[] = $tweet;
        if (count($tweets) == PER_PAGE) {
            break;
        }
    }
    $template = (isset($request->getQueryParams()['append']) && $request->getQueryParams()['append'] !== '') ? '_tweets.inc' : 'user.php';
    $this->view->render($response, $template, ['name' => $name, 'user' => $user, 'tweets' => $tweets, 'is_friend' => $is_friend, 'mypage' => $mypage]);
});


$app->run();
