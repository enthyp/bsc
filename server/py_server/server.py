import logging
import aiohttp
import uuid
from aiohttp import web

logging.basicConfig(level=logging.DEBUG)


async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    this = str(uuid.uuid4())
    clients = request.app['clients']
    clients[this] = ws

    async for msg in ws:
        if msg.type == aiohttp.WSMsgType.TEXT:
            for c_id, c_ws in request.app['clients'].items():
                if c_id != this:
                    await c_ws.send_str(msg.data)
        elif msg.type == aiohttp.WSMsgType.ERROR:
            print('ws connection closed with exception %s' %
                  ws.exception())

    print('websocket connection closed')
    return ws


def main():
    app = web.Application()
    app['clients'] = {}

    app.add_routes([web.get('/', websocket_handler)])
    web.run_app(app, host='192.168.43.21', port=5000)


if __name__ == '__main__':
    main()
