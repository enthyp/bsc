import aiopg.sa as aiosa
import enum
import re
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import insert

meta = sa.MetaData()
users = sa.Table(
    'users', meta,
    sa.Column('id', sa.Integer, primary_key=True),
    sa.Column('login', sa.String(100), unique=True, nullable=False),
    sa.Column('password', sa.String(100), nullable=False),
    sa.Column('token', sa.String(4096), nullable=False),
)

invitations = sa.Table(
    'invitations', meta,
    sa.Column('id', sa.Integer, primary_key=True),
    sa.Column('from_user', sa.Integer, sa.ForeignKey('users.id')),
    sa.Column('to_user', sa.Integer, sa.ForeignKey('users.id')),
    sa.UniqueConstraint('from_user', 'to_user')
)

friendships = sa.Table(
    'friendships', meta,
    sa.Column('id', sa.Integer, primary_key=True),
    sa.Column('from_user', sa.Integer, sa.ForeignKey('users.id')),
    sa.Column('to_user', sa.Integer, sa.ForeignKey('users.id')),
    sa.UniqueConstraint('from_user', 'to_user')
)


class UserStatus(enum.Enum):
    online = 0
    offline = 1
    busy = 2


# aiopg is a pain to work with
create_user_status_type = ("DO $$ BEGIN "
                           "    CREATE TYPE userstatus AS ENUM('online', 'offline', 'busy'); "
                           "EXCEPTION "
                           "    WHEN duplicate_object THEN NULL; "
                           "END $$;")


statuses = sa.Table(
    'statuses', meta,
    sa.Column('id', sa.Integer, primary_key=True),
    sa.Column('user_id', sa.Integer, sa.ForeignKey('users.id')),
    sa.Column('status', sa.Enum(UserStatus)),
    sa.UniqueConstraint('user_id')
)

create_user_table = ('CREATE TABLE IF NOT EXISTS users('
                     'id SERIAL PRIMARY KEY, '
                     'login VARCHAR (100) UNIQUE NOT NULL, '
                     'password VARCHAR (100) NOT NULL, '
                     'token VARCHAR (4096));')

create_invitation_table = ('CREATE TABLE IF NOT EXISTS invitations('
                           'id SERIAL PRIMARY KEY, '
                           'from_user INTEGER REFERENCES users(id), '
                           'to_user INTEGER REFERENCES users(id), '
                           'UNIQUE (from_user, to_user));')

create_friendship_table = ('CREATE TABLE IF NOT EXISTS friendships('
                           'id SERIAL PRIMARY KEY, '
                           'from_user INTEGER REFERENCES users(id), '
                           'to_user INTEGER REFERENCES users(id), '
                           'UNIQUE (from_user, to_user));')

create_status_table = ('CREATE TABLE IF NOT EXISTS statuses('
                       'id SERIAL PRIMARY KEY, '
                       'user_id INTEGER REFERENCES users(id), '
                       'status userstatus NOT NULL, '
                       'UNIQUE (user_id));')


