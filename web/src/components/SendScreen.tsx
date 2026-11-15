import { Share2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { EndScreen, Frame } from '@/components/TripScreen'
import { fetchShare, type ShareResult } from '@/lib/api'

/**
 * /send/:token — what the driver's phone opens from the QR on the car screen.
 * Passes the passenger link (/t/:token) on through the phone's share sheet.
 */
export function SendScreen({ token }: { token: string }) {
  const [status, setStatus] = useState<ShareResult['status'] | 'loading'>('loading')
  const link = `${window.location.origin}/t/${encodeURIComponent(token)}`

  useEffect(() => {
    void fetchShare(token).then((r) => setStatus(r.status))
  }, [token])

  if (status === 'notFound') return <Frame><EndScreen headline="링크를 찾을 수 없어요" note="차 화면의 QR을 다시 찍어 주세요" /></Frame>
  if (status === 'expired') return <Frame><EndScreen headline="끝난 픽업이에요" note="이 링크는 더 이상 열리지 않아요" /></Frame>

  const send = async () => {
    if (navigator.share) {
      try {
        await navigator.share({ title: 'hey — 픽업 차량 위치', text: '차가 어디쯤 왔는지 여기서 볼 수 있어요', url: link })
      } catch {
        // the user closed the share sheet
      }
      return
    }
    try {
      await navigator.clipboard.writeText(link)
      toast('링크를 복사했어요', { description: '메신저에 붙여 넣어 보내 주세요' })
    } catch {
      toast.error('복사하지 못했어요', { description: '아래 주소를 길게 눌러 복사해 주세요' })
    }
  }

  return (
    <Frame>
      <section className="flex flex-1 flex-col justify-center bg-paper px-6">
        <h1 className="text-[34px] font-extrabold leading-[1.1] tracking-[-0.03em]">이 링크를 탑승자에게 보내주세요</h1>
        <p className="mt-3 text-[15px] leading-snug text-muted">받는 사람은 앱 설치 없이 차 위치와 도착 예정 시간을 볼 수 있어요</p>
        <button
          type="button"
          onClick={() => void send()}
          disabled={status === 'loading'}
          className="mt-8 flex items-center justify-center gap-2 rounded-xl bg-ink py-4 text-[17px] font-bold text-white disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-route"
        >
          <Share2 className="size-5" aria-hidden />
          보내기
        </button>
        <p className="mt-4 select-all break-all text-[13px] text-muted">{link}</p>
      </section>
    </Frame>
  )
}
