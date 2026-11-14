import { Body, Controller, Get, HttpCode, Inject, Param, Post, UseGuards } from '@nestjs/common'

import { DriverAuthGuard } from './driver-auth.guard.js'
import { CreateTripDto, LocationDto, TripEventDto } from './dto/trip.dto.js'
import { TripsService } from './trips.service.js'

/** Car app endpoints. Everything except creation needs the driver token. */
@Controller('trips')
export class TripsController {
  constructor(@Inject(TripsService) private readonly trips: TripsService) {}

  @Post()
  create(@Body() dto: CreateTripDto) {
    return this.trips.createTrip(dto)
  }

  /** Progress, per-stop ETAs and shared passenger positions, for the car app's map */
  @Get(':tripId')
  @UseGuards(DriverAuthGuard)
  view(@Param('tripId') tripId: string) {
    return this.trips.getDriverView(tripId)
  }

  @Post(':tripId/location')
  @UseGuards(DriverAuthGuard)
  @HttpCode(204)
  async location(@Param('tripId') tripId: string, @Body() dto: LocationDto) {
    await this.trips.updateLocation(tripId, dto)
  }

  @Post(':tripId/events')
  @UseGuards(DriverAuthGuard)
  @HttpCode(204)
  async event(@Param('tripId') tripId: string, @Body() dto: TripEventDto) {
    await this.trips.handleEvent(tripId, dto)
  }
}
