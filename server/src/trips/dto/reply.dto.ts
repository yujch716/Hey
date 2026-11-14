import { Type } from 'class-transformer'
import { IsIn, IsOptional, ValidateNested } from 'class-validator'

import type { QuickReply } from '../../common/types.js'
import { LatLngDto } from './trip.dto.js'

export const QUICK_REPLIES = ['out', 'late', 'waitHere'] as const satisfies readonly QuickReply[]

export class ReplyDto {
  @IsIn(QUICK_REPLIES)
  type: QuickReply

  @IsOptional()
  @ValidateNested()
  @Type(() => LatLngDto)
  position?: LatLngDto
}
