import { Toaster } from 'sonner'

import { SendScreen } from '@/components/SendScreen'
import { TripScreen } from '@/components/TripScreen'
import { useDemoTrip } from '@/hooks/useDemoTrip'
import { useTripStream } from '@/hooks/useTripStream'

/**
 * Routes by pathname, no router library:
 *   /t/:token  live mode, data from the server
 *   /send/:token  driver's phone after scanning the car's QR: share /t/:token onward
 *   anything else  demo mode (dummy simulation, ?state=&freeze=&me=)
 */
export default function App() {
  const path = window.location.pathname
  const token = /^\/t\/([^/]+)\/?$/.exec(path)?.[1]
  const sendToken = /^\/send\/([^/]+)\/?$/.exec(path)?.[1]

  return (
    <>
      {sendToken ? (
        <SendScreen token={decodeURIComponent(sendToken)} />
      ) : token ? (
        <LiveApp token={decodeURIComponent(token)} />
      ) : (
        <DemoApp />
      )}
      <Toaster position="top-center" toastOptions={{ className: 'font-sans' }} />
    </>
  )
}

function LiveApp({ token }: { token: string }) {
  return <TripScreen source={useTripStream(token)} />
}

function DemoApp() {
  return <TripScreen source={useDemoTrip()} />
}
