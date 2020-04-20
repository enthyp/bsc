import logging
from flask import Flask
from flask_socketio import SocketIO
from notifications import notify, setup_notifications

logging.basicConfig(level=logging.INFO)

app = Flask(__name__)
socketio = SocketIO(app)


@socketio.on('message')
def handle_message(message):
    logging.info('received message: ' + message)


@socketio.on("foo")
def handle_foo(msg):
    logging.info('FOO! ' + msg)


if __name__ == '__main__':
    setup_notifications()
    socketio.run(app, host='192.168.100.106', port=5001)
