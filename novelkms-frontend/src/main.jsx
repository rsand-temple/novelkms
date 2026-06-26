import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider, CssBaseline } from '@mui/material'
import theme from './theme'
import AuthGate from './auth/AuthGate'
import { AuthProvider } from './auth/AuthProvider'

const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 1000 * 30, retry: (failureCount, error) => { const status = error?.response?.status; if (status && status < 500) return false; return failureCount < 1 } } } })

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
	  <QueryClientProvider client={queryClient}>
	    <ThemeProvider theme={theme}>
		  <CssBaseline />
		  <AuthProvider>
		    <AuthGate />
		  </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
	</BrowserRouter>
  </React.StrictMode>
)
