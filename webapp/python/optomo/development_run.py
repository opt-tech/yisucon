import flaskr

if __name__ == '__main__':
    app = flaskr.create_app()
    app.run(debug=True, host='0.0.0.0', port=8081)
