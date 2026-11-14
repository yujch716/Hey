import { Type } from 'class-transformer'
import {
  ArrayMinSize,
  IsArray,
  IsIn,
  IsInt,
  IsLatitude,
  IsLongitude,
  IsNumber,
  IsOptional,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator'

import type { CompletedBy } from '../trip.model.js'

export class LatLngDto {
  @IsLatitude()
  lat: number

  @IsLongitude()
  lng: number
}

export class VehicleDto {
  @IsString()
  @MaxLength(40)
  model: string

  @IsString()
  @MaxLength(20)
  colorName: string

  @Matches(/^#[0-9a-fA-F]{6}$/)
  colorHex: string

  @IsIn(['sedan', 'suv'])
  bodyType: 'sedan' | 'suv'

  @IsString()
  @MaxLength(20)
  plate: string
}

export class CreateStopDto {
  @IsInt()
  @Min(1)
  order: number

  @IsIn(['waypoint', 'pickup'])
  kind: 'waypoint' | 'pickup'

  // Pin label. Never a name or phone number, so keep it short.
  @IsString()
  @MaxLength(12)
  label: string

  @ValidateNested()
  @Type(() => LatLngDto)
  position: LatLngDto

  /** Only with a client-supplied `route`: where this stop sits on it */
  @IsOptional()
  @IsInt()
  @Min(0)
  routeIndex?: number
}

export class CreateTripDto {
  @ValidateNested()
  @Type(() => VehicleDto)
  vehicle: VehicleDto

  /** Where the car is when the trip starts; the first leg begins here */
  @ValidateNested()
  @Type(() => LatLngDto)
  origin: LatLngDto

  /**
   * Optional display polyline (dev and tests). The car app leaves it out and the server
   * plans the route; leg times always come from the server's planner.
   */
  @IsOptional()
  @IsArray()
  @ArrayMinSize(2)
  @ValidateNested({ each: true })
  @Type(() => LatLngDto)
  route?: LatLngDto[]

  @IsArray()
  @ArrayMinSize(1)
  @ValidateNested({ each: true })
  @Type(() => CreateStopDto)
  stops: CreateStopDto[]
}

export class LocationDto {
  @IsLatitude()
  lat: number

  @IsLongitude()
  lng: number

  @IsNumber()
  @Min(0)
  @Max(360)
  heading: number

  /** Navi ETA to the current destination (currentStopOrder), seconds */
  @IsNumber()
  @Min(0)
  currentLegEtaSeconds: number

  @IsInt()
  @Min(1)
  currentStopOrder: number

  /** epoch ms when the car took this fix */
  @IsInt()
  @Min(0)
  ts: number
}

export const TRIP_EVENT_TYPES = ['stopArrived', 'stopCompleted', 'tripEnded'] as const
export type TripEventType = (typeof TRIP_EVENT_TYPES)[number]

export const COMPLETED_BY = ['door', 'button', 'departed'] as const satisfies readonly CompletedBy[]

export class TripEventDto {
  @IsIn(TRIP_EVENT_TYPES)
  type: TripEventType

  // Required for stopArrived / stopCompleted; checked in the service
  @IsOptional()
  @IsInt()
  @Min(1)
  stopOrder?: number

  /** stopCompleted only: door (non-driver door opened and closed while stopped), button, or departed */
  @IsOptional()
  @IsIn(COMPLETED_BY)
  by?: CompletedBy
}
