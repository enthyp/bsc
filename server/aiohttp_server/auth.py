import bcrypt
from aiohttp_security.abc import AbstractAuthorizationPolicy


class DBAuthorizationPolicy(AbstractAuthorizationPolicy):
    def __init__(self, storage):
        self.storage = storage

    async def authorized_userid(self, identity):
        registered = await self.storage.registered(identity)
        if registered:
            return identity
        else:
            return None

    async def permits(self, identity, permission, context=None):
        if identity is None:
            return False
        registered = await self.storage.registered(identity)  # none are privileged o.O
        return registered


async def check_credentials(storage, username, password):
    password_hash = await storage.get_hash(username)

    if password_hash:
        if bcrypt.checkpw(password.encode(), password_hash.encode()):
            return True

    return False
