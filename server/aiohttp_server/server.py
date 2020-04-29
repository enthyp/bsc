import json
import logging
import uuid
from notifications import async_notify


class Server:
    def __init__(self):
        self.clients = {}
        self.calls = {}

    def add_client(self, client):
        if client.nick not in self.clients:
            self.clients[client.nick] = client
        else:
            # TODO: handle error (possible?)
            logging.error(f'Duplicate user nick: {client.nick}')

    def rm_client(self, client):
        if client.nick in self.clients:
            del self.clients[client.nick]
        else:
            # TODO: handle error (possible?)
            logging.error(f'No user to delete: {client.nick}')

    def get_endpoint(self, nick):
        # TODO: handle error
        return self.clients.get(nick, None)

    def on_token(self, identity, token):
        if identity in self.clients:
            self.clients[identity].add_token(token)
        else:
            # TODO: handle error
            logging.error(f'Token received: user {identity} not found')

    def start_conversation(self):
        conversation = Conversation()
        call_id = str(uuid.uuid4())  # TODO: tb replaced by DB id (quality rating, duration etc.)
        self.calls[call_id] = conversation
        return conversation


class Conversation:
    def __init__(self):
        self.parties = {}

    def join(self, client):
        self.parties[client.nick] = client

    def leave(self, client):
        del self.parties[client.nick]

    def signal(self, sender, type, msg):
        for nick, endpoint in self.parties:
            if nick != sender.nick:
                endpoint.send_msg(type, msg)


# TODO: states seem to deserve their own objects to clean it all up
class ClientEndpoint:

    # Client connection state
    INIT = 0
    LOGGED_IN = 1
    RENDEZVOUS = 2
    SIGNALLING = 3

    # Messages from the client
    LOGIN = 'LOGIN'
    CALL = 'CALL'
    ACCEPT = 'ACCEPT'
    REFUSE = 'REFUSE'

    # Messages to the client
    INCOMING = 'INCOMING'  # Firebase only!
    ACCEPTED = 'ACCEPTED'
    REFUSED = 'REFUSED'

    # From/to
    OFFER = 'OFFER'
    ANSWER = 'ANSWER'
    ICE = 'ICE_CANDIDATE'

    def __init__(self, socket, server):
        self.socket = socket
        self.server = server
        self.state = ClientEndpoint.INIT
        self.nick = None
        self.token = None
        self.conversation = None

        # Message handlers for states
        self.handlers = {
            (ClientEndpoint.INIT, ClientEndpoint.LOGIN): self.login,
            (ClientEndpoint.LOGGED_IN, ClientEndpoint.CALL): self.call,
            (ClientEndpoint.RENDEZVOUS, ClientEndpoint.ACCEPT): self.accept,
            (ClientEndpoint.RENDEZVOUS, ClientEndpoint.REFUSE): self.refuse,
            (ClientEndpoint.SIGNALLING, ClientEndpoint.OFFER): self.offer,
            (ClientEndpoint.SIGNALLING, ClientEndpoint.ANSWER): self.answer,
            (ClientEndpoint.SIGNALLING, ClientEndpoint.ICE): self.ice,
        }

    def add_token(self, token):
        self.token = token

    async def dispatch(self, type, msg):
        handler = self.handlers[(self.state, type)]
        if handler:
            await handler(msg)  # TODO: handle errors
        else:
            logging.error(f'No handler found for {type} in state {self.state}')

    async def login(self, msg):
        nick = msg['nick']
        self.nick = nick
        self.state = ClientEndpoint.LOGGED_IN
        self.server.add_client(self)
        logging.info(f'Logged in: {nick}')

    async def call(self, msg):
        callee = msg['to']
        callee_endpoint = self.server.get_endpoint(callee)
        if callee_endpoint:
            self.conversation = self.server.start_conversation(self)
            self.state = ClientEndpoint.RENDEZVOUS
            await callee_endpoint.on_incoming_call(self.nick, self.conversation)
        else:
            # TODO: handle errors
            logging.error(f'Call: user {callee} not found')

    async def accept(self, msg):
        caller = msg['to']
        caller_endpoint = self.server.get_endpoint(caller)
        if caller_endpoint:
            self.conversation.join(self)
            self.state = ClientEndpoint.SIGNALLING
            caller_endpoint.on_accepted_call(self.nick)
        else:
            # TODO: handle errors
            logging.error(f'Accept: user {caller} not found')

    async def refuse(self, msg):
        caller = msg['to']
        caller_endpoint = self.server.get_endpoint(caller)
        if caller_endpoint:
            self.conversation = None
            self.state = ClientEndpoint.LOGGED_IN
            caller_endpoint.on_refused_call(self.nick)
        else:
            # TODO: handle errors
            logging.error(f'Refuse: user {caller} not found')

    async def offer(self, msg):
        self.conversation.signal(self, ClientEndpoint.OFFER, msg)
        logging.debug(f'Offer published by {self.nick}: {msg}')

    async def answer(self, msg):
        self.conversation.signal(self, msg)
        logging.debug(f'Answer published by {self.nick}: {msg}')

    async def ice(self, msg):
        self.conversation.signal(self, msg)
        logging.debug(f'ICE candidate published by {self.nick}: {msg}')

    async def on_incoming_call(self, caller, conversation):
        if self.token:
            await async_notify(self.token, {'type': ClientEndpoint.INCOMING, 'caller': caller})
            self.conversation = conversation
            self.state = ClientEndpoint.RENDEZVOUS
            logging.info(f'Incoming call pushed to: {self.nick}')
        else:
            # TODO: handle error
            logging.error(f'Incoming call from {caller} to {self.nick}: no token for {self.nick}')

    async def on_accepted_call(self, callee):
        self.conversation.join(self)
        self.state = ClientEndpoint.SIGNALLING
        await self.send_msg(ClientEndpoint.ACCEPTED, {'from': self.nick, 'to': callee})
        logging.info(f'Accepted call pushed to: {self.nick}')

    async def on_refused_call(self, callee):
        self.conversation = None
        self.state = ClientEndpoint.LOGGED_IN
        await self.send_msg(ClientEndpoint.REFUSED, {'from': self.nick, 'to': callee})
        logging.info(f'Refused call pushed to: {self.nick}')

    async def send_msg(self, type, payload):
        payload_json = json.dumps(payload)
        ws_msg = json.dumps({'type': type, 'payload': payload_json})
        await self.socket.send_json(ws_msg)
