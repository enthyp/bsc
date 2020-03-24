from flask import Flask, jsonify, request

app = Flask(__name__)


@app.route('/')
def hello_world():
    return 'Hello World!'


@app.route('/echo', methods=['POST'])
def echo_json():
    return jsonify(request.json)


if __name__ == '__main__':
    app.run()
