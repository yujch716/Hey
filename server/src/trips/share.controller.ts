import { Body, Controller, Delete, Get, HttpCode, Inject, Param, Post } from '@nestjs/common'

import { ReplyDto } from './dto/reply.dto.js'
import { LatLngDto } from './dto/trip.dto.js'
import { TripsService } from './trips.service.js'

/** Passenger (web) endpoints. The share token in the URL is the only credential. */
@Controller('share')
export class ShareController {
  constructor(@Inject(TripsService) private readonly trips: TripsService) {}

  @Get(':token')
  view(@Param('token') token: string) {
    return this.trips.getShareView(token)
  }

  @Post(':token/replies')
  @HttpCode(204)
  async reply(@Param('token') token: string, @Body() dto: ReplyDto) {
    await this.trips.reply(token, dto)
  }

  /** Passenger shares where they are standing: pushed to the driver, and kept (last one only) until they stop */
  @Post(':token/location')
  @HttpCode(204)
  async location(@Param('token') token: string, @Body() dto: LatLngDto) {
    await this.trips.shareLocation(token, dto)
  }

  @Delete(':token/location')
  @HttpCode(204)
  async stopLocation(@Param('token') token: string) {
    await this.trips.shareLocation(token, null)
  }
}
