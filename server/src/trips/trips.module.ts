import { Module } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'

import { HttpKakaoDirectionsClient, KAKAO_DIRECTIONS_CLIENT } from '../directions/kakao-directions.client.js'
import { RoutePlanner } from '../directions/route-planner.js'
import { LiveGateway } from '../live/live.gateway.js'
import { DriverAuthGuard } from './driver-auth.guard.js'
import { InMemoryTripRepository } from './in-memory-trip.repository.js'
import { ShareController } from './share.controller.js'
import { TRIP_REPOSITORY } from './trip.repository.js'
import { TripsController } from './trips.controller.js'
import { TripsService } from './trips.service.js'

@Module({
  controllers: [TripsController, ShareController],
  providers: [
    TripsService,
    DriverAuthGuard,
    LiveGateway,
    RoutePlanner,
    // Swap for a PostgreSQL/Redis implementation later
    { provide: TRIP_REPOSITORY, useClass: InMemoryTripRepository },
    // No key → no client → the planner draws straight lines (development and tests)
    {
      provide: KAKAO_DIRECTIONS_CLIENT,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const key = config.get<string>('KAKAO_REST_API_KEY')
        return key ? new HttpKakaoDirectionsClient(key) : null
      },
    },
  ],
})
export class TripsModule {}
