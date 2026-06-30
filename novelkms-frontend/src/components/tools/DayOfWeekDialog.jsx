import { useState } from 'react'
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { toolsApi } from '../../api/tools'

const todayIso = () => new Date().toISOString().slice(0, 10)

export default function DayOfWeekDialog({ open, onClose }) {
  const [date, setDate] = useState(todayIso())
  const [calendar, setCalendar] = useState('gregorian')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const calculate = async () => {
    setLoading(true)
    setError('')
    setResult(null)
    try {
      setResult(await toolsApi.dayOfWeek({ date, calendar }))
    } catch (e) {
      setError(e.response?.data?.message || 'Could not calculate the day of week.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Day-of-week calculator</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Enter any date and choose the calendar system. For historical dates, the
            civil calendar depends on the country and adoption period.
          </Typography>

          <TextField
            label="Date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />

          <FormControl fullWidth>
            <InputLabel id="calendar-system-label">Calendar system</InputLabel>
            <Select
              labelId="calendar-system-label"
              label="Calendar system"
              value={calendar}
              onChange={(e) => setCalendar(e.target.value)}
            >
              <MenuItem value="gregorian">Proleptic Gregorian</MenuItem>
              <MenuItem value="julian">Julian</MenuItem>
            </Select>
          </FormControl>

          {error && <Alert severity="error">{error}</Alert>}

          {result && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="overline" color="text.secondary">
                Result
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                {result.dayOfWeek}
              </Typography>
              <Typography variant="body2" sx={{ mt: 0.5 }}>
                {result.date} · {result.calendar}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                {result.note}
              </Typography>
            </Paper>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={calculate} disabled={loading || !date}>
          {loading ? 'Calculating…' : 'Calculate'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
