import { useCallback, useEffect, useState } from 'react'
import { authApi } from '../api/auth'
import { AuthContext } from './AuthContext'

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState({ loading: true, state: 'UNAUTHENTICATED', user: null, registration: null })
  const refresh = useCallback(async () => {
    try {
      const data = await authApi.status()
      setAuth({ loading: false, state: data.state, user: data.user ?? null, registration: data.registration ?? null })
    } catch {
      setAuth({ loading: false, state: 'UNAUTHENTICATED', user: null, registration: null })
    }
  }, [])
  useEffect(() => { refresh() }, [refresh])
  const logout = async () => { await authApi.logout(); await refresh() }
  return <AuthContext.Provider value={{ ...auth, refresh, logout }}>{children}</AuthContext.Provider>
}
