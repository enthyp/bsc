import base64
import bcrypt
import fernet
from aiohttp_security import SessionIdentityPolicy, setup as setup_security
from aiohttp_security.abc import AbstractAuthorizationPolicy
from aiohttp_session import setup as setup_session
from aiohttp_session.cookie_storage import EncryptedCookieStorage


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


def setup_auth(app):
    async def _setup(app):
        # Setup session storage.
        fernet_key = fernet.Fernet.generate_key()
        secret_key = base64.urlsafe_b64decode(fernet_key)
        setup_session(app, EncryptedCookieStorage(secret_key))

        setup_security(app, SessionIdentityPolicy(), DBAuthorizationPolicy(app['storage']))
    app.on_startup.append(_setup)
