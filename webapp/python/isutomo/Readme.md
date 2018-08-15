
## dev

(with auto reload)

FLASK_ENV=development python development_run.py

## prod

waitress-serve --port=8081 --call 'flaskr:create_app'