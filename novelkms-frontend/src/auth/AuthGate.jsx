import { Box, CircularProgress } from '@mui/material'
import { useLocation } from 'react-router-dom'
import App from '../App'
import { useAuth } from './AuthContext'
import LoginPage from './components/LoginPage'
import RegistrationPage from './components/RegistrationPage'
import BillingReturnPage from '../components/subscription/BillingReturnPage'

export default function AuthGate() {
	const location = useLocation()
	const path = location.pathname
	const auth = useAuth()
	
	if (path === '/billing/success') {
		return <BillingReturnPage result="success" />
	}

	if (path === '/billing/cancel') {
		return <BillingReturnPage result="cancel" />
	}

	if (auth.loading) return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
	if (auth.state === 'REGISTRATION_REQUIRED') return <RegistrationPage />
	if (auth.state !== 'AUTHENTICATED') return <LoginPage />
	return <App />
}
