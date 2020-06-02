import configparser
import json
import logging
import aiohttp
import sys
from aiohttp import web
from aiohttp_security import (
    remember, forget, check_authorized,
    authorized_userid
)

from server.auth import check_credentials, setup_auth
from server.notifications import setup_notifications
from server.serving import ClientEndpoint, setup_server
from server.storage import setup_db

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
        # TODO: some global error handler? (CancelledError...)
        logging.error((e, type(e)))
    finally:
        logging.info('Websocket connection closed')

    server.rm_client(client=endpoint)  # TODO: conversation cleanup? maybe when all parties leave...
    return ws


@routes.post('/token')
async def token_handler(request):
    await check_authorized(request)
    login = await authorized_userid(request)

    token = await request.text()
    logging.info(f'TOKEN: {token}')
    await request.app['server'].on_token(login, token)

    return web.Response()


@routes.post('/login')
async def handle_login(request):
    body = await request.json()
    try:
        login, pwd = body['login'], body['password']
    except KeyError:
        raise web.HTTPBadRequest()

    logging.info("LOGIN: {}".format(login))
    storage = request.app['storage']
    if await check_credentials(storage, login, pwd):
        response = web.HTTPOk()
        await remember(request, response, login)
        raise response
    else:
        raise web.HTTPUnauthorized()


@routes.post('/logout')
async def handle_logout(request):
    logging.info("LOGOUT")
    response = web.HTTPOk()
    await forget(request, response)
    raise response


def main():
    parser = configparser.ConfigParser()
    parser.read('../config.ini')

    app = web.Application()

    setup_db(app, parser)
    setup_notifications(parser)
    setup_auth(app)
    setup_server(app)

    app.add_routes(routes)
    web.run_app(app, host='192.168.100.106', port=5000)


if __name__ == '__main__':
    main()
