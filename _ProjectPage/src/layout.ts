import { useCallback, useState } from 'react'

// Wide / narrow reading width, remembered across visits.
export function useReadingWidth() {
  const [wide, setWide] = useState(() => {
    try {
      return localStorage.getItem('wide') === '1'
    } catch {
      return false
    }
  })

  const toggle = useCallback(() => {
    setWide((w) => {
      try {
        localStorage.setItem('wide', w ? '0' : '1')
      } catch {
        /* storage unavailable */
      }
      return !w
    })
  }, [])

  return { wide, toggle }
}