class DBStorage:
    def __init__(self, db):
        self.db = db

    async def close(self):
        self.db.close()
        await self.db.wait_closed()

    ###
    # USERS
    ###

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

    async def find_users(self, query):
        # Escape wildcards
        # TODO: maybe allow exact match only?
        q = re.sub(r'[%_]', '\\\1', query)
        wildcard_q = '%' + q + '%'

        async with self.db.acquire() as conn:
            s_query = sa.select([users.c.login]).where(users.c.login.like(wildcard_q))
            res = await conn.execute(s_query)

            return await res.fetchall()

    ###
    # TOKENS
    ###

    async def add_token(self, login, token):
        async with self.db.acquire() as conn:
            i_query = users.update().where(users.c.login == login).values(token=token)
            await conn.execute(i_query)

    async def get_tokens(self):
        async with self.db.acquire() as conn:
            s_query = sa.select([users.c.login, users.c.token])
            res = await conn.execute(s_query)

            return await res.fetchall()

    async def get_token(self, login):
        async with self.db.acquire() as conn:
            s_query = sa.select([users.c.token]).where(users.c.login == login)
            res = await conn.execute(s_query)

            token = await res.fetchone()
            return token[0]

    ###
    # FRIENDS
    ###

    async def get_friends(self, login):
        async with self.db.acquire() as conn:
            aliased = users.alias()
            joined = users\
                .join(friendships, users.c.id == friendships.c.from_user)\
                .join(aliased, friendships.c.to_user == aliased.c.id)

            s_query = sa.select([aliased.c.login]).where(users.c.login == login)\
                .select_from(joined)

            res = await conn.execute(s_query)
            return await res.fetchall()

    async def add_friendship(self, login, invited):
        async with self.db.acquire() as conn:
            s_query = sa\
                .select([users.c.id, users.c.login])\
                .where(users.c.login.in_((login, invited)))

            res = await conn.execute(s_query)
            parties = await res.fetchall()

            ids = [p['id'] for p in parties]
            values = [
                {'from_user': ids[0], 'to_user': ids[1]},
                {'from_user': ids[1], 'to_user': ids[0]}
            ]
            i_query = friendships.insert().values(values)

            await conn.execute(i_query)

    async def get_invitations(self, login):
        async with self.db.acquire() as conn:
            aliased = users.alias()
            joined = users\
                .join(invitations, users.c.id == invitations.c.from_user)\
                .join(aliased, invitations.c.to_user == aliased.c.id)

            s_query = sa.select([aliased.c.login]).where(users.c.login == login)\
                .select_from(joined)

            res = await conn.execute(s_query)
            return await res.fetchall()

    async def add_invitation(self, login, invited):
        async with self.db.acquire() as conn:
            s_query = sa\
                .select([users.c.id, users.c.login])\
                .where(users.c.login.in_((login, invited)))

            res = await conn.execute(s_query)
            parties = await res.fetchall()

            ids = [p['id'] for p in parties]
            values = [
                {'from_user': ids[0], 'to_user': ids[1]},
                {'from_user': ids[1], 'to_user': ids[0]}
            ]
            i_query = invitations.insert().values(values)

            await conn.execute(i_query)

    async def remove_invitation(self, login, invited):
        async with self.db.acquire() as conn:
            aliased = users.alias()
            joined = users\
                .join(invitations, users.c.id == invitations.c.from_user)\
                .join(aliased, invitations.c.to_user == aliased.c.id)

            s_query = sa.select([invitations.c.id])\
                .where(
                    sa.and_(
                        users.c.login.in_([login, invited]),
                        aliased.c.login.in_([login, invited])
                    )
                )\
                .select_from(joined)

            d_query = invitations.delete().where(invitations.c.id.in_(s_query))
            await conn.execute(d_query)

    ###
    # USER STATUSES
    ###
    async def set_status(self, login, status):
        async with self.db.acquire() as conn:
            s_query = sa\
                .select([users.c.id])\
                .where(users.c.login == login)

            res = await conn.execute(s_query)
            user_id = (await res.fetchone())[0]

            # TODO: error on "login not found"
            i_query = insert(statuses).values({'user_id': user_id, 'status': status})\
                .on_conflict_do_update(
                constraint='statuses_user_id_key',
                set_={'status': status},
                where=(statuses.c.user_id == user_id)
            )
            await conn.execute(i_query)

    async def get_status(self, login):
        async with self.db.acquire() as conn:
            s_query = sa\
                .select([users.c.id])\
                .where(users.c.login == login)

            res = await conn.execute(s_query)
            user_id = (await res.fetchone())[0]

            # TODO: error on "login not found"
            i_query = sa.select([statuses.c.status]).where(statuses.c.user_id == user_id)
            res = await conn.execute(i_query)
            return (await res.fetchone())[0]


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
            await conn.execute(create_friendship_table)
            await conn.execute(create_invitation_table)
            await conn.execute(create_user_status_type)
            await conn.execute(create_status_table)

    app.on_startup.append(_setup)
    app.on_cleanup.append(cleanup_storage)
