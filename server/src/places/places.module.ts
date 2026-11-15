import { Module } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'

import { HttpKakaoLocalClient, KAKAO_LOCAL_CLIENT } from './kakao-local.client.js'
import { PlacesController } from './places.controller.js'
import { PlacesService } from './places.service.js'

@Module({
  controllers: [PlacesController],
  providers: [
    PlacesService,
    // No key → no client → fixed dummy places (development and tests)
    {
      provide: KAKAO_LOCAL_CLIENT,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const key = config.get<string>('KAKAO_REST_API_KEY')
        return key ? new HttpKakaoLocalClient(key) : null
      },
    },
  ],
})
export class PlacesModule {}
