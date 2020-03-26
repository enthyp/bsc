import logging
import aiohttp
import sys
import uuid
from aiohttp import web

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)


async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    this = str(uuid.uuid4())
    clients = request.app['clients']
    clients[this] = ws

    try:
        async for msg in ws:
            logging.debug(msg.data)

            if msg.type == aiohttp.WSMsgType.TEXT:
                for c_id, c_ws in request.app['clients'].items():
                    if c_id != this:
                        logging.debug('SENT: {} -> {}'.format(this, c_id))
                        await c_ws.send_str(msg.data)
            elif msg.type == aiohttp.WSMsgType.ERROR:
                logging.debug('ws connection closed with exception {}'.format(ws.exception()))
                await ws.close()
                break
            elif msg.type == aiohttp.WSMsgType.CLOSE:
                logging.debug('closing')
                await ws.close()
                break
    except Exception as e:
        logging.debug(e)
    finally:
        logging.debug('UGH')

    logging.debug('websocket connection closed')
    del clients[this]

    return ws


def main():
    app = web.Application()
    app['clients'] = {}

    app.add_routes([web.get('/', websocket_handler)])
    web.run_app(app, host='192.168.100.106', port=5000)


if __name__ == '__main__':
    main()
