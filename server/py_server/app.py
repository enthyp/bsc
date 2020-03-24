import logging
from flask import Flask, jsonify, request

logging.basicConfig(level=logging.DEBUG)
app = Flask(__name__)


@app.route('/')
def hello_world():
    return 'Hello World!'


@app.route('/echo', methods=['POST'])
def echo_json():
    app.logger.info(request.json)
    return jsonify(request.json)


if __name__ == '__main__':
    app.run(host='192.168.100.106')
