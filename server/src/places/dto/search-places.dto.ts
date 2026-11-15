import { Type } from 'class-transformer'
import { IsLatitude, IsLongitude, IsOptional, IsString, MaxLength, MinLength } from 'class-validator'

export class SearchPlacesDto {
  @IsString()
  @MinLength(1)
  @MaxLength(50)
  query: string

  /** Search near the car: both or neither */
  @IsOptional()
  @Type(() => Number)
  @IsLatitude()
  lat?: number

  @IsOptional()
  @Type(() => Number)
  @IsLongitude()
  lng?: number
}
