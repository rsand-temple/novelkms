import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { useAuth } from '../AuthContext'

export default function RegistrationPage() {
  const { registration, refresh } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    firstName: registration?.firstName ?? '',
    lastName: registration?.lastName ?? '',
    displayName: '',
    mobileNumber: '',
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const set = (name) => (event) => setForm(value => ({ ...value, [name]: event.target.value }))

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setSaving(true)
    try {
      await authApi.register(form)
      await refresh()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.response?.data?.error ?? 'Registration failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'background.default', p: 2 }}>
      <Paper component="form" onSubmit={submit} elevation={3} sx={{ width: '100%', maxWidth: 520, p: 4 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h5" fontWeight={700}>Complete registration</Typography>
            <Typography color="text.secondary">Your OAuth provider verified your identity. Choose how you appear in NovelKMS.</Typography>
          </Box>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Email" value={registration?.emailAddress ?? ''} disabled fullWidth />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="First name" value={form.firstName} onChange={set('firstName')} fullWidth />
            <TextField label="Last name" value={form.lastName} onChange={set('lastName')} fullWidth />
          </Stack>
          <TextField label="Display name" value={form.displayName} onChange={set('displayName')} required slotProps={{ htmlInput: { maxLength: 200 } }} fullWidth />
          <TextField label="Mobile" value={form.mobileNumber} onChange={set('mobileNumber')} slotProps={{ htmlInput: { maxLength: 50 } }} fullWidth />
          <Button type="submit" variant="contained" size="large" disabled={saving || !form.displayName.trim()}>
            Create account
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
