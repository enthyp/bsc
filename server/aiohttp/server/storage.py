import aiopg.sa as aiosa
import sqlalchemy as sa

meta = sa.MetaData()
users = sa.Table(
    'users', meta,
    sa.Column('id', sa.Integer, primary_key=True),
    sa.Column('login', sa.String(100), unique=True, nullable=False),
    sa.Column('password', sa.String(100), nullable=False),
    sa.Column('token', sa.String(4096), nullable=False),
)

create_user_table = ('CREATE TABLE IF NOT EXISTS users('
                     'id SERIAL PRIMARY KEY, '
                     'login VARCHAR (100) UNIQUE NOT NULL, '
                     'password VARCHAR (100) NOT NULL, '
                     'token VARCHAR (4096));')


class DBStorage:
    def __init__(self, db):
        self.db = db

    async def close(self):
        self.db.close()
        await self.db.wait_closed()

    async def registered(self, login):
        async with self.db.acquire() as conn:
            s_query = users.count().where(users.c.login == login)
            user_res = await conn.scalar(s_query)
            if user_res:
                return True
            return False

    async def get_hash(self, login):
        async with self.db.acquire() as conn:
            s_query = users.select().where(users.c.login == login)
            user_res = await conn.execute(s_query)
            if user_res:
                user = await user_res.fetchone()
                if user:
                    return user['password']
            return None

    async def get_tokens(self):
        async with self.db.acquire() as conn:
            s_query = sa.select([users.c.login, users.c.token])
            res = await conn.execute(s_query)

            return await res.fetchall()

    async def insert_token(self, login, token):
        async with self.db.acquire() as conn:
            i_query = users.update().where(users.c.login == login).values(token=token)
            await conn.execute(i_query)


async def get_engine(config):
    return await aiosa.create_engine(config.get('DATABASE', 'URL'))


async def cleanup_storage(app):
    await app['storage'].close()


def setup_db(app, config):
    async def _setup(app):
        engine = await get_engine(config)
        app['storage'] = DBStorage(engine)

        # Create tables.
        async with engine.acquire() as conn:
            await conn.execute(create_user_table)

    app.on_startup.append(_setup)
    app.on_cleanup.append(cleanup_storage)
