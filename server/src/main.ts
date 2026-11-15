import 'reflect-metadata'

import { type INestApplicationContext, ValidationPipe } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { NestFactory } from '@nestjs/core'
import { IoAdapter } from '@nestjs/platform-socket.io'
import type { ServerOptions } from 'socket.io'

import { AppModule } from './app.module.js'

/** Same CORS rule for socket.io as for HTTP; the gateway decorator can't read config. */
class CorsIoAdapter extends IoAdapter {
  constructor(
    app: INestApplicationContext,
    private readonly origin: string[],
  ) {
    super(app)
  }

  override createIOServer(port: number, options?: ServerOptions) {
    return super.createIOServer(port, { ...options, cors: { origin: this.origin } } as ServerOptions)
  }
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule)
  const config = app.get(ConfigService)
  const port = Number(config.get('PORT', 3000))
  const origin = config
    .get<string>('CORS_ORIGIN', 'http://localhost:5173')
    .split(',')
    .map((o) => o.trim())

  app.enableCors({ origin })
  app.useWebSocketAdapter(new CorsIoAdapter(app, origin))
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }))

  await app.listen(port)
  console.log(`hey server on http://localhost:${port} (CORS: ${origin.join(', ')})`)
}

void bootstrap()
