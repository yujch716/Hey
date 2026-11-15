import { Controller, Get, Inject, Query } from '@nestjs/common'

import { SearchPlacesDto } from './dto/search-places.dto.js'
import { PlacesService } from './places.service.js'

/** Place search for the car app's stop picker. Results carry no POI ids: the navi works from coordinates. */
@Controller('places')
export class PlacesController {
  constructor(@Inject(PlacesService) private readonly places: PlacesService) {}

  @Get('search')
  search(@Query() dto: SearchPlacesDto) {
    const near = dto.lat !== undefined && dto.lng !== undefined ? { lat: dto.lat, lng: dto.lng } : undefined
    return this.places.search(dto.query, near)
  }
}
