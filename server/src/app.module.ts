import { Module } from '@nestjs/common'
import { ConfigModule } from '@nestjs/config'

import { PlacesModule } from './places/places.module.js'
import { TripsModule } from './trips/trips.module.js'

@Module({
  imports: [ConfigModule.forRoot({ isGlobal: true }), TripsModule, PlacesModule],
})
export class AppModule {}
