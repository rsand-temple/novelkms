import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { authApi } from '../../api/auth'

export default function LoginPage() {
  const [providers, setProviders] = useState({ google: false, meta: false })
  useEffect(() => { authApi.providers().then(setProviders).catch(() => {}) }, [])
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'background.default', p: 2 }}>
    <Paper elevation={3} sx={{ width: '100%', maxWidth: 420, p: 4 }}>
      <Stack spacing={2.5}>
        <Box><Typography variant="h4" fontWeight={750}>NovelKMS</Typography><Typography color="text.secondary">Novel Authoring Workspace</Typography></Box>
        <Button variant="contained" size="large" disabled={!providers.google} href={authApi.startUrl('google')}>Continue with Google</Button>
        <Button variant="outlined" size="large" disabled={!providers.meta} href={authApi.startUrl('meta')}>Continue with Facebook</Button>
      </Stack>
    </Paper>
  </Box>
}
