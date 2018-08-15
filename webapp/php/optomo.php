<?php
require __DIR__ . '/vendor/autoload.php';

$container = new \Slim\Container();

$container['config'] = [
    'settings' => [
        'db' => [
            'host' => $_ENV['OPTOMO_DB_HOST'] ?? 'localhost',
            'port' => $_ENV['OPTOMO_DB_PORT'] ?? '3306',
            'user' => $_ENV['OPTOMO_DB_USER'] ?? 'root',
            'password' => $_ENV['OPTOMO_DB_PASSWORD'] ?? null,
            'database' => $_ENV['OPTOMO_DB_NAME'] ?? 'optomo'
        ]
    ]
];
$app = new \Slim\App($container);


function db_open() {
    global $app;
    static $db;
    if (!$db) {
        $config = $app->getContainer()->get('config')['settings']['db'];
        $dsn = sprintf("mysql:host=%s;port=%s;dbname=%s", $config['host'], $config['port'], $config['database']);
        $options = array(
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        );
        $db = new \PDO($dsn, $config['user'], $config['password'], $options);
        $db->exec("SET SESSION sql_mode='TRADITIONAL,NO_AUTO_VALUE_ON_ZERO,ONLY_FULL_GROUP_BY'");
    }
    return $db;
}

function db_exec($query, $args = array()) {
    $stmt = db_open()->prepare($query);
    $stmt->execute($args);
    return $stmt;
}

function get_friends($name) {
    $friends = explode(',', db_exec("SELECT * FROM friends WHERE me = ?", array($name))->fetch()['friends']);
    return $friends;
}

function update_friends($me, $friends) {
    $result = db_exec('UPDATE friends SET friends = ? WHERE me = ?', array(implode(',', $friends), $me));
}



$app->get('/initialize', function ($request, $response, $args) {
    system('mysql -u root -D optomo < ' . __DIR__ . '/../sql/seed_optomo.sql', $status);
    return $response->withJson(array('result' => 'ok'));
});

$app->get('/{me}', function ($request, $response, $args) {
    $me = $args['me'];
    $friends = get_friends($me);
    return $response->withJson(array('friends' => $friends));
});

$app->post('/{me}', function ($request, $response, $args) {
    $me = $args['me'];
    $new_friend = $request->getParsedBody()['user'];

    $friends = get_friends($me);

    if(in_array($new_friend, $friends)) {
        return $response->withStatus(500)->write($new_friend . ' is already your friends.');
    }

    array_push($friends, $new_friend);
    update_friends($me, $friends);
    return $response->withJson(array('friends' => $friends));
});

$app->delete('/{me}', function ($request, $response, $args) {
    $me = $args['me'];
    $new_friend = $request->getParsedBody()['user'];

    $friends = get_friends($me);
    if(!in_array($new_friend, $friends)) {
        return $response->withStatus(500)->write($new_friend . ' is not your friends.');
    }

    $friends = array_diff($friends, array($new_friend));
    $friends = array_values($friends);
    update_friends($me, $friends);
    return $response->withJson(array('friends' => $friends));
});


$app->run();
