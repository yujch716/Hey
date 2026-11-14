import { Check } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { QuickReply } from '@/types'

const REPLIES: { id: QuickReply; label: string; spoken: string }[] = [
  { id: 'out', label: '나왔어요', spoken: '나왔어요' },
  { id: 'late', label: '5분 늦어요', spoken: '5분 늦어요' },
  { id: 'waitHere', label: '여기서 기다릴게요', spoken: '지금 위치에서 기다릴게요' },
]

type Props = {
  /** Rejects when the driver could not be told; the button then goes back to how it was. */
  onSend: (id: QuickReply) => Promise<void>
  disabled?: boolean
}

/** Replies are read aloud in the car (TTS), so the driver never has to look at a phone. */
export function QuickReplies({ onSend, disabled }: Props) {
  const [sent, setSent] = useState<QuickReply | null>(null)
  const [pending, setPending] = useState<QuickReply | null>(null)

  const send = async (r: (typeof REPLIES)[number]) => {
    const previous = sent
    setSent(r.id)
    setPending(r.id)
    try {
      await onSend(r.id)
      toast(`운전자에게 "${r.spoken}"를 전했어요`, { description: '차 안에서 음성으로 읽어줘요' })
    } catch {
      setSent(previous)
      toast.error('운전자에게 전하지 못했어요', { description: '잠시 뒤 다시 눌러 주세요' })
    } finally {
      setPending(null)
    }
  }

  return (
    <section aria-label="운전자에게 알리기">
      <h2 className="mb-2 text-[13px] font-semibold text-muted">운전자에게 알리기</h2>
      <div className="grid grid-cols-[1fr_1fr] gap-2">
        {REPLIES.map((r) => {
          const isSent = sent === r.id
          return (
            <Button
              key={r.id}
              variant={isSent ? 'default' : 'secondary'}
              disabled={disabled || pending !== null}
              onClick={() => void send(r)}
              aria-pressed={isSent}
              aria-busy={pending === r.id}
              className={cn(r.id === 'waitHere' && 'col-span-2')}
            >
              {isSent && <Check aria-hidden />}
              {r.label}
            </Button>
          )
        })}
      </div>
    </section>
  )
}
