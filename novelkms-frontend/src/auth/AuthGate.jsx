import { Box, CircularProgress } from '@mui/material'
import App from '../App'
import { useAuth } from './AuthContext'
import LoginPage from './components/LoginPage'
import RegistrationPage from './components/RegistrationPage'

export default function AuthGate() {
  const auth = useAuth()
  if (auth.loading) return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
  if (auth.state === 'REGISTRATION_REQUIRED') return <RegistrationPage />
  if (auth.state !== 'AUTHENTICATED') return <LoginPage />
  return <App />
}
