import json
import logging
import aiohttp
import sys
from aiohttp import web

from notifications import setup_notifications
from server import ClientEndpoint, Server

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)
routes = web.RouteTableDef()


@routes.get('/')
async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    logging.info('Connected...')

    server = request.app['server']
    # TODO: nick and token should be in place (login)
    endpoint = ClientEndpoint(socket=ws, server=server)

    try:
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                msg_str = msg.json()
                msg_obj = json.loads(msg_str)
                msg_type = msg_obj['type']
                payload = json.loads(msg_obj['payload'])
                await endpoint.dispatch(msg_type, payload)

            elif msg.type == aiohttp.WSMsgType.CLOSED:
                logging.info('WebSocket connection closing...')
                await ws.close()
                break

            elif msg.type == aiohttp.WSMsgType.ERROR:
                logging.error('WebSocket connection closed with exception {}'.format(ws.exception()))
                await ws.close()
                break

    except Exception as e:
        logging.error((e, type(e)))
    finally:
        logging.info('Websocket connection closed')

    server.rm_client(client=endpoint)  # TODO: conversation cleanup? maybe when all parties leave...
    return ws


async def token_handler(request):
    body = await request.json()
    identity, token = body['id'], body['token']
    request.app['server'].on_token(identity, token)

    return web.Response()


def main():
    setup_notifications()

    app = web.Application()
    app['server'] = Server()
    app.add_routes([
        web.get('/', websocket_handler),
        web.post('/token', token_handler)
    ])

    web.run_app(app, host='192.168.100.106', port=5000)


if __name__ == '__main__':
    main()
