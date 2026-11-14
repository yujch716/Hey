import { type CanActivate, type ExecutionContext, Inject, Injectable } from '@nestjs/common'
import type { Request } from 'express'

import { TripsService } from './trips.service.js'

/** `Authorization: Bearer <driverToken>` must match the trip in the `:tripId` route param. */
@Injectable()
export class DriverAuthGuard implements CanActivate {
  constructor(@Inject(TripsService) private readonly trips: TripsService) {}

  async canActivate(context: ExecutionContext) {
    const req = context.switchToHttp().getRequest<Request>()
    const tripId = req.params.tripId as string
    const match = /^Bearer (.+)$/.exec(req.headers.authorization ?? '')
    await this.trips.authorizeDriver(tripId, match?.[1])
    return true
  }
}
