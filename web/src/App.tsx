import { Toaster } from 'sonner'

import { TripScreen } from '@/components/TripScreen'
import { useDemoTrip } from '@/hooks/useDemoTrip'
import { useTripStream } from '@/hooks/useTripStream'

/**
 * Routes by pathname, no router library:
 *   /t/:token  live mode, data from the server
 *   anything else  demo mode (dummy simulation, ?state=&freeze=&me=)
 */
export default function App() {
  const token = /^\/t\/([^/]+)\/?$/.exec(window.location.pathname)?.[1]

  return (
    <>
      {token ? <LiveApp token={decodeURIComponent(token)} /> : <DemoApp />}
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
